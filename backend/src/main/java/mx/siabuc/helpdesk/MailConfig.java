package mx.siabuc.helpdesk;

import jakarta.mail.internet.InternetAddress;
import java.util.Locale;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** SMTP secrets remain in this server-only object and are never returned by the API. */
@Component
public final class MailConfig {
    final boolean enabled;
    final String host, username, password, security, fromAddress, fromName, replyTo;
    final int port, maxAttempts;
    final long pollMs;
    final boolean auth;

    public MailConfig(Environment env) {
        enabled = env.getProperty("app.mail.enabled", Boolean.class, false);
        host = env.getProperty("app.mail.host", "").strip();
        port = env.getProperty("app.mail.port", Integer.class, 587);
        username = env.getProperty("app.mail.username", "");
        password = env.getProperty("app.mail.password", "");
        auth = env.getProperty("app.mail.auth", Boolean.class, true);
        security = env.getProperty("app.mail.security", "STARTTLS").strip().toUpperCase(Locale.ROOT);
        fromAddress = env.getProperty("app.mail.from-address", "").strip();
        fromName = env.getProperty("app.mail.from-name", "SIABUC").strip();
        replyTo = env.getProperty("app.mail.reply-to", "").strip();
        maxAttempts = env.getProperty("app.mail.max-attempts", Integer.class, 5);
        pollMs = env.getProperty("app.mail.poll-ms", Long.class, 15000L);
        if (port < 1 || port > 65535) invalid("SMTP_PORT debe estar entre 1 y 65535.");
        if (maxAttempts < 1 || maxAttempts > 5) invalid("MAIL_MAX_ATTEMPTS debe estar entre 1 y 5.");
        if (pollMs < 1000 || pollMs > 3600000) invalid("MAIL_POLL_MS debe estar entre 1000 y 3600000.");
        if (!java.util.Set.of("STARTTLS", "SSL", "NONE").contains(security)) invalid("SMTP_SECURITY debe ser STARTTLS, SSL o NONE.");
        if (enabled) {
            if (host.isBlank() || host.length() > 253 || host.chars().anyMatch(Character::isWhitespace)) invalid("Configure SMTP_HOST para habilitar el correo.");
            if (email(fromAddress) == null) invalid("Configure MAIL_FROM con una sola dirección válida.");
            if (!replyTo.isBlank() && email(replyTo) == null) invalid("MAIL_REPLY_TO debe ser una sola dirección válida.");
            if (fromName.length() > 200 || hasHeaderControl(fromName)) invalid("MAIL_FROM_NAME no debe contener saltos de línea y admite 200 caracteres.");
            if (auth && (username.isBlank() || password.isBlank())) invalid("SMTP_AUTH requiere SMTP_USERNAME y SMTP_PASSWORD configurados.");
        }
    }
    private static void invalid(String message) { throw new IllegalStateException(message); }
    static boolean hasHeaderControl(String value) { return value.chars().anyMatch(c -> c < 32 || c == 127); }
    /** Accept a single mailbox, never a display name, recipient list or injected header. */
    static String email(String value) {
        if (value == null) return null;
        String cleaned = value.strip();
        if (cleaned.isEmpty() || cleaned.length() > 254 || hasHeaderControl(cleaned)
                || !cleaned.matches("^[^\\s@<>,;]+@[^\\s@<>,;]+\\.[^\\s@<>,;]+$")) return null;
        try {
            InternetAddress parsed = new InternetAddress(cleaned, true);
            parsed.validate();
            if (parsed.isGroup() || parsed.getPersonal() != null || !parsed.getAddress().equals(cleaned)) return null;
            return cleaned;
        } catch (jakarta.mail.internet.AddressException e) { return null; }
    }
    static String subject(String value) {
        String cleaned = value.replaceAll("[\\p{Cntrl}]+", " ").strip();
        return cleaned.substring(0, Math.min(300, cleaned.length()));
    }
}
