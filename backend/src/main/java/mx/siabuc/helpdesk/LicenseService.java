package mx.siabuc.helpdesk;

import static mx.siabuc.helpdesk.Models.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** License subaccounts. Every accounting mutation holds the institution lock first. */
@Service
public class LicenseService {
    public record Defaults(long emailMinutes, long remoteMinutes, boolean configured, long revision) {}
    public record DefaultsInput(Long emailMinutes, Long remoteMinutes, Long revision) {}
    public record CreateInput(Long institutionId, String number, Long defaultsRevision) {}
    public record TopupInput(String requestId, Long emailMinutes, Long remoteMinutes, String reason) {}
    public record License(long id, long institutionId, String institutionName, String number,
            long initialEmailMinutes, long initialRemoteMinutes, long emailAllocatedMinutes, long remoteAllocatedMinutes,
            long emailUsedMinutes, long remoteUsedMinutes, long emailRemainingMinutes, long remoteRemainingMinutes,
            long defaultsRevision) {}
    public record Movement(long id, String kind, long emailMinutes, long remoteMinutes, String reason,
            Long paymentId, Long entryId, String createdBy, Instant createdAt) {}
    private final JdbcTemplate db;
    private final PortalEventService events;
    public LicenseService(JdbcTemplate db, PortalEventService events) { this.db = db; this.events = events; }
    private static final String SELECT = "SELECT l.*,i.name AS institution_name FROM support_licenses l JOIN institutions i ON i.id=l.institution_id";
    private static final RowMapper<License> LICENSE = (r, n) -> new License(r.getLong("id"), r.getLong("institution_id"),
        r.getString("institution_name"), r.getString("number"), r.getLong("initial_email_minutes"), r.getLong("initial_remote_minutes"),
        r.getLong("email_allocated_minutes"), r.getLong("remote_allocated_minutes"), r.getLong("email_used_minutes"), r.getLong("remote_used_minutes"),
        r.getLong("email_allocated_minutes") - r.getLong("email_used_minutes"), r.getLong("remote_allocated_minutes") - r.getLong("remote_used_minutes"),
        r.getLong("defaults_revision"));

