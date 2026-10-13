package mx.siabuc.helpdesk;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Sends real JavaMail SMTP messages exclusively to an in-process loopback sink. */
class SmtpNotificationIT extends ApiIntegrationSupport {
    private static final SmtpSink SMTP = SmtpSink.start();
    private NotificationDispatcher dispatcher;
    @Autowired JdbcTemplate db;
    @Autowired PlatformTransactionManager transactions;
    @Autowired NotificationService notifications;
    private List<Map<String, Object>> deferredFixtures;

    @BeforeEach
    void configureLocalDispatcherWithoutOpeningAnotherDatabasePool() {
        db.update("UPDATE notification_settings SET paused=FALSE WHERE id=1");
        dispatcher = localDispatcher("siabuc@example.test");
        SMTP.rejectMessages = false;
        SMTP.beforeDataResponse = null;
        SMTP.accepted.clear();
        // Prevent delivery of earlier tests' fixtures by deferring their timestamps.
        // Global pause scenarios may suppress them; their history is never deleted.
        deferredFixtures = db.queryForList("SELECT id,available_at FROM notification_outbox WHERE status='PENDING'");
        db.update("UPDATE notification_outbox SET available_at=NOW()+INTERVAL '1 day' WHERE status='PENDING'");
    }

    private NotificationDispatcher localDispatcher(String fromAddress) {
        return new NotificationDispatcher(db, localMailConfig(fromAddress), transactions);
    }

