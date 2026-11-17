package mx.siabuc.helpdesk;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login lockout applies only to USER accounts; staff keeps current local access. */
@Service
public class ClientLoginService {
    public record Settings(int maxAttempts, int lockMinutes, long revision) {}
    public record SettingsInput(Integer maxAttempts, Integer lockMinutes, Long revision) {}
    public record LockState(long userId, int failedAttempts, Instant lockedUntil) {}
    private final JdbcTemplate db;
    private final HelpdeskService helpdesk;
    public ClientLoginService(JdbcTemplate db, HelpdeskService helpdesk) {
        this.db=db;this.helpdesk=helpdesk;
    }
    private void requireAdmin() {
        if(helpdesk.me().role()!=Models.Role.ADMIN)throw ApiException.forbidden();
    }
    public Settings settings() {
        return db.queryForObject("SELECT max_attempts,lock_minutes,revision FROM client_login_settings WHERE id=1",
            (r,n)->new Settings(r.getInt(1),r.getInt(2),r.getLong(3)));
    }
    @Transactional
    public Settings updateSettings(SettingsInput input) {
        requireAdmin();
        if(input==null || input.maxAttempts()==null || input.lockMinutes()==null || input.revision()==null
            || input.maxAttempts()<3 || input.maxAttempts()>10
            || input.lockMinutes()<1 || input.lockMinutes()>1440 || input.revision()<1)
            throw ApiException.bad("Intentos permitidos: 3 a 10. Bloqueo: 1 a 1440 minutos.");
        int updated=db.update("""
            UPDATE client_login_settings SET max_attempts=?,lock_minutes=?,revision=revision+1,updated_at=NOW()
            WHERE id=1 AND revision=?
            """,input.maxAttempts(),input.lockMinutes(),input.revision());
        if(updated==0)throw ApiException.conflict("Otro administrador modificó estos valores. Actualice la pantalla.");
        db.update("INSERT INTO audit_log(actor,action,entity_type,details) VALUES(?,?,'SETTINGS',?)",
            helpdesk.me().username(),"UPDATE_CLIENT_LOGIN_SETTINGS","Actualización de límites de inicio de sesión de clientes.");
        return settings();
    }
    /** Failure handler is triggered only on rejected form authentication. Lock the row for atomic accounting. */
    @Transactional
    public void failed(String username) {
        // Match UsernamePasswordAuthenticationFilter's normalization exactly.
        // Otherwise a padded username authenticates the account but escapes its counter.
        if(username==null)return;
        username=username.trim();
        if(username.isEmpty())return;
        List<LockState> users=db.query("""
            SELECT id,failed_login_attempts,login_locked_until FROM app_users
            WHERE username=? AND role='USER' FOR UPDATE
            """,(r,n)->new LockState(r.getLong(1),r.getInt(2),
                r.getTimestamp(3)==null?null:r.getTimestamp(3).toInstant()),username);
        if(users.isEmpty())return;
        LockState user=users.get(0);
        if(user.lockedUntil()!=null && user.lockedUntil().isAfter(Instant.now()))return;
        Settings setting=settings();
        int count=user.lockedUntil()!=null?1:Math.min(setting.maxAttempts(),user.failedAttempts()+1);
        if(count>=setting.maxAttempts()) {
            db.update("""
                UPDATE app_users SET failed_login_attempts=0,
                    login_locked_until=NOW()+(? * INTERVAL '1 minute') WHERE id=?
                """,setting.lockMinutes(),user.userId());
            db.update("""
                INSERT INTO audit_log(actor,action,entity_type,entity_id,details)
                VALUES('system','CLIENT_LOGIN_LOCK','USER',?,'Bloqueo temporal por intentos fallidos.')
                """,user.userId());
        } else {
            db.update("UPDATE app_users SET failed_login_attempts=?,login_locked_until=NULL WHERE id=?",
                count,user.userId());
        }
    }
    @Transactional
    public void successful(String username) {
        db.update("""
            UPDATE app_users SET failed_login_attempts=0,login_locked_until=NULL
            WHERE username=? AND role='USER'
            """,username);
    }
    @Transactional(readOnly=true)
    public List<LockState> states() {
        requireAdmin();
        return db.query("""
            SELECT id,failed_login_attempts,login_locked_until FROM app_users
            WHERE role='USER' ORDER BY username
            """,(r,n)->new LockState(r.getLong(1),r.getInt(2),
                r.getTimestamp(3)==null?null:r.getTimestamp(3).toInstant()));
    }
    @Transactional
    public void unlock(long userId) {
        requireAdmin();
        int changed=db.update("""
            UPDATE app_users SET failed_login_attempts=0,login_locked_until=NULL
            WHERE id=? AND role='USER'
            """,userId);
        if(changed==0)throw ApiException.missing();
        db.update("""
            INSERT INTO audit_log(actor,action,entity_type,entity_id,details)
            VALUES(?,'UNLOCK_CLIENT_LOGIN','USER',?,'Desbloqueo manual de cuenta cliente.')
            """,helpdesk.me().username(),userId);
    }
}
