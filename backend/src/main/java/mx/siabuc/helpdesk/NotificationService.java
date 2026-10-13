package mx.siabuc.helpdesk;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Only persists messages; SMTP is deliberately outside business transactions. */
@Service
public class NotificationService {
    private final JdbcTemplate db;
    private final MailConfig config;
    private final PortalEventService portalEvents;
    public NotificationService(JdbcTemplate db, MailConfig config, PortalEventService portalEvents) {
        this.db = db; this.config = config; this.portalEvents = portalEvents;
    }

    public record Notification(long id, Long caseId, Long paymentId, String eventType, String recipient,
        String subject, String status, int attempts, String lastError, Instant createdAt, Instant sentAt) {}
    public record NotificationDetail(long id, Long caseId, Long paymentId, String eventType, String recipient,
        String subject, String status, int attempts, String lastError, Instant createdAt, Instant sentAt,
        String body, boolean internal, String recipientType, Long recipientEntityId) {}
    public record Configuration(boolean enabled, boolean configuredEnabled, boolean paused, String host, int port, String username, String fromAddress,
        String fromName, String security, boolean auth, String replyTo, long pendingCount, long failedCount, long suppressedCount) {}
    private static final RowMapper<Notification> NOTIFICATION = (r, n) -> new Notification(r.getLong("id"),
        r.getObject("case_id", Long.class), r.getObject("payment_id", Long.class), r.getString("event_type"),
        r.getString("recipient"), r.getString("subject"), r.getString("status"), r.getInt("attempts"),
        r.getString("last_error"), r.getTimestamp("created_at").toInstant(),
        r.getTimestamp("sent_at") == null ? null : r.getTimestamp("sent_at").toInstant());
    private record Recipient(String type, Long id, String address, String absentReason) {}
    private record CaseInfo(long id, long institutionId, String institutionName, String institutionEmail,
        Long assignedId, String assignedEmail, String assignedName, String title, String description, String status, String channel) {}
    private record PaymentInfo(long id, long institutionId, String institutionName, String institutionEmail,
        long authorId, String authorEmail, String reference, String amount, String currency, String paidOn,
        String method, long emailMinutes, long remoteMinutes, boolean voided, String policyFolio) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public void caseEvent(long caseId, String eventType, String description, boolean internal, String actor) {
        portalEvents.caseEvent(caseId, eventType, description, internal, actor);
        CaseInfo c = db.queryForObject("""
            SELECT c.*,i.name AS institution_name,i.email AS institution_email,
                CASE WHEN u.active THEN u.email END AS assigned_email,u.display_name AS assigned_name
            FROM support_cases c JOIN institutions i ON i.id=c.institution_id
            LEFT JOIN app_users u ON u.id=c.assigned_admin_id AND u.role='ADMIN'
            WHERE c.id=?
            """, (r, n) -> new CaseInfo(r.getLong("id"), r.getLong("institution_id"), r.getString("institution_name"),
                r.getString("institution_email"), r.getObject("assigned_admin_id", Long.class), r.getString("assigned_email"),
                r.getString("assigned_name"), r.getString("title"), r.getString("description"),
                r.getString("status"), r.getString("channel")), caseId);
        if (c == null) throw ApiException.missing();
        UUID eventKey = UUID.randomUUID();
        String subject = MailConfig.subject("[SIABUC] Caso #" + c.id() + " · " + eventLabel(eventType) + " · " + c.title());
        String responsible = c.assignedName() != null ? c.assignedName()
            : c.assignedId() == null ? "Sin administrativo asignado" : "Administrativo no disponible";
        String body = "SIABUC — Notificación de soporte\n\nCaso: #" + c.id() + "\nInstitución cliente: " + c.institutionName()
            + "\nAdministrativo que atiende: " + responsible
            + "\nTítulo: " + c.title() + "\nEstado: " + ("CLOSED".equals(c.status()) ? "Cerrado" : "Abierto")
            + "\nCanal principal: " + ("REMOTE".equals(c.channel()) ? "Escritorio remoto" : "Correo")
            + "\nEvento: " + eventLabel(eventType) + "\nRegistrado por: " + actor
            + (internal ? "\nVisibilidad: nota interna, sólo personal autorizado" : "")
            + "\n\nDescripción actual del caso:\n" + c.description()
            + "\n\nDetalle de la actualización:\n" + description
            + "\n\nPara mayor información, consulte la plataforma Helpdesk.\n";
        Set<String> recipients = new HashSet<>();
        if (!internal) enqueue(eventKey, caseId, null, eventType,
            new Recipient("INSTITUTION", c.institutionId(), c.institutionEmail(), "La institución no tiene un correo válido."),
            subject, body, false, recipients);
        boolean sharedClientMailbox = internal && sameMailbox(c.assignedEmail(), c.institutionEmail());
        enqueue(eventKey, caseId, null, eventType,
            new Recipient("USER", c.assignedId(), sharedClientMailbox ? null : c.assignedEmail(), sharedClientMailbox
                ? "La nota interna no se envió: el correo del responsable coincide con el correo de la institución."
                : c.assignedId() == null
                ? "El caso no tiene administrador asignado. Asigne un responsable para futuros eventos."
                : "El administrador responsable no está activo o no tiene un correo válido."),
            subject, body, internal, recipients);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void paymentEvent(long paymentId, String eventType, String description, String actor) {
        PaymentInfo p = db.queryForObject("""
            SELECT p.*,i.name AS institution_name,i.email AS institution_email,u.email AS author_email,s.folio AS policy_folio
            FROM payments p JOIN institutions i ON i.id=p.institution_id
            LEFT JOIN app_users u ON u.id=p.created_by AND u.active AND u.role IN ('ADMIN','BILLING')
            LEFT JOIN support_policies s ON s.id=p.policy_id
            WHERE p.id=?
            """, (r, n) -> new PaymentInfo(r.getLong("id"), r.getLong("institution_id"), r.getString("institution_name"),
                r.getString("institution_email"), r.getLong("created_by"), r.getString("author_email"),
                r.getString("reference"), r.getBigDecimal("amount").toPlainString(), r.getString("currency"),
                r.getString("paid_on"), r.getString("method"), r.getLong("email_minutes"),
                r.getLong("remote_minutes"), r.getBoolean("voided"), r.getString("policy_folio")), paymentId);
        if (p == null) throw ApiException.missing();
        UUID eventKey = UUID.randomUUID();
        String subject = MailConfig.subject("[SIABUC] Pago #" + p.id() + " · " + eventLabel(eventType));
        String body = "SIABUC — Notificación de pago\n\nPago: #" + p.id() + "\nInstitución: " + p.institutionName()
            + "\nReferencia: " + p.reference() + "\nMonto: " + p.amount() + " " + p.currency()
            + "\nFecha de pago: " + p.paidOn() + "\nMétodo: " + PolicyService.methodLabel(Models.PaymentMethod.valueOf(p.method()))
            + (p.policyFolio() == null ? "" : "\nPóliza: " + p.policyFolio())
            + "\nMinutos de correo: " + p.emailMinutes() + "\nMinutos de escritorio remoto: " + p.remoteMinutes()
            + "\nEstado: " + (p.voided() ? "Anulado" : "Registrado") + "\nRegistrado por: " + actor
            + "\n\n" + description + "\n\nEste aviso informa el registro administrativo; no es una factura fiscal.\n";
        Set<String> recipients = new HashSet<>();
        enqueue(eventKey, null, paymentId, eventType,
            new Recipient("INSTITUTION", p.institutionId(), p.institutionEmail(), "La institución no tiene un correo válido."),
            subject, body, false, recipients);
        enqueue(eventKey, null, paymentId, eventType,
            new Recipient("USER", p.authorId(), p.authorEmail(), "El autor del pago no está activo o no tiene un correo válido."),
            subject, body, false, recipients);
    }

    private long enqueue(UUID key, Long caseId, Long paymentId, String eventType, Recipient recipient,
            String subject, String body, boolean internal, Set<String> seen) {
        if (internal && !"USER".equals(recipient.type())) throw new IllegalStateException("Una nota interna sólo puede notificarse al personal.");
        // Serialize event creation with pause/resume so a notice cannot cross the pause boundary.
        boolean paused = deliveryPausedForUpdate();
        String address = MailConfig.email(recipient.address());
        if (address != null && !seen.add(address.toLowerCase(Locale.ROOT))) return -1;
        return db.queryForObject("""
            INSERT INTO notification_outbox(event_key,case_id,payment_id,event_type,recipient_type,recipient_entity_id,
                recipient,subject,body,internal,status,last_error)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id
            """, Long.class, key, caseId, paymentId, eventType, recipient.type(), recipient.id(), address == null ? "" : address,
                subject, body, internal, paused ? "SUPPRESSED" : address == null ? "SKIPPED" : "PENDING",
                paused ? "Envío desactivado al registrar el aviso. No se enviará al reactivar el correo."
                    : address == null ? recipient.absentReason() : null);
    }

    public List<Notification> notifications() {
        return db.query("SELECT * FROM notification_outbox ORDER BY id DESC LIMIT 500", NOTIFICATION);
    }
    @Transactional(readOnly = true)
    public NotificationDetail notificationDetail(long id) {
        // Read the persisted event snapshot; viewing history never retries or regenerates mail.
        List<NotificationDetail> rows = db.query("""
            SELECT id,case_id,payment_id,event_type,recipient,subject,status,attempts,last_error,
                created_at,sent_at,body,internal,recipient_type,recipient_entity_id
            FROM notification_outbox WHERE id=?
            """, (r, n) -> new NotificationDetail(r.getLong("id"), r.getObject("case_id", Long.class),
                r.getObject("payment_id", Long.class), r.getString("event_type"), r.getString("recipient"),
                r.getString("subject"), r.getString("status"), r.getInt("attempts"), r.getString("last_error"),
                r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("sent_at") == null ? null : r.getTimestamp("sent_at").toInstant(),
                r.getString("body"), r.getBoolean("internal"), r.getString("recipient_type"),
                r.getObject("recipient_entity_id", Long.class)), id);
        if (rows.isEmpty()) throw ApiException.missing();
        return rows.get(0);
    }
    public Configuration configuration() {
        boolean paused = db.queryForObject("SELECT paused FROM notification_settings WHERE id=1", Boolean.class);
        long pending = db.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE status IN ('PENDING','SENDING')", Long.class);
        long failed = db.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE status IN ('FAILED','SKIPPED')", Long.class);
        long suppressed = db.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE status='SUPPRESSED'", Long.class);
        return new Configuration(config.enabled && !paused, config.enabled, paused, config.host, config.port, config.username, config.fromAddress, config.fromName,
            config.security, config.auth, config.replyTo, pending, failed, suppressed);
    }
    @Transactional
    public Configuration setPaused(boolean paused, String actor) {
        // The dispatcher takes this same lock before every claim. Once this transaction
        // commits, no new delivery can be claimed until another administrator resumes it.
        // SMTP operations already in progress do not hold this lock and may still finish.
        boolean previous = deliveryPausedForUpdate();
        if (previous != paused) {
            int suppressed = 0;
            if (paused) {
                // Keep claimed deliveries untouched, but remember the boundary: if they later
                // fail or their lease expires, they must never be retried after resuming.
                db.update("""
                    UPDATE notification_settings SET paused=TRUE,suppressed_through_id=
                        GREATEST(suppressed_through_id,(SELECT COALESCE(MAX(id),0) FROM notification_outbox)) WHERE id=1
                    """);
                suppressed = db.update("""
                    UPDATE notification_outbox SET status='SUPPRESSED',
                        last_error='Envío desactivado por administración. Este aviso anterior no se enviará al reactivar el correo.'
                    WHERE status IN ('PENDING','FAILED','SKIPPED')
                    """);
            } else {
                db.update("UPDATE notification_settings SET paused=FALSE WHERE id=1");
            }
            db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,?, 'NOTIFICATION_CONFIG',1,?)",
                actor, paused ? "PAUSE_NOTIFICATIONS" : "RESUME_NOTIFICATIONS", paused
                    ? "Envío de correos desactivado. Avisos anteriores suprimidos: " + suppressed
                        + ". Los envíos ya iniciados pueden finalizar; los avisos suprimidos no se reenviarán."
                    : "Envío de correos reactivado sólo para eventos nuevos, si MAIL_ENABLED está activado. Los avisos anteriores no se envían.");
        }
        return configuration();
    }
    private boolean deliveryPausedForUpdate() {
        return db.queryForObject("SELECT paused FROM notification_settings WHERE id=1 FOR UPDATE", Boolean.class);
    }
    private Notification notification(long id) {
        List<Notification> rows = db.query("SELECT * FROM notification_outbox WHERE id=?", NOTIFICATION, id);
        if (rows.isEmpty()) throw ApiException.missing();
        return rows.get(0);
    }
    private record RetryInfo(UUID key, String type, Long entityId, String recipient, String status, boolean internal, Long caseId) {}
    @Transactional
    public Notification retry(long id, String actor) {
        if (deliveryPausedForUpdate())
            throw ApiException.conflict("El envío de correos está desactivado. No pueden reintentarse avisos durante la pausa.");
        List<RetryInfo> rows = db.query("SELECT * FROM notification_outbox WHERE id=? FOR UPDATE", (r, n) -> new RetryInfo(
            r.getObject("event_key", UUID.class), r.getString("recipient_type"), r.getObject("recipient_entity_id", Long.class),
            r.getString("recipient"), r.getString("status"), r.getBoolean("internal"), r.getObject("case_id", Long.class)), id);
        if (rows.isEmpty()) throw ApiException.missing();
        RetryInfo row = rows.get(0);
        if (!Set.of("FAILED", "SKIPPED").contains(row.status()))
            throw ApiException.conflict("Sólo pueden reintentarse avisos fallidos u omitidos. Un aviso enviado no se reenvía.");
        String address = MailConfig.email(row.recipient());
        if (address == null) {
            if (row.entityId() == null) throw ApiException.conflict("El aviso original no tenía destinatario identificado; asigne un responsable para los próximos eventos.");
            List<String> emails;
            if ("INSTITUTION".equals(row.type())) {
                if (row.internal()) throw ApiException.forbidden();
                emails = db.query("SELECT email FROM institutions WHERE id=?", (r, n) -> r.getString(1), row.entityId());
            } else {
                String role = row.internal() || row.caseId() != null ? "role='ADMIN'" : "role IN ('ADMIN','BILLING')";
                emails = db.query("SELECT email FROM app_users WHERE id=? AND active AND " + role, (r, n) -> r.getString(1), row.entityId());
            }
            address = emails.isEmpty() ? null : MailConfig.email(emails.get(0));
        }
        if (address == null) throw ApiException.conflict("Configure un correo válido en el destinatario original antes de reintentar.");
        if (row.internal() && row.caseId() != null) {
            String clientEmail = db.queryForObject("SELECT i.email FROM support_cases c JOIN institutions i ON i.id=c.institution_id WHERE c.id=?",
                String.class, row.caseId());
            if (sameMailbox(address, clientEmail)) throw ApiException.conflict("Una nota interna requiere un correo del responsable distinto del correo de la institución.");
        }
        if (db.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE event_key=? AND id<>? AND LOWER(recipient)=LOWER(?)",
                Long.class, row.key(), id, address) > 0)
            throw ApiException.conflict("Ese correo ya tiene un aviso para el mismo evento; no se duplicó el envío.");
        db.update("UPDATE notification_outbox SET recipient=?,status='PENDING',attempts=0,last_error=NULL,available_at=NOW(),claim_token=NULL,claimed_until=NULL WHERE id=?", address, id);
        audit(actor, "RETRY_NOTIFICATION", id, "Reintento manual para el destinatario original.");
        return notification(id);
    }
    @Transactional
    public Notification test(Models.User actor) {
        if (deliveryPausedForUpdate())
            throw ApiException.conflict("El envío de correos está desactivado. Reactívelo antes de solicitar una prueba.");
        String email = db.queryForObject("SELECT email FROM app_users WHERE id=? AND active AND role='ADMIN'", String.class, actor.id());
        if (MailConfig.email(email) == null) throw ApiException.conflict("Configure primero un correo válido en su propia cuenta administrativa.");
        long id = enqueue(UUID.randomUUID(), null, null, "TEST_MAIL", new Recipient("USER", actor.id(), email, ""),
            "[SIABUC] Prueba de configuración de correo",
            "Mensaje de prueba solicitado por " + actor.username() + ".\nEste mensaje sólo se dirige al correo registrado en su propia cuenta.\n", false, new HashSet<>());
        audit(actor.username(), "TEST_NOTIFICATION", id, "Prueba encolada al correo de la propia cuenta.");
        return notification(id);
    }
    private void audit(String actor, String action, long id, String details) {
        db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,?, 'NOTIFICATION',?,?)", actor, action, id, details);
    }
    private static boolean sameMailbox(String first, String second) {
        String a = MailConfig.email(first), b = MailConfig.email(second);
        return a != null && b != null && a.equalsIgnoreCase(b);
    }
    private static String eventLabel(String value) {
        return switch (value) {
            case "CREATE_CASE" -> "Caso abierto";
            case "CLAIM_CASE" -> "Caso tomado";
            case "UPDATE_CASE_PROFILE" -> "Ficha técnica actualizada";
            case "ATTACHMENT_ADDED" -> "Archivo adjunto agregado";
            case "REMOTE_SESSION_PROPOSED" -> "Sesión remota propuesta";
            case "REMOTE_SESSION_CONFIRMED" -> "Sesión remota confirmada";
            case "REMOTE_SESSION_DECLINED" -> "Sesión remota rechazada";
            case "REMOTE_SESSION_CANCELLED" -> "Sesión remota cancelada";
            case "REMOTE_SESSION_COMPLETED" -> "Sesión remota finalizada";
            case "UPDATE_CASE" -> "Caso actualizado";
            case "CHANGE_CASE_STATUS" -> "Cambio de estado";
            case "CREATE_ENTRY" -> "Nueva atención o comentario";
            case "VOID_ENTRY" -> "Atención anulada";
            case "CREATE_PAYMENT" -> "Pago registrado";
            case "VOID_PAYMENT" -> "Pago anulado";
            default -> "Actualización";
        };
    }
}
