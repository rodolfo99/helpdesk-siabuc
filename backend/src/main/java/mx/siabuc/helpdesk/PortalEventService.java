package mx.siabuc.helpdesk;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional portal events, independent of SMTP and of the authenticated HTTP session. */
@Service
public class PortalEventService {
    private final JdbcTemplate db;
    public PortalEventService(JdbcTemplate db) { this.db = db; }

    @Transactional
    public void caseEvent(long caseId, String eventType, String description, boolean internal, String actor) {
        var cases = db.query("SELECT id,institution_id,title,status,assigned_admin_id FROM support_cases WHERE id=?",
            (r,n) -> new CaseInfo(r.getLong("id"),r.getLong("institution_id"),r.getString("title"),
                r.getString("status"),r.getObject("assigned_admin_id",Long.class)), caseId);
        if (cases.isEmpty()) throw ApiException.missing();
        CaseInfo current = cases.get(0);
        String actorRole = db.queryForObject("SELECT role FROM app_users WHERE username=?", String.class, actor);
        if ("CLOSED".equals(current.status())) {
            db.update("UPDATE support_cases SET waiting_for='NONE' WHERE id=?", caseId);
        } else if ("CREATE_CASE".equals(eventType) || "CHANGE_CASE_STATUS".equals(eventType)) {
            db.update("UPDATE support_cases SET waiting_for='ADMIN' WHERE id=?", caseId);
        } else if ("CREATE_ENTRY".equals(eventType) && !internal) {
            db.update("UPDATE support_cases SET waiting_for=? WHERE id=?", "USER".equals(actorRole) ? "ADMIN" : "CLIENT", caseId);
        }
        List<Long> recipients = db.queryForList("""
            SELECT u.id FROM app_users u JOIN institutions i ON i.id=?
            WHERE u.active AND u.username<>? AND
              ((u.role IN ('ADMIN','TECH') AND (?::BIGINT IS NULL OR u.id=?))
               OR (u.role='USER' AND u.institution_id=i.id AND i.active AND NOT ?))
            """, Long.class, current.institutionId(), actor, current.assignedId(), current.assignedId(), internal);
        String title = "Caso #" + caseId + " · " + label(eventType) + " · " + current.title();
        for (long recipient : recipients)
            insert(recipient,current.institutionId(),caseId,eventType,clip(title,300),clip(description,2000),internal);
    }

    /** Locks follow the same institution-first order as the accounting service. */
    @Transactional
    public void checkBalance(long institutionId) {
        var institutions = db.query("""
            SELECT id,name,active,email_allocated_minutes-email_used_minutes AS email_remaining,
              remote_allocated_minutes-remote_used_minutes AS remote_remaining
            FROM institutions WHERE id=? FOR UPDATE
            """, (r,n) -> new Balance(r.getString("name"),r.getBoolean("active"),r.getLong("email_remaining"),r.getLong("remote_remaining")),institutionId);
        if (institutions.isEmpty()) throw ApiException.missing();
        Balance balance = institutions.get(0);
        db.update("INSERT INTO balance_alert_settings(institution_id) VALUES (?) ON CONFLICT (institution_id) DO NOTHING",institutionId);
        Settings settings = db.queryForObject("SELECT * FROM balance_alert_settings WHERE institution_id=? FOR UPDATE",
            (r,n) -> new Settings(r.getBoolean("enabled"),r.getLong("email_threshold_minutes"),r.getLong("remote_threshold_minutes"),
                r.getBoolean("email_low"),r.getBoolean("remote_low")),institutionId);
        if (!settings.enabled() || !balance.active()) return;
        boolean emailLow = balance.email() <= settings.emailThreshold();
        boolean remoteLow = balance.remote() <= settings.remoteThreshold();
        if (emailLow && !settings.emailLow()) lowBalance(institutionId,balance.name(),"correo",balance.email(),settings.emailThreshold());
        if (remoteLow && !settings.remoteLow()) lowBalance(institutionId,balance.name(),"escritorio remoto",balance.remote(),settings.remoteThreshold());
        db.update("UPDATE balance_alert_settings SET email_low=?,remote_low=? WHERE institution_id=?", emailLow,remoteLow,institutionId);
    }

    private void lowBalance(long institutionId, String name, String channel, long remaining, long threshold) {
        List<Long> recipients = db.queryForList("""
            SELECT id FROM app_users WHERE active AND (role IN ('ADMIN','BILLING','TECH') OR (role='USER' AND institution_id=?))
            """,Long.class,institutionId);
        for (long recipient : recipients)
            insert(recipient,institutionId,null,"LOW_BALANCE",clip("Saldo bajo de " + channel + " · " + name,300),
                "Quedan " + remaining + " minutos de soporte por " + channel + ". Umbral de aviso: " + threshold + " minutos.",false);
    }

    private void insert(long recipient, long institution, Long caseId, String event, String title, String description, boolean internal) {
        db.update("""
            INSERT INTO portal_notifications(recipient_id,institution_id,case_id,event_type,title,description,internal)
            VALUES (?,?,?,?,?,?,?)
            """,recipient,institution,caseId,event,title,description,internal);
    }
    private static String label(String event) {
        return switch (event) {
            case "CREATE_CASE" -> "Nuevo caso";
            case "CREATE_ENTRY" -> "Nuevo comentario o atención";
            case "CLAIM_CASE" -> "Caso asignado";
            case "UPDATE_CASE" -> "Datos o responsable actualizados";
            case "CHANGE_CASE_STATUS" -> "Cambio de estado";
            case "VOID_ENTRY" -> "Atención anulada";
            default -> "Actualización";
        };
    }
    private static String clip(String text, int limit) {
        if (text == null) return "";
        return text.length() <= limit ? text : text.substring(0,limit-1) + "…";
    }
    private record CaseInfo(long id,long institutionId,String title,String status,Long assignedId) {}
    private record Balance(String name,boolean active,long email,long remote) {}
    private record Settings(boolean enabled,long emailThreshold,long remoteThreshold,boolean emailLow,boolean remoteLow) {}
}
