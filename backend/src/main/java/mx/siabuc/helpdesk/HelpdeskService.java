package mx.siabuc.helpdesk;

import static mx.siabuc.helpdesk.Models.*;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HelpdeskService {
    public static final long MAX_MINUTES = 1_000_000_000L;
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final NotificationService notifications;
    private final String bootstrapUsername;
    private final String bootstrapPassword;
    private final String bootstrapEmail;
    public HelpdeskService(JdbcTemplate db, PasswordEncoder passwords, NotificationService notifications,
            @Value("${app.admin-username}") String bootstrapUsername,
            @Value("${app.admin-password}") String bootstrapPassword,
            @Value("${app.admin-email:}") String bootstrapEmail) {
        this.db = db; this.passwords = passwords; this.notifications = notifications;
        this.bootstrapUsername = bootstrapUsername; this.bootstrapPassword = bootstrapPassword;
        this.bootstrapEmail = bootstrapEmail;
    }

    static final RowMapper<User> USER = (r, n) -> new User(r.getLong("id"), r.getString("username"),
        r.getString("display_name"), Role.valueOf(r.getString("role")), r.getObject("institution_id", Long.class), r.getBoolean("active"), r.getString("email"));
    private static final RowMapper<Institution> INSTITUTION = (r, n) -> new Institution(r.getLong("id"), r.getString("name"),
        r.getString("contact_name"), r.getString("email"), r.getString("phone"), r.getString("notes"), r.getBoolean("active"),
        r.getLong("email_allocated_minutes"), r.getLong("email_used_minutes"), r.getLong("email_allocated_minutes") - r.getLong("email_used_minutes"),
        r.getLong("remote_allocated_minutes"), r.getLong("remote_used_minutes"), r.getLong("remote_allocated_minutes") - r.getLong("remote_used_minutes"), r.getObject("support_admin_id", Long.class));
    private static final String CASE_SELECT = """
        SELECT c.*, i.name AS institution_name, u.username AS creator, COALESCE(a.display_name,'') AS assigned_admin_name,
          COALESCE((SELECT SUM(e.minutes) FROM case_entries e WHERE e.case_id=c.id AND NOT e.voided),0) AS total_minutes
        FROM support_cases c JOIN institutions i ON i.id=c.institution_id JOIN app_users u ON u.id=c.created_by
        LEFT JOIN app_users a ON a.id=c.assigned_admin_id
        """;
    private static final RowMapper<SupportCase> CASE = (r, n) -> new SupportCase(r.getLong("id"), r.getLong("institution_id"),
        r.getString("institution_name"), r.getString("title"), r.getString("description"), Channel.valueOf(r.getString("channel")),
        CaseStatus.valueOf(r.getString("status")), r.getString("creator"), r.getTimestamp("created_at").toInstant(),
        r.getTimestamp("updated_at").toInstant(), r.getLong("total_minutes"), r.getObject("assigned_admin_id", Long.class), r.getString("assigned_admin_name"));
    private static final RowMapper<Entry> ENTRY = (r, n) -> new Entry(r.getLong("id"), r.getLong("case_id"),
        r.getString("description"), r.getString("channel") == null ? null : Channel.valueOf(r.getString("channel")),
        r.getLong("minutes"), r.getBoolean("internal"), r.getString("creator"), r.getTimestamp("created_at").toInstant(),
        r.getBoolean("voided"), r.getString("void_reason"));
    private static final String PAYMENT_SELECT = """
        SELECT p.*,i.name AS institution_name,u.username AS creator,v.username AS voider
        FROM payments p JOIN institutions i ON i.id=p.institution_id JOIN app_users u ON u.id=p.created_by
        LEFT JOIN app_users v ON v.id=p.voided_by
        """;
    private static final RowMapper<Payment> PAYMENT = (r, n) -> new Payment(r.getLong("id"), r.getString("request_id"),
        r.getLong("institution_id"), r.getString("institution_name"), r.getString("reference"), r.getBigDecimal("amount"),
        r.getString("currency"), r.getDate("paid_on").toLocalDate(), PaymentMethod.valueOf(r.getString("method")),
        r.getLong("email_minutes"), r.getLong("remote_minutes"), r.getString("notes"), r.getString("creator"),
        r.getTimestamp("created_at").toInstant(), r.getBoolean("voided"), r.getString("void_reason"), r.getString("voider"),
        r.getTimestamp("voided_at") == null ? null : r.getTimestamp("voided_at").toInstant());

    @Transactional
    public void bootstrapAdmin() {
        accountLock();
        if (db.queryForObject("SELECT COUNT(*) FROM app_users WHERE role='ADMIN' AND active", Long.class) > 0) return;
        if (bootstrapPassword == null || bootstrapPassword.isBlank())
            throw new IllegalStateException("Configure ADMIN_PASSWORD (12 a 72 caracteres, máximo 72 bytes UTF-8) para crear el primer administrador.");
        validatePassword(bootstrapPassword);
        validateEmail(bootstrapEmail);
        String username = username(bootstrapUsername);
        Long id = db.queryForObject("INSERT INTO app_users(username,display_name,password_hash,role,active,email) VALUES (?,?,?,'ADMIN',TRUE,?) RETURNING id",
            Long.class, username, "Administrador SIABUC", passwords.encode(bootstrapPassword), optional(bootstrapEmail));
        audit(username, "BOOTSTRAP_ADMIN", "USER", id, "Creación inicial del administrador.");
    }

    public User activeUser(String username) {
        List<User> users = db.query("SELECT u.* FROM app_users u LEFT JOIN institutions i ON i.id=u.institution_id WHERE u.username=? AND u.active AND (u.role IN ('ADMIN','BILLING') OR i.active)", USER, username);
        if (users.isEmpty()) throw new ApiException(401, "La cuenta no está activa o su institución está dada de baja.");
        return users.get(0);
    }
    public User me() { return actor(); }
    public long credentialVersion(String username) {
        return db.queryForObject("SELECT auth_version FROM app_users WHERE username=?", Long.class, username);
    }
    private User actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName()))
            throw new ApiException(401, "Inicie sesión para continuar.");
        return activeUser(auth.getName());
    }
    private User admin() { User u = actor(); if (u.role() != Role.ADMIN) throw ApiException.forbidden(); return u; }
    private User supportActor() { User u = actor(); if (u.role() == Role.BILLING) throw ApiException.forbidden(); return u; }
    private User billingActor() { User u = actor(); if (u.role() == Role.USER) throw ApiException.forbidden(); return u; }
    private void accountLock() { db.queryForObject("SELECT id FROM system_lock WHERE id=1 FOR UPDATE", Integer.class); }

    public List<Institution> institutions() {
        User u = actor();
        if (u.role() == Role.ADMIN) return db.query("SELECT * FROM institutions ORDER BY name,id", INSTITUTION);
        List<Institution> institutions = u.role() == Role.BILLING ? db.query("SELECT * FROM institutions ORDER BY name,id", INSTITUTION)
            : db.query("SELECT * FROM institutions WHERE id=? AND active", INSTITUTION, u.institutionId());
        return institutions.stream()
            .map(i -> new Institution(i.id(), i.name(), i.contactName(), i.email(), i.phone(), "", i.active(),
                i.emailAllocatedMinutes(), i.emailUsedMinutes(), i.emailRemainingMinutes(), i.remoteAllocatedMinutes(),
                i.remoteUsedMinutes(), i.remoteRemainingMinutes(), i.supportAdminId())).toList();
    }
    @Transactional
    public Institution createInstitution(InstitutionInput input) {
        User actor = admin(); validateInstitution(input);
        Long id = db.queryForObject("""
            INSERT INTO institutions(name,contact_name,email,phone,notes,email_allocated_minutes,remote_allocated_minutes,support_admin_id)
            VALUES (?,?,?,?,?,?,?,?) RETURNING id
            """, Long.class, clean(input.name()), optional(input.contactName()), optional(input.email()), optional(input.phone()),
            optional(input.notes()), input.emailAllocatedMinutes(), input.remoteAllocatedMinutes(), input.supportAdminId());
        audit(actor.username(), "CREATE_INSTITUTION", "INSTITUTION", id, "Alta de institución; correo=" + input.emailAllocatedMinutes() + "; remoto=" + input.remoteAllocatedMinutes());
        return institution(id, false);
    }
    @Transactional
    public Institution updateInstitution(long id, InstitutionInput input, boolean supportAdminProvided) {
        User actor = admin(); validateInstitution(input);
        if (input.active() == null) throw ApiException.bad("Indique si la institución está activa.");
        Institution old = institution(id, true);
        if (input.emailAllocatedMinutes() < old.emailUsedMinutes() || input.remoteAllocatedMinutes() < old.remoteUsedMinutes())
            throw ApiException.conflict("El tiempo asignado no puede ser menor que el tiempo ya utilizado.");
        db.update("""
            UPDATE institutions SET name=?,contact_name=?,email=?,phone=?,notes=?,active=?,email_allocated_minutes=?,
            remote_allocated_minutes=?,support_admin_id=?,updated_at=NOW() WHERE id=?
            """, clean(input.name()), optional(input.contactName()), optional(input.email()), optional(input.phone()), optional(input.notes()),
            input.active(), input.emailAllocatedMinutes(), input.remoteAllocatedMinutes(), supportAdminProvided ? input.supportAdminId() : old.supportAdminId(), id);
        audit(actor.username(), "UPDATE_INSTITUTION", "INSTITUTION", id, "Antes: " + old + "; después: " + institution(id, false));
        return institution(id, false);
    }
    @Transactional
    public void deleteInstitution(long id) {
        User actor = admin(); Institution old = institution(id, true);
        if (!old.active()) return;
        db.update("UPDATE institutions SET active=FALSE,updated_at=NOW() WHERE id=?", id);
        audit(actor.username(), "DEACTIVATE_INSTITUTION", "INSTITUTION", id, "Baja lógica; se conservan cuentas, casos y movimientos.");
    }
    private Institution institution(long id, boolean lock) {
        List<Institution> rows = db.query("SELECT * FROM institutions WHERE id=?" + (lock ? " FOR UPDATE" : ""), INSTITUTION, id);
        if (rows.isEmpty()) throw ApiException.missing(); return rows.get(0);
    }
    private void validateInstitution(InstitutionInput i) {
        required(i.name(), "Nombre", 180); optionalLength(i.contactName(), "Contacto", 180);
        optionalLength(i.email(), "Correo", 254); optionalLength(i.phone(), "Teléfono", 60); optionalLength(i.notes(), "Notas", 10000);
        validateEmail(i.email()); validateAdminAssignment(i.supportAdminId());
        minutes(i.emailAllocatedMinutes()); minutes(i.remoteAllocatedMinutes());
    }

    public List<User> users() { admin(); return db.query("SELECT * FROM app_users ORDER BY username,id", USER); }
    @Transactional
    public User createUser(UserInput input) {
        User actor = admin(); accountLock(); validateUser(input, true);
        Long id = db.queryForObject("INSERT INTO app_users(username,display_name,password_hash,role,institution_id,active,email) VALUES (?,?,?,?,?,?,?) RETURNING id",
            Long.class, username(input.username()), clean(input.displayName()), passwords.encode(input.password()), input.role().name(), input.institutionId(), input.active(), optional(input.email()));
        audit(actor.username(), "CREATE_USER", "USER", id, "Alta de " + input.username() + "; rol=" + input.role() + "; institución=" + input.institutionId());
        return user(id);
    }
    @Transactional
    public User updateUser(long id, UserInput input) {
        User actor = admin(); accountLock(); User old = user(id);
        if (input.role() != old.role()) throw ApiException.conflict("El rol de una cuenta existente no puede cambiarse. Cree una cuenta distinta para el nuevo ámbito.");
        validateUser(input, false);
        if (!old.username().equals(username(input.username()))) throw ApiException.bad("El nombre de usuario no puede cambiarse.");
        if (!Objects.equals(old.institutionId(), input.institutionId()))
            throw ApiException.conflict("No puede cambiarse la institución de una cuenta existente. Cree una cuenta distinta.");
        if (old.active() && old.role() == Role.ADMIN && (!input.active() || input.role() != Role.ADMIN)
            && db.queryForObject("SELECT COUNT(*) FROM app_users WHERE role='ADMIN' AND active", Long.class) <= 1)
            throw ApiException.conflict("Debe conservar al menos un administrador activo.");
        db.update("UPDATE app_users SET display_name=?,role=?,institution_id=?,active=?,email=? WHERE id=?", clean(input.displayName()), input.role().name(), input.institutionId(), input.active(), input.email() == null ? old.email() : optional(input.email()), id);
        if (input.password() != null && !input.password().isEmpty())
            db.update("UPDATE app_users SET password_hash=?,auth_version=auth_version+1 WHERE id=?", passwords.encode(input.password()), id);
        audit(actor.username(), "UPDATE_USER", "USER", id, "Antes: " + old + "; después: " + user(id) + "; contraseña restablecida=" + (input.password() != null && !input.password().isEmpty()));
        return user(id);
    }
    private User user(long id) {
        List<User> rows = db.query("SELECT * FROM app_users WHERE id=?", USER, id);
        if (rows.isEmpty()) throw ApiException.missing(); return rows.get(0);
    }
    private void validateUser(UserInput i, boolean creating) {
        username(i.username()); required(i.displayName(), "Nombre visible", 180);
        validateEmail(i.email());
        if (i.role() == null || i.active() == null) throw ApiException.bad("Indique rol y estado de la cuenta.");
        if (creating || (i.password() != null && !i.password().isEmpty())) validatePassword(i.password());
        if (i.role() == Role.USER) {
            if (i.institutionId() == null) throw ApiException.bad("Los usuarios requieren una institución.");
            Institution institution = institution(i.institutionId(), false);
            if (creating && !institution.active()) throw ApiException.conflict("No pueden crearse usuarios para una institución dada de baja.");
        } else if (i.institutionId() != null) throw ApiException.bad("Administración y cobranza no deben tener institución asignada.");
    }
    @Transactional
    public void changePassword(PasswordInput input) {
        User actor = actor(); validatePassword(input.newPassword());
        String hash = db.queryForObject("SELECT password_hash FROM app_users WHERE id=? FOR UPDATE", String.class, actor.id());
        if (input.currentPassword() == null || !passwords.matches(input.currentPassword(), hash))
            throw ApiException.bad("La contraseña actual no es correcta.");
        db.update("UPDATE app_users SET password_hash=?,auth_version=auth_version+1 WHERE id=?", passwords.encode(input.newPassword()), actor.id());
        audit(actor.username(), "CHANGE_PASSWORD", "USER", actor.id(), "Cambio de contraseña por su titular.");
    }

    public List<SupportCase> cases() {
        User actor = supportActor();
        return actor.role() == Role.ADMIN ? db.query(CASE_SELECT + " ORDER BY c.updated_at DESC,c.id DESC", CASE)
            : db.query(CASE_SELECT + " WHERE c.institution_id=? ORDER BY c.updated_at DESC,c.id DESC", CASE, actor.institutionId());
    }
    public CaseDetail caseDetail(long id) { User actor = supportActor(); return detail(authorizedCase(id, actor), actor); }
    private SupportCase authorizedCase(long id, User actor) {
        List<SupportCase> rows = db.query(CASE_SELECT + " WHERE c.id=?", CASE, id);
        if (rows.isEmpty()) throw ApiException.missing();
        SupportCase c = rows.get(0);
        if (actor.role() != Role.ADMIN && !Objects.equals(actor.institutionId(), c.institutionId())) throw ApiException.missing();
        return c;
    }
    private CaseDetail detail(SupportCase c, User actor) {
        List<Entry> entries = db.query("SELECT e.*,u.username AS creator FROM case_entries e JOIN app_users u ON u.id=e.created_by WHERE e.case_id=?" +
            (actor.role() == Role.ADMIN ? "" : " AND NOT e.internal") + " ORDER BY e.id", ENTRY, c.id());
        return new CaseDetail(c.id(), c.institutionId(), c.institutionName(), c.title(), c.description(), c.channel(), c.status(),
            c.createdBy(), c.createdAt(), c.updatedAt(), c.totalMinutes(), entries, c.assignedAdminId(), c.assignedAdminName());
    }
    private record LockedCase(SupportCase supportCase, Institution institution) {}
    private LockedCase lockCase(long id, User actor) {
        SupportCase initial = authorizedCase(id, actor);
        // All accounting mutations lock institution first, then case, avoiding lock inversion.
        Institution institution = institution(initial.institutionId(), true);
        db.queryForObject("SELECT id FROM support_cases WHERE id=? FOR UPDATE", Long.class, id);
        return new LockedCase(authorizedCase(id, actor), institution);
    }
    @Transactional
    public CaseDetail createCase(CaseInput input) {
        User actor = supportActor(); validateCase(input.title(), input.description(), input.channel());
        if (input.institutionId() == null) throw ApiException.bad("Indique la institución del caso.");
        if (actor.role() != Role.ADMIN && !Objects.equals(actor.institutionId(), input.institutionId())) throw ApiException.forbidden();
        Institution institution = institution(input.institutionId(), true); requireActive(institution); requireBalance(institution, input.channel(), 1);
        if (actor.role() == Role.USER && input.assignedAdminId() != null) throw ApiException.forbidden();
        Long assigned = input.assignedAdminId() != null ? input.assignedAdminId() : institution.supportAdminId();
        if (input.assignedAdminId() == null && assigned != null && !activeAdmin(assigned)) assigned = null;
        if (assigned == null && actor.role() == Role.ADMIN) assigned = actor.id();
        validateAdminAssignment(assigned);
        Long id = db.queryForObject("INSERT INTO support_cases(institution_id,title,description,channel,created_by,assigned_admin_id) VALUES (?,?,?,?,?,?) RETURNING id", Long.class,
            input.institutionId(), clean(input.title()), clean(input.description()), input.channel().name(), actor.id(), assigned);
        audit(actor.username(), "CREATE_CASE", "CASE", id, "Caso abierto; canal=" + input.channel());
        notifications.caseEvent(id, "CREATE_CASE", "Caso abierto: " + clean(input.title()) + "\n" + clean(input.description()), false, actor.username());
        return detail(authorizedCase(id, actor), actor);
    }
    @Transactional
    public CaseDetail updateCase(long id, CaseEdit input, boolean assignedAdminProvided) {
        User actor = admin(); validateCase(input.title(), input.description(), input.channel());
        LockedCase locked = lockCase(id, actor);
        Long assigned = assignedAdminProvided ? input.assignedAdminId() : locked.supportCase().assignedAdminId();
        if (assignedAdminProvided) validateAdminAssignment(assigned);
        db.update("UPDATE support_cases SET title=?,description=?,channel=?,assigned_admin_id=?,updated_at=NOW() WHERE id=?", clean(input.title()), clean(input.description()), input.channel().name(), assigned, id);
        audit(actor.username(), "UPDATE_CASE", "CASE", id, "Antes: " + locked.supportCase() + "; después: " + authorizedCase(id, actor));
        String description = "Datos del caso actualizados: " + clean(input.title()) + "\n" + clean(input.description())
            + "\nResponsable anterior: " + locked.supportCase().assignedAdminName() + "; responsable actual: " + authorizedCase(id, actor).assignedAdminName();
        insertEntry(id, description, null, 0, false, actor.id());
        notifications.caseEvent(id, "UPDATE_CASE", description, false, actor.username());
        return detail(authorizedCase(id, actor), actor);
    }
    @Transactional
    public CaseDetail changeStatus(long id, StatusInput input) {
        User actor = admin();
        if (input.status() == null) throw ApiException.bad("Indique el estado del caso.");
        required(input.reason(), "Motivo del cambio de estado", 10000);
        LockedCase locked = lockCase(id, actor);
        if (locked.supportCase().status() == input.status()) return detail(locked.supportCase(), actor);
        if (input.status() == CaseStatus.OPEN) { requireActive(locked.institution()); requireBalance(locked.institution(), locked.supportCase().channel(), 1); }
        db.update("UPDATE support_cases SET status=?,updated_at=NOW() WHERE id=?", input.status().name(), id);
        String description = (input.status() == CaseStatus.OPEN ? "Caso reabierto: " : "Caso cerrado: ") + clean(input.reason());
        insertEntry(id, description, null, 0, false, actor.id());
        audit(actor.username(), "CHANGE_CASE_STATUS", "CASE", id, description);
        notifications.caseEvent(id, "CHANGE_CASE_STATUS", description, false, actor.username());
        return detail(authorizedCase(id, actor), actor);
    }
    @Transactional
    public CaseDetail addEntry(long id, EntryInput input) {
        User actor = supportActor(); required(input.description(), "Descripción de la atención", 10000); minutes(input.minutes());
        if (input.internal() == null) throw ApiException.bad("Indique si el registro es interno.");
        if ((input.minutes() == 0 && input.channel() != null) || (input.minutes() > 0 && input.channel() == null))
            throw ApiException.bad("Un comentario usa 0 minutos y canal vacío; una atención requiere minutos positivos y canal.");
        if (actor.role() != Role.ADMIN && (input.minutes() != 0 || input.channel() != null || input.internal())) throw ApiException.forbidden();
        LockedCase locked = lockCase(id, actor); requireActive(locked.institution());
        if (locked.supportCase().status() != CaseStatus.OPEN) throw ApiException.conflict("El caso está cerrado. Reábralo antes de registrar una atención.");
        if (input.minutes() > 0) { requireBalance(locked.institution(), input.channel(), input.minutes()); changeUsed(locked.institution().id(), input.channel(), input.minutes()); }
        long entryId = insertEntry(id, clean(input.description()), input.channel(), input.minutes(), input.internal(), actor.id());
        db.update("UPDATE support_cases SET updated_at=NOW() WHERE id=?", id);
        audit(actor.username(), "CREATE_ENTRY", "ENTRY", entryId, "Caso=" + id + "; minutos=" + input.minutes() + "; canal=" + input.channel() + "; interno=" + input.internal());
        notifications.caseEvent(id, "CREATE_ENTRY", clean(input.description()) + "\nMinutos: " + input.minutes() + "; canal: " + input.channel(), input.internal(), actor.username());
        return detail(authorizedCase(id, actor), actor);
    }
    @Transactional
    public CaseDetail voidEntry(long id, long entryId, VoidInput input) {
        User actor = admin(); required(input.reason(), "Motivo de anulación", 10000); LockedCase locked = lockCase(id, actor);
        List<Entry> rows = db.query("SELECT e.*,u.username AS creator FROM case_entries e JOIN app_users u ON u.id=e.created_by WHERE e.id=? AND e.case_id=? FOR UPDATE OF e", ENTRY, entryId, id);
        if (rows.isEmpty()) throw ApiException.missing(); Entry entry = rows.get(0);
        if (entry.voided()) throw ApiException.conflict("Este registro ya fue anulado; sus minutos no pueden devolverse otra vez.");
        if (entry.minutes() <= 0) throw ApiException.conflict("Sólo se anulan atenciones con consumo de minutos. Los comentarios conservan su trazabilidad.");
        changeUsed(locked.institution().id(), entry.channel(), -entry.minutes());
        db.update("UPDATE case_entries SET voided=TRUE,void_reason=? WHERE id=?", clean(input.reason()), entryId);
        db.update("UPDATE support_cases SET updated_at=NOW() WHERE id=?", id);
        audit(actor.username(), "VOID_ENTRY", "ENTRY", entryId, "Caso=" + id + "; reintegro=" + entry.minutes() + "; canal=" + entry.channel() + "; motivo=" + clean(input.reason()));
        notifications.caseEvent(id, "VOID_ENTRY", "Atención anulada: " + entry.description() + "\nMotivo: " + clean(input.reason())
            + "\nMinutos devueltos: " + entry.minutes() + "; canal: " + entry.channel(), entry.internal(), actor.username());
        return detail(authorizedCase(id, actor), actor);
    }
    private long insertEntry(long caseId, String description, Channel channel, long minutes, boolean internal, long actorId) {
        return db.queryForObject("INSERT INTO case_entries(case_id,description,channel,minutes,internal,created_by) VALUES (?,?,?,?,?,?) RETURNING id",
            Long.class, caseId, description, channel == null ? null : channel.name(), minutes, internal, actorId);
    }
    private void changeUsed(long id, Channel channel, long delta) {
        String column = channel == Channel.EMAIL ? "email_used_minutes" : "remote_used_minutes";
        db.update("UPDATE institutions SET " + column + "=" + column + "+?,updated_at=NOW() WHERE id=?", delta, id);
    }
    private void requireActive(Institution i) { if (!i.active()) throw ApiException.conflict("La institución está dada de baja."); }
    private void requireBalance(Institution i, Channel channel, long required) {
        long remaining = channel == Channel.EMAIL ? i.emailRemainingMinutes() : i.remoteRemainingMinutes();
        if (remaining < required) throw ApiException.conflict("Saldo insuficiente de " + (channel == Channel.EMAIL ? "soporte por correo" : "escritorio remoto") + ". Disponibles: " + remaining + " minutos.");
    }
    private void validateCase(String title, String description, Channel channel) {
        required(title, "Título", 220); required(description, "Descripción", 10000);
        if (channel == null) throw ApiException.bad("Indique el canal de atención.");
    }

    public List<Payment> payments() {
        billingActor(); return db.query(PAYMENT_SELECT + " ORDER BY p.id DESC", PAYMENT);
    }
    @Transactional
    public Payment createPayment(PaymentInput input) {
        User actor = billingActor(); UUID requestId = validatePayment(input);
        // Idempotence and accounting share the institution lock with support consumption,
        // administrative adjustments and payment cancellation.
        Institution institution = institution(input.institutionId(), true);
        List<Payment> existing = db.query(PAYMENT_SELECT + " WHERE p.request_id=?", PAYMENT, requestId);
        if (!existing.isEmpty()) {
            Payment previous = existing.get(0);
            if (!samePayment(previous, input)) throw ApiException.conflict("Este identificador de solicitud ya se usó con datos diferentes.");
            return previous;
        }
        requireActive(institution);
        if (institution.emailAllocatedMinutes() > MAX_MINUTES - input.emailMinutes()
                || institution.remoteAllocatedMinutes() > MAX_MINUTES - input.remoteMinutes())
            throw ApiException.conflict("La acreditación excede el máximo permitido de minutos asignados.");
        if (db.queryForObject("SELECT COUNT(*) FROM payments WHERE institution_id=? AND LOWER(BTRIM(reference))=LOWER(?)",
                Long.class, institution.id(), clean(input.reference())) > 0)
            throw ApiException.conflict("La referencia de pago ya existe para esta institución, incluso si fue anulada.");
        Long id = db.queryForObject("""
            INSERT INTO payments(request_id,institution_id,reference,amount,currency,paid_on,method,email_minutes,remote_minutes,notes,created_by)
            VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING id
            """, Long.class, requestId, institution.id(), clean(input.reference()), input.amount(), input.currency(),
            java.sql.Date.valueOf(input.paidOn()), input.method().name(), input.emailMinutes(), input.remoteMinutes(), clean(input.notes()), actor.id());
        db.update("UPDATE institutions SET email_allocated_minutes=email_allocated_minutes+?,remote_allocated_minutes=remote_allocated_minutes+?,updated_at=NOW() WHERE id=?",
            input.emailMinutes(), input.remoteMinutes(), institution.id());
        audit(actor.username(), "CREATE_PAYMENT", "PAYMENT", id, "Institución=" + institution.id() + "; referencia=" + clean(input.reference())
            + "; importe=" + input.amount().toPlainString() + " " + input.currency() + "; correo=" + input.emailMinutes() + "; remoto=" + input.remoteMinutes());
        notifications.paymentEvent(id, "CREATE_PAYMENT", "Pago recibido y minutos acreditados. " + clean(input.notes()), actor.username());
        return payment(id);
    }
    @Transactional
    public Payment voidPayment(long id, VoidInput input) {
        User actor = admin(); required(input.reason(), "Motivo de anulación", 10000);
        Payment initial = payment(id);
        Institution institution = institution(initial.institutionId(), true);
        db.queryForObject("SELECT id FROM payments WHERE id=? FOR UPDATE", Long.class, id);
        Payment payment = payment(id);
        if (payment.voided()) throw ApiException.conflict("Este pago ya fue anulado.");
        if (institution.emailRemainingMinutes() < payment.emailMinutes() || institution.remoteRemainingMinutes() < payment.remoteMinutes())
            throw ApiException.conflict("No quedan suficientes minutos disponibles en ambos canales para retirar toda la acreditación del pago.");
        db.update("UPDATE institutions SET email_allocated_minutes=email_allocated_minutes-?,remote_allocated_minutes=remote_allocated_minutes-?,updated_at=NOW() WHERE id=?",
            payment.emailMinutes(), payment.remoteMinutes(), institution.id());
        db.update("UPDATE payments SET voided=TRUE,void_reason=?,voided_by=?,voided_at=NOW() WHERE id=?", clean(input.reason()), actor.id(), id);
        audit(actor.username(), "VOID_PAYMENT", "PAYMENT", id, "Anulación; correo retirado=" + payment.emailMinutes() + "; remoto retirado="
            + payment.remoteMinutes() + "; motivo=" + clean(input.reason()));
        notifications.paymentEvent(id, "VOID_PAYMENT", "Pago anulado; minutos acreditados retirados. Motivo: " + clean(input.reason()), actor.username());
        return payment(id);
    }
    private Payment payment(long id) {
        List<Payment> rows = db.query(PAYMENT_SELECT + " WHERE p.id=?", PAYMENT, id);
        if (rows.isEmpty()) throw ApiException.missing(); return rows.get(0);
    }
    private UUID validatePayment(PaymentInput i) {
        required(i.requestId(), "Identificador de solicitud", 36);
        UUID request;
        try { request = UUID.fromString(i.requestId()); }
        catch (IllegalArgumentException e) { throw ApiException.bad("El identificador de solicitud debe ser un UUID válido."); }
        if (!request.toString().equalsIgnoreCase(i.requestId())) throw ApiException.bad("El UUID debe tener su formato completo de 36 caracteres.");
        if (i.institutionId() == null || i.institutionId() <= 0) throw ApiException.bad("Indique la institución del pago.");
        required(i.reference(), "Referencia de pago", 100); required(i.notes(), "Descripción del pago", 10000);
        if (i.amount() == null || i.amount().signum() <= 0 || i.amount().compareTo(new BigDecimal("999999999.99")) > 0 || i.amount().scale() > 2)
            throw ApiException.bad("El importe debe ser positivo, no exceder 999999999.99 y tener como máximo dos decimales.");
        if (i.currency() == null || !i.currency().matches("[A-Z]{3}")) throw ApiException.bad("La moneda debe tener tres letras mayúsculas, por ejemplo MXN.");
        if (i.paidOn() == null || i.method() == null) throw ApiException.bad("Indique la fecha y el método del pago.");
        if (i.paidOn().getYear() < 1 || i.paidOn().getYear() > 9999) throw ApiException.bad("La fecha del pago debe tener un año entre 0001 y 9999.");
        minutes(i.emailMinutes()); minutes(i.remoteMinutes());
        if (i.emailMinutes() == 0 && i.remoteMinutes() == 0) throw ApiException.bad("El pago debe acreditar minutos en al menos un canal.");
        return request;
    }
    private boolean samePayment(Payment p, PaymentInput i) {
        return p.institutionId() == i.institutionId() && p.reference().equals(clean(i.reference()))
            && p.amount().compareTo(i.amount()) == 0 && p.currency().equals(i.currency()) && p.paidOn().equals(i.paidOn())
            && p.method() == i.method() && p.emailMinutes() == i.emailMinutes() && p.remoteMinutes() == i.remoteMinutes()
            && p.notes().equals(clean(i.notes()));
    }

    private void validateAdminAssignment(Long id) {
        if (id == null) return;
        if (!activeAdmin(id))
            throw ApiException.bad("El responsable debe ser un administrador activo.");
    }
    private boolean activeAdmin(long id) {
        return db.queryForObject("SELECT COUNT(*) FROM app_users WHERE id=? AND role='ADMIN' AND active", Long.class, id) == 1;
    }
    private void validateEmail(String email) {
        optionalLength(email, "Correo electrónico", 254);
        if (email != null && !email.isBlank() && MailConfig.email(email) == null)
            throw ApiException.bad("Correo electrónico inválido.");
    }

    public List<Audit> audit() {
        admin();
        return db.query("SELECT * FROM audit_log ORDER BY id DESC LIMIT 500", (r, n) -> new Audit(r.getLong("id"), r.getString("actor"),
            r.getString("action"), r.getString("entity_type"), r.getObject("entity_id", Long.class), r.getString("details"), r.getTimestamp("created_at").toInstant()));
    }
    private void audit(String actor, String action, String type, Long id, String details) {
        db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,?,?,?,?)", actor, action, type, id, details);
    }
    public void health() { if (!Objects.equals(db.queryForObject("SELECT 1", Integer.class), 1)) throw new ApiException(503, "Base de datos no disponible."); }
    private static String username(String value) {
        required(value, "Usuario", 80); String result = clean(value);
        if (!result.matches("[A-Za-z0-9][A-Za-z0-9._@-]{2,79}")) throw ApiException.bad("El usuario debe tener entre 3 y 80 caracteres: letras, números, punto, guion, arroba o guion bajo.");
        return result;
    }
    static void validatePassword(String value) {
        if (value == null || value.codePointCount(0, value.length()) < 12 || value.codePointCount(0, value.length()) > 72 || value.getBytes(StandardCharsets.UTF_8).length > 72 || value.isBlank())
            throw ApiException.bad("La contraseña debe tener de 12 a 72 caracteres y como máximo 72 bytes UTF-8.");
    }
    private static String clean(String value) { return value == null ? "" : value.strip(); }
    private static String optional(String value) { return clean(value); }
    private static void required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw ApiException.bad(field + " es obligatorio."); optionalLength(value, field, max);
    }
    private static void optionalLength(String value, String field, int max) {
        if (value != null && value.length() > max) throw ApiException.bad(field + " excede " + max + " caracteres.");
    }
    private static void minutes(Long value) {
        if (value == null || value < 0 || value > MAX_MINUTES) throw ApiException.bad("Los minutos deben ser enteros entre 0 y " + MAX_MINUTES + ".");
    }
}
