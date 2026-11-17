package mx.siabuc.helpdesk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Temporary recovery for the existing local username/password customer login.
 * It is NOT recovery of a SIABUC9 license key, policy number or license email.
 */
@Service
public class ClientRecoveryService {
    public record Request(String username, String email) {}
    public record Reset(String username, String code, String newPassword) {}
    private record Account(long id, String email, Instant lastIssued) {}
    private final JdbcTemplate db;
    private final MailConfig mail;
    private final PasswordEncoder passwords;
    private final SecureRandom random=new SecureRandom();
    public ClientRecoveryService(JdbcTemplate db,MailConfig mail,PasswordEncoder passwords) {
        this.db=db;this.mail=mail;this.passwords=passwords;
    }

    /** Always return the same response whether or not the account exists. */
    @Transactional
    public void request(Request request) {
        if(request==null || request.username()==null || request.email()==null ||
            request.username().length()>80 || request.email().length()>254) return;
        String recipient=MailConfig.email(request.email());
        if(recipient==null || request.username().isBlank() || !mail.enabled)return;
        // Lock the candidate account before rate-limiting and replacing its code.
        List<Account> rows=db.query("""
            SELECT u.id,u.email,r.created_at FROM app_users u
            LEFT JOIN client_password_recovery r ON r.user_id=u.id
            JOIN institutions i ON i.id=u.institution_id
            WHERE u.username=? AND u.role='USER' AND u.active AND i.active
            FOR UPDATE OF u
            """,(r,n)->new Account(r.getLong(1),r.getString(2),
                r.getTimestamp(3)==null?null:r.getTimestamp(3).toInstant()),
            request.username().strip());
        if(rows.isEmpty())return;
        Account account=rows.get(0);
        if(account.email()==null || !account.email().equalsIgnoreCase(recipient))return;
        if(account.lastIssued()!=null && account.lastIssued().isAfter(Instant.now().minusSeconds(300)))return;
        Boolean paused=db.queryForObject("SELECT paused FROM notification_settings WHERE id=1 FOR UPDATE",Boolean.class);
        if(Boolean.TRUE.equals(paused))return;
        byte[] codeBytes=new byte[18];
        random.nextBytes(codeBytes);
        String code=HexFormat.of().formatHex(codeBytes).toUpperCase();
        db.update("""
            INSERT INTO client_password_recovery(user_id,token_hash,expires_at,consumed_at,created_at)
            VALUES(?,?,NOW()+INTERVAL '15 minutes',NULL,NOW())
            ON CONFLICT(user_id) DO UPDATE SET token_hash=EXCLUDED.token_hash,expires_at=EXCLUDED.expires_at,
                consumed_at=NULL,created_at=NOW()
            """,account.id(),digest(code));
        db.update("""
            INSERT INTO notification_outbox(event_key,event_type,recipient_type,recipient_entity_id,
                recipient,subject,body,internal,status)
            VALUES(?, 'CLIENT_PASSWORD_RECOVERY','USER',?, ?, ?, ?, FALSE,'PENDING')
            """,UUID.randomUUID(),account.id(),recipient,
                "[SIABUC] Recuperación de acceso",
                "Se solicitó recuperar el acceso al portal de clientes SIABUC.\n\n"+
                "Código de recuperación: "+code+"\n\n"+
                "Válido por 15 minutos, un solo uso. Introdúzcalo en la pantalla de recuperación. "+
                "Si usted no lo solicitó, ignore este mensaje.\n");
    }

    @Transactional
    public void reset(Reset request) {
        if(request==null || request.username()==null || request.code()==null ||
           request.username().length()>80 || !request.code().matches("[0-9a-fA-F]{36}"))
            throw ApiException.bad("Código inválido o caducado.");
        HelpdeskService.validatePassword(request.newPassword());
        List<Long> users=db.queryForList("""
            SELECT id FROM app_users WHERE username=? AND role='USER' AND active FOR UPDATE
            """,Long.class,request.username().strip());
        if(users.isEmpty())throw ApiException.bad("Código inválido o caducado.");
        long id=users.get(0);
        List<Long> valid=db.queryForList("""
            SELECT user_id FROM client_password_recovery
            WHERE user_id=? AND token_hash=? AND consumed_at IS NULL AND expires_at>NOW()
            FOR UPDATE
            """,Long.class,id,digest(request.code().toUpperCase()));
        if(valid.isEmpty())throw ApiException.bad("Código inválido o caducado.");
        db.update("""
            UPDATE app_users SET password_hash=?,auth_version=auth_version+1,
                failed_login_attempts=0,login_locked_until=NULL WHERE id=?
            """,passwords.encode(request.newPassword()),id);
        db.update("UPDATE client_password_recovery SET consumed_at=NOW() WHERE user_id=?",id);
        db.update("""
            INSERT INTO audit_log(actor,action,entity_type,entity_id,details)
            VALUES('system','RECOVER_CLIENT_PASSWORD','USER',?,'Recuperación de contraseña mediante código de correo registrado.')
            """,id);
    }

    private static String digest(String value) {
        try {
            byte[] raw=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