    public Defaults defaults() {
        return db.queryForObject("SELECT * FROM license_support_defaults WHERE id=1", (r,n) -> new Defaults(
            r.getLong("email_minutes"), r.getLong("remote_minutes"), r.getBoolean("configured"), r.getLong("revision")));
    }
    @Transactional
    public Defaults saveDefaults(DefaultsInput input, User actor) {
        requireAdmin(actor); minutes(input.emailMinutes()); minutes(input.remoteMinutes());
        if (input.revision() == null || input.revision() < 0) throw ApiException.bad("Indique la revisión de la configuración.");
        int changed = db.update("""
            UPDATE license_support_defaults SET email_minutes=?,remote_minutes=?,configured=TRUE,revision=revision+1,updated_at=NOW()
            WHERE id=1 AND revision=?
            """, input.emailMinutes(), input.remoteMinutes(), input.revision());
        if (changed != 1) throw ApiException.conflict("La configuración cambió. Vuelva a cargarla antes de guardar.");
        audit(actor, "LICENSE_DEFAULTS", null, "Correo=" + input.emailMinutes() + "; remoto=" + input.remoteMinutes()
            + "; revisión=" + (input.revision() + 1) + ". Sólo altas nuevas.");
        return defaults();
    }
    public List<License> list(User actor, Long institutionId) {
        if (actor.role() == Role.USER) institutionId = actor.institutionId();
        return institutionId == null ? db.query(SELECT + " ORDER BY i.name,l.number,l.id", LICENSE)
            : db.query(SELECT + " WHERE l.institution_id=? ORDER BY l.number,l.id", LICENSE, institutionId);
    }
    public License license(long id) {
        List<License> rows = db.query(SELECT + " WHERE l.id=?", LICENSE, id);
        if (rows.isEmpty()) throw ApiException.missing();
        return rows.get(0);
    }
    public void validateLink(long institutionId, Long licenseId) {
        if (licenseId != null && license(licenseId).institutionId() != institutionId)
            throw ApiException.conflict("La licencia no pertenece a la institución indicada.");
    }
    @Transactional
    public License create(CreateInput input, User actor) {
        requireAdmin(actor);
        if (input.institutionId() == null) throw ApiException.bad("Indique la institución.");
        required(input.number(), "Número de licencia", 100);
        lockInstitution(input.institutionId());
        // Shared lock makes the displayed revision and the once-only initial credit one atomic snapshot.
        db.queryForObject("SELECT id FROM license_support_defaults WHERE id=1 FOR SHARE", Integer.class);
        Defaults defaults = defaults();
        if (!defaults.configured()) throw ApiException.conflict("Configure primero las horas predeterminadas en Configuración de soporte.");
        if (!Objects.equals(input.defaultsRevision(), defaults.revision()))
            throw ApiException.conflict("Las horas predeterminadas cambiaron. Actualice el formulario antes de crear la licencia.");
        if (db.queryForObject("SELECT COUNT(*) FROM support_licenses WHERE LOWER(BTRIM(number))=LOWER(?)", Long.class, input.number().trim()) > 0)
            throw ApiException.conflict("El número de licencia ya está registrado.");
        growInstitution(input.institutionId(), defaults.emailMinutes(), defaults.remoteMinutes());
        Long id = db.queryForObject("""
            INSERT INTO support_licenses(institution_id,number,defaults_revision,initial_email_minutes,initial_remote_minutes,
              email_allocated_minutes,remote_allocated_minutes,created_by) VALUES(?,?,?,?,?,?,?,?) RETURNING id
            """, Long.class, input.institutionId(), input.number().trim(), defaults.revision(), defaults.emailMinutes(), defaults.remoteMinutes(),
            defaults.emailMinutes(), defaults.remoteMinutes(), actor.id());
        movement(id, "INITIAL", defaults.emailMinutes(), defaults.remoteMinutes(), "Horas incluidas al registrar la licencia.", null, null, null, actor);
        events.checkBalance(input.institutionId());
        return license(id);
    }
    @Transactional
    public License topup(long id, TopupInput input, User actor) {
        requireAdmin(actor); minutes(input.emailMinutes()); minutes(input.remoteMinutes()); required(input.reason(), "Motivo", 2000);
        if (input.emailMinutes() + input.remoteMinutes() == 0) throw ApiException.bad("Agregue tiempo en al menos un canal.");
        UUID requestId;
        try { requestId = UUID.fromString(input.requestId()); }
        catch (RuntimeException e) { throw ApiException.bad("Identificador de solicitud inválido."); }
        License initial = license(id); lockInstitution(initial.institutionId());
        var previous = db.queryForList("SELECT * FROM license_support_history WHERE request_id=?", requestId);
        if (!previous.isEmpty()) {
            var p = previous.get(0);
            if (!Objects.equals(p.get("license_id"), id) || !Objects.equals(p.get("email_minutes"), input.emailMinutes())
                    || !Objects.equals(p.get("remote_minutes"), input.remoteMinutes()) || !Objects.equals(p.get("reason"), input.reason().trim()))
                throw ApiException.conflict("El identificador ya se utilizó con otra asignación.");
            return license(id);
        }
        growInstitution(initial.institutionId(), input.emailMinutes(), input.remoteMinutes());
        changeAllocated(id, input.emailMinutes(), input.remoteMinutes());
        movement(id, "TOPUP", input.emailMinutes(), input.remoteMinutes(), input.reason().trim(), requestId, null, null, actor);
        events.checkBalance(initial.institutionId());
        return license(id);
    }
    public List<Movement> history(long id, User actor) {
        requireAdmin(actor); license(id);
        return db.query("""
            SELECT h.*,u.username AS creator FROM license_support_history h JOIN app_users u ON u.id=h.created_by
            WHERE h.license_id=? ORDER BY h.id DESC
            """, (r,n) -> new Movement(r.getLong("id"), r.getString("kind"), r.getLong("email_minutes"), r.getLong("remote_minutes"),
            r.getString("reason"), r.getObject("payment_id", Long.class), r.getObject("entry_id", Long.class), r.getString("creator"),
            r.getTimestamp("created_at").toInstant()), id);
    }
    public long reserved(long institutionId, Channel channel) {
        String prefix = channel == Channel.EMAIL ? "email" : "remote";
        return db.queryForObject("SELECT COALESCE(SUM(" + prefix + "_allocated_minutes-" + prefix
            + "_used_minutes),0) FROM support_licenses WHERE institution_id=?", Long.class, institutionId);
    }
    public void requireBalance(Institution institution, Long licenseId, Channel channel, long required) {
        validateLink(institution.id(), licenseId);
        long available;
        if (licenseId == null) available = (channel == Channel.EMAIL ? institution.emailRemainingMinutes() : institution.remoteRemainingMinutes()) - reserved(institution.id(), channel);
        else { License l = license(licenseId); available = channel == Channel.EMAIL ? l.emailRemainingMinutes() : l.remoteRemainingMinutes(); }
        if (available < required) throw ApiException.conflict("Saldo insuficiente de " + (channel == Channel.EMAIL ? "correo" : "escritorio remoto")
            + (licenseId == null ? " sin licencia" : " en la licencia") + ". Disponibles: " + available + " minutos.");
    }
    /** Institution lock and the normal case transaction are held by HelpdeskService. */
    public void usage(Long licenseId, long entryId, Channel channel, long delta, User actor, String reason) {
        if (licenseId == null) return;
        String column = channel == Channel.EMAIL ? "email_used_minutes" : "remote_used_minutes";
        db.update("UPDATE support_licenses SET " + column + "=" + column + "+? WHERE id=?", delta, licenseId);
        movement(licenseId, delta > 0 ? "USAGE" : "VOID_USAGE", channel == Channel.EMAIL ? -delta : 0,
            channel == Channel.REMOTE ? -delta : 0, reason, null, null, entryId, actor);
    }
    public Long policyLicense(Long policyId) {
        return policyId == null ? null : db.queryForObject("SELECT license_id FROM support_policies WHERE id=?", Long.class, policyId);
    }
    public void payment(Payment payment, User actor, boolean voided, String reason) {
        Long licenseId = policyLicense(payment.policyId());
        if (licenseId == null) return;
        long sign = voided ? -1 : 1;
        changeAllocated(licenseId, sign * payment.emailMinutes(), sign * payment.remoteMinutes());
        movement(licenseId, voided ? "VOID_PAYMENT" : "PAYMENT", sign * payment.emailMinutes(), sign * payment.remoteMinutes(),
            reason, null, payment.id(), null, actor);
    }
    private void changeAllocated(long id, long email, long remote) {
        db.update("UPDATE support_licenses SET email_allocated_minutes=email_allocated_minutes+?,remote_allocated_minutes=remote_allocated_minutes+? WHERE id=?", email, remote, id);
    }
    private void growInstitution(long id, long email, long remote) {
        int changed = db.update("""
            UPDATE institutions SET email_allocated_minutes=email_allocated_minutes+?,remote_allocated_minutes=remote_allocated_minutes+?,updated_at=NOW()
            WHERE id=? AND email_allocated_minutes<=1000000000-? AND remote_allocated_minutes<=1000000000-?
            """, email, remote, id, email, remote);
        if (changed != 1) throw ApiException.conflict("La asignación excede el máximo permitido de minutos.");
    }
    private void lockInstitution(long id) {
        var rows = db.queryForList("SELECT active FROM institutions WHERE id=? FOR UPDATE", Boolean.class, id);
        if (rows.isEmpty()) throw ApiException.missing();
        if (!rows.get(0)) throw ApiException.conflict("La institución está dada de baja.");
    }
    private void movement(long id, String kind, long email, long remote, String reason, UUID request, Long payment, Long entry, User actor) {
        db.update("""
            INSERT INTO license_support_history(license_id,kind,email_minutes,remote_minutes,reason,request_id,payment_id,entry_id,created_by)
            VALUES(?,?,?,?,?,?,?,?,?)
            """, id, kind, email, remote, reason, request, payment, entry, actor.id());
        audit(actor, "LICENSE_" + kind, id, "Correo=" + email + "; remoto=" + remote + "; " + reason);
    }
    private void audit(User actor, String action, Long id, String details) {
        db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES(?,?,'LICENSE',?,?)", actor.username(), action, id, details);
    }
    private static void requireAdmin(User actor) { if (actor.role() != Role.ADMIN) throw ApiException.forbidden(); }
    private static void minutes(Long value) { if (value == null || value < 0 || value > HelpdeskService.MAX_MINUTES) throw ApiException.bad("Indique minutos enteros de 0 a 1000000000."); }
    private static void required(String value, String label, int max) { if (value == null || value.isBlank() || value.length() > max || value.indexOf('\0') >= 0) throw ApiException.bad(label + " es obligatorio y admite hasta " + max + " caracteres."); }
}