    private MailConfig localMailConfig(String fromAddress) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("local-smtp-test", Map.ofEntries(
                Map.entry("app.mail.enabled", true), Map.entry("app.mail.host", "127.0.0.1"),
                Map.entry("app.mail.port", SMTP.port()), Map.entry("app.mail.auth", false),
                Map.entry("app.mail.security", "NONE"), Map.entry("app.mail.from-address", fromAddress),
                Map.entry("app.mail.from-name", "SIABUC pruebas"), Map.entry("app.mail.password", "secreto-smtp-solo-pruebas"),
                Map.entry("app.mail.max-attempts", 2), Map.entry("app.mail.poll-ms", 3_600_000))));
        // Use the real production dispatcher, JDBC template and transaction manager.
        // Only SMTP configuration differs; the shared Spring context remains intact.
        return new MailConfig(environment);
    }

    @AfterEach
    void restorePreviousFixtures() {
        SMTP.beforeDataResponse = null;
        db.update("UPDATE notification_settings SET paused=FALSE WHERE id=1");
        if (deferredFixtures == null) return;
        for (Map<String, Object> row : deferredFixtures)
            db.update("UPDATE notification_outbox SET available_at=? WHERE id=?", row.get("available_at"), row.get("id"));
    }

    @AfterAll static void stopLocalSmtp() throws Exception { SMTP.close(); }

    @Test
    void actualSmtpDeliversPublicEventsIndividuallyAndKeepsInternalNotesOutOfClientMail() throws Exception {
        long institution = institution(50, 50);
        long caseId = supportCase(institution, "EMAIL");
        String secret = "Nota privada SMTP " + UUID.randomUUID();
        addEntry(admin, caseId, secret, "EMAIL", 3, true, 200);
        assertStatus(admin.post("/cases/" + caseId + "/status", Map.of("status", "CLOSED", "reason", "Servicio concluido")), 200);
        drainReady();
        List<Delivered> messages = forCase(caseId);
        assertThat(messages).hasSize(5);
        assertThat(messages.stream().filter(m -> m.to().equals("contacto@example.test")).count()).isEqualTo(2);
        assertThat(messages.stream().filter(m -> m.to().equals("testadmin@example.test")).count()).isEqualTo(3);
        for (Delivered message : messages) {
            assertThat(message.envelope().recipients()).hasSize(1);
            assertThat(message.mime().getAllRecipients()).hasSize(1);
            assertThat(message.mime().getFrom()[0].toString()).contains("siabuc@example.test");
            assertThat(message.body()).contains("Caso: #" + caseId, "Institución:", "testadmin");
            if (message.to().equals("contacto@example.test")) assertThat(message.body()).doesNotContain(secret);
        }
        assertThat(messages.stream().filter(m -> m.body().contains(secret)).count()).isEqualTo(1);
        assertThat(messages.stream().filter(m -> m.body().contains("Estado: Cerrado")).count()).isEqualTo(2);
        assertThat(db.queryForList("SELECT status FROM notification_outbox WHERE case_id=?", String.class, caseId))
                .hasSize(5).containsOnly("SENT");
    }

    @Test
    void actualSmtpPaymentContainsAmountsMinutesAndBothSeparateRecipients() throws Exception {
        long institution = institution(20, 20);
        long payment = success(admin.post("/payments", PaymentIntegrationIT.paymentPayload(institution, 15, 25))).path("id").asLong();
        drainReady();
        List<Delivered> payments = decoded().stream().filter(m -> m.body().contains("Pago: #" + payment + "\n")).toList();
        assertThat(payments).hasSize(2);
        assertThat(payments.stream().map(Delivered::to).toList()).containsExactlyInAnyOrder("contacto@example.test", "testadmin@example.test");
        for (Delivered mail : payments) assertThat(mail.body()).contains("250.00 MXN", "Minutos de correo: 15", "Minutos de escritorio remoto: 25");
        assertThat(db.queryForList("SELECT status FROM notification_outbox WHERE payment_id=?", String.class, payment)).containsOnly("SENT");
    }

    @Test
    void actualJavaMailPreservesIpv4DomainLiteralInSmtpEnvelopeAndMimeSender() throws Exception {
        String fromAddress = "soporte@[192.168.1.50]";
        dispatcher = localDispatcher(fromAddress);
        long id = success(admin.post("/notifications/test", Map.of())).path("id").asLong();
        dispatcher.dispatchPending();
        assertDeliveryState(id, "SENT", 1);
        List<Delivered> delivered = decoded();
        assertThat(delivered).hasSize(1);
        Delivered mail = delivered.get(0);
        assertThat(mail.envelope().sender()).startsWith("<" + fromAddress + ">");
        assertThat(((jakarta.mail.internet.InternetAddress) mail.mime().getFrom()[0]).getAddress()).isEqualTo(fromAddress);
        assertThat(mail.to()).isEqualTo("testadmin@example.test");
        assertThat(mail.body()).contains("testadmin");
    }

    @Test
    void failedSmtpStopsAtConfiguredAttemptsAndManualRetryPreservesOriginalRecipient() throws Exception {
        long id = success(admin.post("/notifications/test", Map.of())).path("id").asLong();
        SMTP.rejectMessages = true;
        dispatcher.dispatchPending();
        assertDeliveryState(id, "PENDING", 1);
        assertThat(SMTP.accepted).isEmpty();
        db.update("UPDATE notification_outbox SET available_at=NOW() WHERE id=?", id);
        dispatcher.dispatchPending();
        assertDeliveryState(id, "FAILED", 2);
        dispatcher.dispatchPending();
        assertDeliveryState(id, "FAILED", 2);
        assertThat(db.queryForObject("SELECT last_error FROM notification_outbox WHERE id=?", String.class, id))
                .isNotBlank().doesNotContain("secreto-smtp-solo-pruebas");
        assertStatus(admin.post("/notifications/" + id + "/retry", Map.of("recipient", "arbitrario@example.test")), 400);
        assertStatus(admin.post("/notifications/" + id + "/retry", Map.of()), 200);
        assertDeliveryState(id, "PENDING", 0);
        SMTP.rejectMessages = false;
        dispatcher.dispatchPending();
        assertDeliveryState(id, "SENT", 1);
        assertThat(decoded()).hasSize(1);
        assertThat(decoded().get(0).to()).isEqualTo("testadmin@example.test");
        assertStatus(admin.post("/notifications/" + id + "/retry", Map.of()), 409);
        assertThat(json(admin.get("/notifications/config")).toString()).doesNotContain("secreto-smtp-solo-pruebas", "password");
    }

    @Test
    void expiredClaimIsRecoveredWithoutLosingTheQueuedNotification() throws Exception {
        long id = success(admin.post("/notifications/test", Map.of())).path("id").asLong();
        db.update("UPDATE notification_outbox SET status='SENDING',attempts=1,claim_token=?,claimed_until=NOW()-INTERVAL '1 minute' WHERE id=?", UUID.randomUUID(), id);
        dispatcher.dispatchPending();
        assertDeliveryState(id, "SENT", 2);
        assertThat(decoded()).hasSize(1);
        assertThat(decoded().get(0).to()).isEqualTo("testadmin@example.test");
    }

    @Test
    void pauseKeepsBusinessHistoryButSuppressesOldAndPausedEventsAndResumeOnlySendsNewEvents() throws Exception {
        long institution = institution(50, 50);
        long caseId = supportCase(institution, "EMAIL");
        assertStatus(admin.post("/notifications/config", Map.of("paused", true)), 200);
        // Case work continues normally, including balance usage and public history.
        addEntry(admin, caseId, "Trabajo registrado durante la pausa", "EMAIL", 7, false, 200);
        assertStatus(admin.post("/cases/" + caseId + "/status", Map.of("status", "CLOSED", "reason", "Solución durante pausa")), 200);
        assertBalance(institution, 7, 43, 0, 50);
        assertThat(json(institutionUser(institution).get("/cases/" + caseId)).toString())
                .contains("Trabajo registrado durante la pausa", "Solución durante pausa");

        var before = db.queryForList("SELECT id,status,attempts,available_at,subject,body,recipient FROM notification_outbox WHERE case_id=? ORDER BY id", caseId);
        assertThat(before).hasSize(6);
        assertThat(before).allSatisfy(row -> {
            assertThat(row.get("status")).isEqualTo("SUPPRESSED");
            assertThat(((Number) row.get("attempts")).intValue()).isZero();
        });
        var configured = new NotificationService(db, localMailConfig("siabuc@example.test")).configuration();
        assertThat(configured.configuredEnabled()).isTrue();
        assertThat(configured.paused()).isTrue();
        assertThat(configured.enabled()).isFalse();
        // A newly constructed dispatcher also honors the persisted pause.
        dispatcher = localDispatcher("siabuc@example.test");
        assertThat(dispatcher.dispatchPending()).isZero();
        assertThat(dispatcher.dispatchPending()).isZero();
        assertThat(SMTP.accepted).isEmpty();
        assertThat(db.queryForList("SELECT id,status,attempts,available_at,subject,body,recipient FROM notification_outbox WHERE case_id=? ORDER BY id", caseId))
                .isEqualTo(before);

        assertStatus(admin.post("/notifications/config", Map.of("paused", false)), 200);
        assertThat(new NotificationService(db, localMailConfig("siabuc@example.test")).configuration().enabled()).isTrue();
        drainReady();
        assertThat(forCase(caseId)).isEmpty();
        assertThat(db.queryForList("SELECT status FROM notification_outbox WHERE case_id=?", String.class, caseId)).containsOnly("SUPPRESSED");
        assertThat(db.queryForList("SELECT attempts FROM notification_outbox WHERE case_id=?", Integer.class, caseId)).containsOnly(0);
        assertStatus(admin.post("/notifications/" + before.get(0).get("id") + "/retry", Map.of()), 409);
        assertThat(db.queryForList("SELECT id FROM notification_outbox WHERE case_id=? ORDER BY id", Long.class, caseId))
                .containsExactlyElementsOf(before.stream().map(row -> ((Number) row.get("id")).longValue()).toList());
        long newCaseId = supportCase(institution, "EMAIL");
        drainReady();
        assertThat(forCase(newCaseId)).hasSize(2);
        assertThat(forCase(caseId)).isEmpty();
        assertThat(db.queryForList("SELECT status FROM notification_outbox WHERE case_id=?", String.class, newCaseId)).containsOnly("SENT");
    }

    @Test
    void expiredPrePauseClaimIsSuppressedEvenWhenResumeHappensBeforeRecovery() throws Exception {
        long id = success(admin.post("/notifications/test", Map.of())).path("id").asLong();
        db.update("UPDATE notification_outbox SET status='SENDING',attempts=1,claim_token=?,claimed_until=NOW()-INTERVAL '1 minute' WHERE id=?", UUID.randomUUID(), id);
        assertStatus(admin.post("/notifications/config", Map.of("paused", true)), 200);
        assertStatus(admin.post("/notifications/config", Map.of("paused", false)), 200);
        assertThat(dispatcher.dispatchPending()).isZero();
        assertDeliveryState(id, "SUPPRESSED", 1);
        assertThat(decoded()).isEmpty();
        assertStatus(admin.post("/notifications/" + id + "/retry", Map.of()), 409);
    }

    @Test
    void inFlightFailureAfterPauseAndResumeCannotRequeueAnOldEvent() throws Exception {
        long oldId = success(admin.post("/notifications/test", Map.of())).path("id").asLong();
        SMTP.rejectMessages = true;
        // Pause and resume while the real SMTP socket is awaiting its DATA response.
        // Both changes commit before the dispatcher receives the transient rejection.
        SMTP.beforeDataResponse = () -> {
            notifications.setPaused(true, "testadmin");
            notifications.setPaused(false, "testadmin");
        };
        assertThat(dispatcher.dispatchPending()).isZero();
        assertDeliveryState(oldId, "SUPPRESSED", 1);
        assertThat(SMTP.accepted).isEmpty();
        SMTP.beforeDataResponse = null;
        SMTP.rejectMessages = false;
        assertThat(dispatcher.dispatchPending()).isZero();
        assertStatus(admin.post("/notifications/" + oldId + "/retry", Map.of()), 409);
        long newId = success(admin.post("/notifications/test", Map.of())).path("id").asLong();
        assertThat(dispatcher.dispatchPending()).isEqualTo(1);
        assertDeliveryState(newId, "SENT", 1);
        assertDeliveryState(oldId, "SUPPRESSED", 1);
        assertThat(decoded()).hasSize(1);
    }

    private void assertDeliveryState(long id, String status, int attempts) {
        Map<String, Object> row = db.queryForMap("SELECT status,attempts,sent_at FROM notification_outbox WHERE id=?", id);
        assertThat(row.get("status")).isEqualTo(status);
        assertThat(((Number) row.get("attempts")).intValue()).isEqualTo(attempts);
        if (status.equals("SENT")) assertThat(row.get("sent_at")).isNotNull();
    }

    private void drainReady() {
        for (int i = 0; i < 100; i++) {
            long ready = db.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE status='PENDING' AND available_at<=NOW()", Long.class);
            if (ready == 0) return;
            dispatcher.dispatchPending();
        }
        throw new AssertionError("La cola SMTP local no terminó en 100 lotes");
    }

    private List<Delivered> forCase(long caseId) throws Exception {
        return decoded().stream().filter(m -> m.body().contains("Caso: #" + caseId + "\n")).toList();
    }

    private List<Delivered> decoded() throws Exception {
        List<Delivered> result = new ArrayList<>();
        for (Envelope envelope : SMTP.accepted) {
            MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(envelope.data()));
            String body = mime.getContent().toString().replace("\r\n", "\n");
            String to = ((jakarta.mail.internet.InternetAddress) mime.getRecipients(Message.RecipientType.TO)[0]).getAddress();
            result.add(new Delivered(envelope, mime, to, body));
        }
        return result;
    }

    private record Delivered(Envelope envelope, MimeMessage mime, String to, String body) {}
    private record Envelope(String sender, List<String> recipients, byte[] data) {}

    private static final class SmtpSink implements AutoCloseable {
        private final ServerSocket server;
        private final ExecutorService workers = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "local-smtp-test"); t.setDaemon(true); return t; });
        private final List<Envelope> accepted = new CopyOnWriteArrayList<>();
        private volatile boolean rejectMessages;
        private volatile Runnable beforeDataResponse;
        private volatile boolean closed;

        private SmtpSink() throws IOException {
            server = new ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"));
            workers.submit(() -> {
                while (!closed) {
                    try { Socket socket = server.accept(); workers.submit(() -> serve(socket)); }
                    catch (IOException e) { if (!closed) throw new IllegalStateException(e); }
                }
            });
        }
        static SmtpSink start() { try { return new SmtpSink(); } catch (IOException e) { throw new IllegalStateException(e); } }
        int port() { return server.getLocalPort(); }

        private void serve(Socket socket) {
            try (socket;
                 var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                socket.setSoTimeout(10_000);
                reply(writer, "220 localhost SIABUC SMTP test sink");
                List<String> recipients = new ArrayList<>();
                String sender = "";
                for (String line; (line = reader.readLine()) != null;) {
                    String upper = line.toUpperCase(Locale.ROOT);
                    if (upper.startsWith("EHLO") || upper.startsWith("HELO")) reply(writer, "250-localhost\r\n250-8BITMIME\r\n250 SIZE 5242880");
                    else if (upper.startsWith("MAIL FROM:")) { sender = line.substring(10).strip(); recipients.clear(); reply(writer, "250 sender accepted"); }
                    else if (upper.startsWith("RCPT TO:")) { recipients.add(line.substring(8).strip()); reply(writer, "250 recipient accepted"); }
                    else if (upper.equals("DATA")) {
                        reply(writer, "354 End with dot on a line");
                        StringBuilder data = new StringBuilder();
                        while ((line = reader.readLine()) != null && !line.equals(".")) data.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                        Runnable hook = beforeDataResponse;
                        if (hook != null) hook.run();
                        if (rejectMessages) reply(writer, "451 temporary rejection for retry test");
                        else { accepted.add(new Envelope(sender, List.copyOf(recipients), data.toString().getBytes(StandardCharsets.UTF_8))); reply(writer, "250 message accepted"); }
                    } else if (upper.equals("QUIT")) { reply(writer, "221 bye"); return; }
                    else if (upper.equals("RSET") || upper.equals("NOOP")) reply(writer, "250 OK");
                    else reply(writer, "502 unsupported command");
                }
            } catch (IOException e) { if (!closed) throw new IllegalStateException(e); }
        }
        private static void reply(BufferedWriter writer, String value) throws IOException { writer.write(value); writer.write("\r\n"); writer.flush(); }
        @Override public void close() throws Exception { closed = true; server.close(); workers.shutdownNow(); assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }
}
