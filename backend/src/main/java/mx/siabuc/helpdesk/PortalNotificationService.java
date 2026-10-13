package mx.siabuc.helpdesk;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalNotificationService {
    private final JdbcTemplate db;
    private final HelpdeskService helpdesk;
    private final PortalEventService events;
    public PortalNotificationService(JdbcTemplate db, HelpdeskService helpdesk, PortalEventService events) {
        this.db=db; this.helpdesk=helpdesk; this.events=events;
    }
    public record Item(long id,long institutionId,Long caseId,String eventType,String title,String description,
                       boolean internal,Instant createdAt,Instant readAt) {}
    public record PendingReply(long caseId,String title,String institutionName,String waitingFor,String assignedAdminName,Instant updatedAt) {}
    public record Inbox(List<Item> items,long unreadCount,List<PendingReply> pendingReplies) {}
    public record BalanceAlertSetting(long institutionId,String institutionName,long emailThresholdMinutes,
                       long remoteThresholdMinutes,boolean enabled,long emailRemainingMinutes,long remoteRemainingMinutes) {}

    // Re-evaluate the current account role and institution, including notices created before an account transfer.
    private static final String VISIBLE = """
         n.recipient_id=? AND ( ?='ADMIN'
           OR (?='BILLING' AND n.case_id IS NULL AND NOT n.internal AND n.event_type='LOW_BALANCE')
           OR (?='USER' AND n.institution_id=? AND i.active AND NOT n.internal))
         AND (n.case_id IS NULL OR c.institution_id=n.institution_id)
        """;
    private static final String FROM = " FROM portal_notifications n JOIN institutions i ON i.id=n.institution_id LEFT JOIN support_cases c ON c.id=n.case_id WHERE ";

    @Transactional(readOnly=true)
    public Inbox inbox() {
        Models.User actor = helpdesk.me();
        List<Item> items = db.query("SELECT n.*" + FROM + VISIBLE + " ORDER BY n.created_at DESC,n.id DESC LIMIT 100",
            (r,n) -> new Item(r.getLong("id"),r.getLong("institution_id"),r.getObject("case_id",Long.class),
                r.getString("event_type"),r.getString("title"),r.getString("description"),r.getBoolean("internal"),
                r.getTimestamp("created_at").toInstant(),r.getTimestamp("read_at")==null ? null:r.getTimestamp("read_at").toInstant()),args(actor));
        long unread = db.queryForObject("SELECT COUNT(*)" + FROM + VISIBLE + " AND n.read_at IS NULL",Long.class,args(actor));
        return new Inbox(items,unread,pending(actor));
    }

    private Object[] args(Models.User actor) {
        return new Object[]{actor.id(),actor.role().name(),actor.role().name(),actor.role().name(),actor.institutionId()};
    }
    private List<PendingReply> pending(Models.User actor) {
        if (actor.role() == Models.Role.BILLING) return List.of();
        return db.query("""
            SELECT c.id,c.title,c.waiting_for,c.updated_at,i.name,COALESCE(u.display_name,'Sin administrativo asignado') AS assigned_name
            FROM support_cases c JOIN institutions i ON i.id=c.institution_id LEFT JOIN app_users u ON u.id=c.assigned_admin_id
            WHERE c.status='OPEN' AND i.active AND
              ((?='ADMIN' AND c.waiting_for='ADMIN' AND (c.assigned_admin_id=? OR c.assigned_admin_id IS NULL))
               OR (?='USER' AND c.waiting_for='CLIENT' AND c.institution_id=?))
            ORDER BY c.updated_at,c.id LIMIT 100
            """,(r,n) -> new PendingReply(r.getLong("id"),r.getString("title"),r.getString("name"),
                r.getString("waiting_for"),r.getString("assigned_name"),r.getTimestamp("updated_at").toInstant()),
            actor.role().name(),actor.id(),actor.role().name(),actor.institutionId());
    }

    @Transactional
    public void markRead(long id) {
        Models.User actor = helpdesk.me();
        Object[] base=args(actor), values=new Object[base.length+1];
        System.arraycopy(base,0,values,0,base.length); values[base.length]=id;
        List<Long> allowed = db.queryForList("SELECT n.id" + FROM + VISIBLE + " AND n.id=?",Long.class,values);
        if (allowed.isEmpty()) throw ApiException.missing();
        db.update("UPDATE portal_notifications SET read_at=COALESCE(read_at,NOW()) WHERE id=? AND recipient_id=?",id,actor.id());
    }

    @Transactional
    public void markAllRead() {
        Models.User actor = helpdesk.me();
        db.update("UPDATE portal_notifications SET read_at=NOW() WHERE read_at IS NULL AND id IN (SELECT n.id" + FROM + VISIBLE + ")",args(actor));
    }

    public List<BalanceAlertSetting> settings() {
        requireAdmin();
        return db.query("""
            SELECT i.id,i.name,COALESCE(s.email_threshold_minutes,60) AS email_threshold,
              COALESCE(s.remote_threshold_minutes,30) AS remote_threshold,COALESCE(s.enabled,TRUE) AS enabled,
              i.email_allocated_minutes-i.email_used_minutes AS email_remaining,
              i.remote_allocated_minutes-i.remote_used_minutes AS remote_remaining
            FROM institutions i LEFT JOIN balance_alert_settings s ON s.institution_id=i.id
            WHERE i.active ORDER BY i.name,i.id
            """,(r,n) -> new BalanceAlertSetting(r.getLong("id"),r.getString("name"),r.getLong("email_threshold"),
                r.getLong("remote_threshold"),r.getBoolean("enabled"),r.getLong("email_remaining"),r.getLong("remote_remaining")));
    }

    @Transactional
    public BalanceAlertSetting updateSettings(long institutionId,long emailThreshold,long remoteThreshold,boolean enabled) {
        Models.User actor = requireAdmin();
        if (emailThreshold<0 || emailThreshold>1000000000 || remoteThreshold<0 || remoteThreshold>1000000000)
            throw ApiException.bad("Los umbrales deben ser minutos enteros entre 0 y 1000000000.");
        if (db.queryForList("SELECT id FROM institutions WHERE id=? AND active FOR UPDATE",Long.class,institutionId).isEmpty())
            throw ApiException.missing();
        db.update("""
            INSERT INTO balance_alert_settings(institution_id,email_threshold_minutes,remote_threshold_minutes,enabled)
            VALUES (?,?,?,?) ON CONFLICT (institution_id) DO UPDATE SET email_threshold_minutes=EXCLUDED.email_threshold_minutes,
              remote_threshold_minutes=EXCLUDED.remote_threshold_minutes,enabled=EXCLUDED.enabled,updated_at=NOW()
            """,institutionId,emailThreshold,remoteThreshold,enabled);
        events.checkBalance(institutionId);
        db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,?,?,?,?)",actor.username(),
            "UPDATE_BALANCE_ALERTS","INSTITUTION",institutionId,"Avisos de saldo: correo="+emailThreshold+"; remoto="+remoteThreshold+"; habilitados="+enabled);
        return settings().stream().filter(s -> s.institutionId()==institutionId).findFirst().orElseThrow(ApiException::missing);
    }
    private Models.User requireAdmin() {
        Models.User actor=helpdesk.me();
        if (actor.role()!=Models.Role.ADMIN) throw ApiException.forbidden();
        return actor;
    }

    /** Catches pre-existing balances after migration as well as ordinary accounting hooks. */
    @Scheduled(initialDelayString="${app.balance-alert-scan-ms:60000}",fixedDelayString="${app.balance-alert-scan-ms:60000}")
    public void scanBalances() {
        for (Long institution : db.queryForList("SELECT id FROM institutions WHERE active ORDER BY id",Long.class))
            events.checkBalance(institution);
    }
}
