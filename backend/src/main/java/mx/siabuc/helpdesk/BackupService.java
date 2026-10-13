package mx.siabuc.helpdesk;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BackupService {
    private final JdbcTemplate db;
    private final HelpdeskService helpdesk;
    public BackupService(JdbcTemplate db,HelpdeskService helpdesk) {this.db=db;this.helpdesk=helpdesk;}
    public record Settings(boolean enabled,String dailyTime,String timeZone,int retentionDays,int keepLast,boolean destinationConfirmed,Instant updatedAt) {}
    public record Configuration(Boolean enabled,String dailyTime,String timeZone,Integer retentionDays,Integer keepLast,Boolean destinationConfirmed) {}
    public record Worker(boolean online,boolean writable,String destinationLabel,String lastError,Instant lastSeenAt) {}
    public record Run(long id,String kind,String status,Instant createdAt,Instant startedAt,Instant finishedAt,Instant retryAt,
        int attempts,String filename,String sha256,Long sizeBytes,String lastError,Instant purgedAt) {}
    public record Attempt(int attempt,Instant startedAt,Instant finishedAt,String status,String error) {}
    public record Status(Settings settings,Worker worker,List<Run> runs,Run lastFailure) {}
    public record Detail(Run run,List<Attempt> attempts) {}

    @Transactional(readOnly=true)
    public Status status() {
        admin();
        List<Run> runs=db.query("SELECT * FROM backup_runs ORDER BY id DESC LIMIT 100",this::run);
        // Preserve the latest failed/retrying run until a later successful backup resolves the warning.
        List<Run> failures=db.query("""
            SELECT * FROM backup_runs WHERE status IN ('FAILED','RETRYING')
              AND id > COALESCE((SELECT MAX(id) FROM backup_runs WHERE status='SUCCEEDED'),0)
            ORDER BY id DESC LIMIT 1
            """,this::run);
        return new Status(settings(),worker(),runs,failures.isEmpty()?null:failures.get(0));
    }
    private Settings settings() {
        return db.queryForObject("SELECT * FROM backup_settings WHERE id=1",(r,n)->new Settings(r.getBoolean("enabled"),
            r.getTime("daily_time").toLocalTime().toString(),r.getString("time_zone"),r.getInt("retention_days"),r.getInt("keep_last"),
            r.getBoolean("destination_confirmed"),r.getTimestamp("updated_at").toInstant()));
    }
    private Worker worker() {
        return db.queryForObject("SELECT *,last_seen_at>NOW()-INTERVAL '2 minutes' AS online FROM backup_runtime WHERE id=1",
            (r,n)->new Worker(r.getBoolean("online"),r.getBoolean("writable"),r.getString("destination_label"),r.getString("last_error"),instant(r,"last_seen_at")));
    }
    @Transactional
    public Status configure(Configuration input) {
        Models.User actor=admin();
        if(input==null||input.enabled()==null||input.destinationConfirmed()==null||input.retentionDays()==null||input.keepLast()==null
                ||input.dailyTime()==null||!input.dailyTime().matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")
                ||input.timeZone()==null||!ZoneId.getAvailableZoneIds().contains(input.timeZone())
                ||input.retentionDays()<1||input.retentionDays()>3650||input.keepLast()<1||input.keepLast()>1000)
            throw ApiException.bad("Indique hora HH:mm, zona IANA válida, retención de 1 a 3650 días y mínimo de 1 a 1000 copias.");
        db.queryForObject("SELECT id FROM backup_settings WHERE id=1 FOR UPDATE",Integer.class);
        if(input.enabled()&&!input.destinationConfirmed())throw ApiException.bad("Confirme el destino de respaldo antes de activar el calendario.");
        if(input.enabled())requireWorker();
        db.update("""
            UPDATE backup_settings SET enabled=?,daily_time=?,time_zone=?,retention_days=?,keep_last=?,destination_confirmed=?,
              updated_at=NOW(),updated_by=? WHERE id=1
            """,input.enabled(),java.sql.Time.valueOf(LocalTime.parse(input.dailyTime())),input.timeZone(),input.retentionDays(),input.keepLast(),input.destinationConfirmed(),actor.id());
        if(!input.enabled())db.update("""
            UPDATE backup_runs SET status='CANCELLED',finished_at=NOW(),retry_at=NULL,last_error='Calendario desactivado por el administrativo.'
            WHERE kind='SCHEDULED' AND status IN ('QUEUED','RETRYING')
            """);
        audit(actor,"CONFIGURE_BACKUPS","Calendario="+input.enabled()+"; hora="+input.dailyTime()+"; zona="+input.timeZone()+"; retención="+input.retentionDays()+"; mínimo="+input.keepLast());
        return status();
    }
    @Transactional
    public Run trigger() {
        Models.User actor=admin();
        db.queryForObject("SELECT id FROM backup_settings WHERE id=1 FOR UPDATE",Integer.class);
        if(!settings().destinationConfirmed())throw ApiException.conflict("Guarde la configuración y confirme el destino antes de solicitar un respaldo.");
        requireWorker();
        if(db.queryForObject("SELECT COUNT(*) FROM backup_runs WHERE status IN ('QUEUED','RUNNING','RETRYING')",Long.class)>0)
            throw ApiException.conflict("Ya hay un respaldo pendiente o en ejecución. Espere a que termine.");
        long id=db.queryForObject("INSERT INTO backup_runs(kind,requested_by) VALUES ('MANUAL',?) RETURNING id",Long.class,actor.id());
        audit(actor,"REQUEST_BACKUP","Respaldo manual solicitado #"+id+". No modifica los datos del sistema.");
        return db.queryForObject("SELECT * FROM backup_runs WHERE id=?",this::run,id);
    }
    @Transactional
    public Status enabled(boolean enabled) {
        Models.User actor=admin();
        db.queryForObject("SELECT id FROM backup_settings WHERE id=1 FOR UPDATE",Integer.class);
        Settings current=settings();
        if(enabled){if(!current.destinationConfirmed())throw ApiException.conflict("Confirme primero el destino de respaldo.");requireWorker();}
        if(current.enabled()!=enabled){
            db.update("UPDATE backup_settings SET enabled=?,updated_at=NOW(),updated_by=? WHERE id=1",enabled,actor.id());
            if(!enabled)db.update("UPDATE backup_runs SET status='CANCELLED',finished_at=NOW(),retry_at=NULL,last_error='Calendario desactivado por el administrativo.' WHERE kind='SCHEDULED' AND status IN ('QUEUED','RETRYING')");
            audit(actor,enabled?"ENABLE_BACKUPS":"DISABLE_BACKUPS",enabled?"Calendario activado desde este momento; no se recuperan horarios perdidos.":"Calendario desactivado; se conservan copias e historial. Una ejecución iniciada puede finalizar.");
        }
        return status();
    }
    public Detail detail(long id) {
        admin();
        List<Run> rows=db.query("SELECT * FROM backup_runs WHERE id=?",this::run,id);
        if(rows.isEmpty())throw ApiException.missing();
        return new Detail(rows.get(0),db.query("SELECT * FROM backup_attempts WHERE run_id=? ORDER BY attempt",
            (r,n)->new Attempt(r.getInt("attempt"),instant(r,"started_at"),instant(r,"finished_at"),r.getString("status"),r.getString("error")),id));
    }
    private Run run(java.sql.ResultSet r,int row) throws java.sql.SQLException {
        return new Run(r.getLong("id"),r.getString("kind"),r.getString("status"),instant(r,"created_at"),instant(r,"started_at"),
            instant(r,"finished_at"),instant(r,"retry_at"),r.getInt("attempts"),r.getString("filename"),r.getString("sha256"),
            r.getObject("size_bytes",Long.class),r.getString("last_error"),instant(r,"purged_at"));
    }
    private static Instant instant(java.sql.ResultSet r,String field) throws java.sql.SQLException {
        var value=r.getTimestamp(field);return value==null?null:value.toInstant();
    }
    private Models.User admin(){Models.User actor=helpdesk.me();if(actor.role()!=Models.Role.ADMIN)throw ApiException.forbidden();return actor;}
    private void requireWorker(){Worker worker=worker();if(!worker.online()||!worker.writable())throw ApiException.conflict("El servicio de respaldos debe estar conectado y tener acceso de escritura al destino.");}
    private void audit(Models.User actor,String action,String details){db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,?, 'BACKUP_CONFIG',1,?)",actor.username(),action,details);}
}
