package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Real authenticated HTTP, durable settings and audit; never contacts external SMTP. */
class NotificationConfigIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    @Autowired MailConfig mailConfig;

    @BeforeEach
    @AfterEach
    void restoreMailSwitchForSharedDatabase() {
        db.update("UPDATE notification_settings SET paused=FALSE WHERE id=1");
    }

    @Test
    void administratorPersistsPauseAndResumeAndAuditsOnlyActualChanges() throws Exception {
        long before = lastAuditId();
        JsonNode paused = success(admin.post("/notifications/config", Map.of("paused", true)));
        assertThat(paused.path("paused").asBoolean()).isTrue();
        assertThat(paused.path("enabled").asBoolean()).isFalse();
        assertThat(paused.path("configuredEnabled").asBoolean()).isFalse();
        assertThat(db.queryForObject("SELECT paused FROM notification_settings WHERE id=1", Boolean.class)).isTrue();

        // A new session and a freshly constructed service read the same durable state.
        assertThat(json(login("testadmin", "PruebaSegura123!").get("/notifications/config")).path("paused").asBoolean()).isTrue();
        assertThat(new NotificationService(db, mailConfig).configuration().paused()).isTrue();
        success(admin.post("/notifications/config", Map.of("paused", true)));
        JsonNode resumed = success(admin.post("/notifications/config", Map.of("paused", false)));
        assertThat(resumed.path("paused").asBoolean()).isFalse();
        // The runtime switch cannot override MAIL_ENABLED=false at application startup.
        assertThat(resumed.path("enabled").asBoolean()).isFalse();
        assertThat(resumed.path("configuredEnabled").asBoolean()).isFalse();
        success(admin.post("/notifications/config", Map.of("paused", false)));
        assertThat(new NotificationService(db, mailConfig).configuration().paused()).isFalse();

        var events = db.queryForList("SELECT actor,action,entity_type,entity_id FROM audit_log WHERE id>? AND entity_type='NOTIFICATION_CONFIG' ORDER BY id", before);
        assertThat(events).hasSize(2);
        assertThat(events.stream().map(e -> e.get("action")).toList()).containsExactly("PAUSE_NOTIFICATIONS", "RESUME_NOTIFICATIONS");
        for (var event : events) {
            assertThat(event.get("actor")).isEqualTo("testadmin");
            assertThat(((Number) event.get("entity_id")).longValue()).isEqualTo(1L);
        }
    }

    @Test
    void rejectsMissingNonBooleanOrAdditionalFieldsWithoutMutatingSettingsOrAudit() throws Exception {
        long before = lastAuditId();
        for (String body : new String[]{"{}", "[]", "true", "null", "{\"paused\":null}",
                "{\"paused\":\"true\"}", "{\"paused\":1}", "{\"paused\":false,\"enabled\":true}",
                "{\"paused\":true,\"password\":\"should-not-be-accepted\"}"}) {
            assertStatus(admin.post("/notifications/config", mapper.readTree(body)), 400);
        }
        assertStatus(admin.post("/notifications/config", null), 400);
        assertThat(db.queryForObject("SELECT paused FROM notification_settings WHERE id=1", Boolean.class)).isFalse();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE id>? AND entity_type='NOTIFICATION_CONFIG'", Long.class, before)).isZero();
        assertThat(json(admin.get("/notifications/config")).toString()).doesNotContain("password", "should-not-be-accepted");
    }

    @Test
    void onlyAdministratorWithCsrfCanChangeTheGlobalSwitch() throws Exception {
        long institution = institution(30, 30);
        Client user = institutionUser(institution);
        String billingName = unique("mailbilling");
        success(admin.post("/users", Map.of("username", billingName, "displayName", "Cobranza correo",
                "password", PASSWORD, "role", "BILLING", "active", true, "email", "cobranza@example.test")));
        Client billing = login(billingName, PASSWORD);
        long before = lastAuditId();
        for (Client unauthorized : new Client[]{user, billing}) {
            assertStatus(unauthorized.get("/notifications/config"), 403);
            assertStatus(unauthorized.post("/notifications/config", Map.of("paused", true)), 403);
        }
        assertStatus(new Client().get("/notifications/config"), 401);
        assertStatus(admin.raw("POST", "/notifications/config", Map.of("paused", true), false), 403);
        assertThat(db.queryForObject("SELECT paused FROM notification_settings WHERE id=1", Boolean.class)).isFalse();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE id>? AND entity_type='NOTIFICATION_CONFIG'", Long.class, before)).isZero();
    }

    @Test
    void pauseSuppressesAllUnsentStatesAndPreventsManualSendingOfPastEvents() throws Exception {
        long pending = success(admin.post("/notifications/test", Map.of())).path("id").asLong();
        long failed = success(admin.post("/notifications/test", Map.of())).path("id").asLong();
        long skipped = success(admin.post("/notifications/test", Map.of())).path("id").asLong();
        long sent = success(admin.post("/notifications/test", Map.of())).path("id").asLong();
        db.update("UPDATE notification_outbox SET status='FAILED',attempts=2,last_error='Error de prueba' WHERE id=?", failed);
        db.update("UPDATE notification_outbox SET status='SKIPPED',recipient='',last_error='Sin destinatario' WHERE id=?", skipped);
        db.update("UPDATE notification_outbox SET status='SENT',attempts=1,sent_at=NOW() WHERE id=?", sent);
        JsonNode paused = success(admin.post("/notifications/config", Map.of("paused", true)));
        assertThat(paused.path("suppressedCount").asLong()).isGreaterThanOrEqualTo(3);
        for (long id : new long[]{pending, failed, skipped}) {
            assertThat(db.queryForObject("SELECT status FROM notification_outbox WHERE id=?", String.class, id)).isEqualTo("SUPPRESSED");
            assertStatus(admin.post("/notifications/" + id + "/retry", Map.of()), 409);
        }
        assertThat(db.queryForObject("SELECT attempts FROM notification_outbox WHERE id=?", Integer.class, failed)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT status FROM notification_outbox WHERE id=?", String.class, sent)).isEqualTo("SENT");
        long beforeRejectedTest = db.queryForObject("SELECT COUNT(*) FROM notification_outbox", Long.class);
        assertStatus(admin.post("/notifications/test", Map.of()), 409);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notification_outbox", Long.class)).isEqualTo(beforeRejectedTest);

        // A missing destination during the pause must also remain terminal after resume.
        var input = institutionPayload(unique("SinCorreoPausado"), 30, 30);
        input.put("email", "");
        long institution = success(admin.post("/institutions", input)).path("id").asLong();
        long caseId = supportCase(institution, "EMAIL");
        assertThat(db.queryForList("SELECT status FROM notification_outbox WHERE case_id=?", String.class, caseId)).hasSize(2).containsOnly("SUPPRESSED");
        success(admin.post("/notifications/config", Map.of("paused", false)));
        for (long id : new long[]{pending, failed, skipped})
            assertStatus(admin.post("/notifications/" + id + "/retry", Map.of()), 409);
        long suppressedMissing = db.queryForObject("SELECT id FROM notification_outbox WHERE case_id=? AND recipient_type='INSTITUTION'", Long.class, caseId);
        input.put("email", "corregido@example.test"); input.put("active", true);
        success(admin.put("/institutions/" + institution, input));
        assertStatus(admin.post("/notifications/" + suppressedMissing + "/retry", Map.of()), 409);
        assertThat(db.queryForObject("SELECT status FROM notification_outbox WHERE id=?", String.class, suppressedMissing)).isEqualTo("SUPPRESSED");
        assertThat(success(admin.post("/notifications/test", Map.of())).path("status").asText()).isEqualTo("PENDING");
    }

    private long lastAuditId() {
        return db.queryForObject("SELECT COALESCE(MAX(id),0) FROM audit_log", Long.class);
    }
}
