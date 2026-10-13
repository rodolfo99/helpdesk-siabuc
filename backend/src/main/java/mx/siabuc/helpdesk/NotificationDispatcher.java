package mx.siabuc.helpdesk;

import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** At-least-once delivery: an SMTP acceptance followed by a DB failure can produce a duplicate. */
@Component
@EnableScheduling
public class NotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private final JdbcTemplate db;
    private final MailConfig config;
    private final TransactionTemplate transaction;
    private final JavaMailSenderImpl sender;
    private record Claim(long id, String recipient, String subject, String body, int attempts, UUID token) {}

    public NotificationDispatcher(JdbcTemplate db, MailConfig config, PlatformTransactionManager manager) {
        this.db = db;
        this.config = config;
        this.transaction = new TransactionTemplate(manager);
        sender = new JavaMailSenderImpl();
        sender.setHost(config.host);
        sender.setPort(config.port);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (config.auth) { sender.setUsername(config.username); sender.setPassword(config.password); }
        var properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", Boolean.toString(config.auth));
        properties.setProperty("mail.smtp.starttls.enable", Boolean.toString("STARTTLS".equals(config.security)));
        properties.setProperty("mail.smtp.starttls.required", Boolean.toString("STARTTLS".equals(config.security)));
        properties.setProperty("mail.smtp.ssl.enable", Boolean.toString("SSL".equals(config.security)));
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
        properties.setProperty("mail.smtp.sendpartial", "false");
        properties.setProperty("mail.debug", "false");
    }

    @Scheduled(fixedDelayString = "${app.mail.poll-ms:15000}", initialDelayString = "${app.mail.poll-ms:15000}")
    public void scheduledDispatch() {
        try { dispatchPending(); }
        catch (org.springframework.dao.DataAccessException e) {
            // Never log SMTP exception text: a remote server can echo credentials or message contents.
            log.warn("No se pudo consultar o actualizar la cola de correo: {}", e.getClass().getSimpleName());
        }
    }

    /** Bounded batch, also callable by integration tests using a local SMTP sink. */
    public int dispatchPending() {
        if (!config.enabled) return 0;
        recoverExpiredClaims();
        int sent = 0;
        for (int i = 0; i < 20; i++) {
            Claim claim = claimNext();
            if (claim == null) break;
            try {
                send(claim); // No database transaction or row lock is held while contacting SMTP.
                sent += db.update("""
                    UPDATE notification_outbox SET status='SENT',sent_at=NOW(),last_error=NULL,claim_token=NULL,claimed_until=NULL
                    WHERE id=? AND status='SENDING' AND claim_token=?
                    """, claim.id(), claim.token());
            } catch (MessagingException | java.io.UnsupportedEncodingException | MailException e) {
                String error = e instanceof MailAuthenticationException
                    ? "SMTP rechazó la autenticación. Revise la configuración del servidor."
                    : "No fue posible entregar el correo por SMTP. Revise conexión, TLS y remitente autorizados.";
                long backoffSeconds = Math.min(3600L, 30L * (1L << Math.min(claim.attempts() - 1, 7)));
                db.update("""
                    UPDATE notification_outbox SET status=?,last_error=?,available_at=NOW()+(? * INTERVAL '1 second'),
                        claim_token=NULL,claimed_until=NULL WHERE id=? AND status='SENDING' AND claim_token=?
                    """, claim.attempts() >= config.maxAttempts ? "FAILED" : "PENDING", error,
                        backoffSeconds, claim.id(), claim.token());
            }
        }
        return sent;
    }
    private void recoverExpiredClaims() {
        transaction.executeWithoutResult(status -> {
            db.update("""
                UPDATE notification_outbox SET status=CASE WHEN attempts>=? THEN 'FAILED' ELSE 'PENDING' END,
                    last_error='El envío no confirmó su resultado antes de vencer el bloqueo. Puede haberse entregado; un reintento podría duplicarlo.',
                    available_at=NOW(),claim_token=NULL,claimed_until=NULL
                WHERE status='SENDING' AND claimed_until<NOW()
                """, config.maxAttempts);
            // A lower configured limit must not leave previously retried messages pending forever.
            db.update("""
                UPDATE notification_outbox SET status='FAILED',last_error='Se alcanzó el límite configurado de intentos.'
                WHERE status='PENDING' AND attempts>=?
                """, config.maxAttempts);
        });
    }
    private Claim claimNext() {
        return transaction.execute(status -> {
            List<Claim> rows = db.query("""
                SELECT id,recipient,subject,body,attempts FROM notification_outbox
                WHERE status='PENDING' AND attempts<? AND available_at<=NOW()
                ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (r, n) -> new Claim(r.getLong("id"), r.getString("recipient"), r.getString("subject"),
                    r.getString("body"), r.getInt("attempts") + 1, UUID.randomUUID()), config.maxAttempts);
            if (rows.isEmpty()) return null;
            Claim claim = rows.get(0);
            db.update("""
                UPDATE notification_outbox SET status='SENDING',attempts=attempts+1,claim_token=?,
                    claimed_until=NOW()+INTERVAL '5 minutes' WHERE id=?
                """, claim.token(), claim.id());
            return claim;
        });
    }
    private void send(Claim claim) throws MessagingException, java.io.UnsupportedEncodingException {
        if (MailConfig.email(claim.recipient()) == null) throw new MessagingException("Destinatario inválido.");
        var message = sender.createMimeMessage();
        var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        helper.setFrom(config.fromAddress, config.fromName);
        helper.setTo(claim.recipient());
        if (!config.replyTo.isBlank()) helper.setReplyTo(config.replyTo);
        helper.setSubject(MailConfig.subject(claim.subject()));
        helper.setText(claim.body(), false);
        message.setHeader("X-SIABUC-Notification-ID", Long.toString(claim.id()));
        sender.send(message);
    }
}
