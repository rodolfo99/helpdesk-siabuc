package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Real authenticated HTTP and stored history; no external SMTP is contacted. */
class NotificationDetailIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    @Test
    void detailShowsTheOriginalMessageAndRecipientWhileTheListStaysCompact() throws Exception {
        long institution = institution(30, 30);
        long caseId = supportCase(institution, "EMAIL");
        long id = db.queryForObject("SELECT id FROM notification_outbox WHERE case_id=? AND recipient_type='INSTITUTION'",
                Long.class, caseId);
        String originalBody = db.queryForObject("SELECT body FROM notification_outbox WHERE id=?", String.class, id);

        // Changing the case must not regenerate a historical notification when it is opened.
        success(admin.put("/cases/" + caseId, Map.of("title", "Título posterior",
                "description", "Descripción que no pertenecía al aviso original", "channel", "EMAIL")));
        JsonNode detail = success(admin.get("/notifications/" + id));
        assertThat(detail.path("id").asLong()).isEqualTo(id);
        assertThat(detail.path("caseId").asLong()).isEqualTo(caseId);
        assertThat(detail.path("paymentId").isNull()).isTrue();
        assertThat(detail.path("eventType").asText()).isEqualTo("CREATE_CASE");
        assertThat(detail.path("recipient").asText()).isEqualTo("contacto@example.test");
        assertThat(detail.path("recipientType").asText()).isEqualTo("INSTITUTION");
        assertThat(detail.path("recipientEntityId").asLong()).isEqualTo(institution);
        assertThat(detail.path("body").asText()).isEqualTo(originalBody)
                .contains("Descripción actual del caso:", "Estado: Abierto")
                .doesNotContain("Descripción que no pertenecía al aviso original");
        assertThat(detail.path("internal").asBoolean()).isFalse();
        assertThat(detail.path("createdAt").asText()).isNotBlank();
        assertThat(detail.has("sentAt")).isTrue();
        assertThat(detail.path("sentAt").isNull()).isTrue();
        assertThat(detail.has("lastError")).isTrue();
        assertThat(detail.size()).isEqualTo(15);
        assertThat(detail.toString()).doesNotContain("claimToken", "eventKey", "claimedUntil", "password");

        addEntry(admin, caseId, "Análisis interno completo\nSegunda línea <script>no ejecutar</script>", null, 0, true, 200);
        long internalId = db.queryForObject("SELECT id FROM notification_outbox WHERE case_id=? AND internal", Long.class, caseId);
        JsonNode internal = success(admin.get("/notifications/" + internalId));
        assertThat(internal.path("internal").asBoolean()).isTrue();
        assertThat(internal.path("recipientType").asText()).isEqualTo("USER");
        assertThat(internal.path("body").asText()).contains("Análisis interno completo\nSegunda línea <script>no ejecutar</script>");

        for (JsonNode listed : success(admin.get("/notifications"))) {
            assertThat(listed.has("body")).isFalse();
            assertThat(listed.has("recipientEntityId")).isFalse();
        }
    }

    @Test
    void openingAnyDeliveryStateIncludingSuppressedIsStrictlyReadOnly() throws Exception {
        List<Long> notificationIds = new ArrayList<>();
        for (String state : List.of("PENDING", "SENDING", "SENT", "FAILED", "SKIPPED", "SUPPRESSED")) {
            long id = success(admin.post("/notifications/test", Map.of())).path("id").asLong();
            db.update("""
                UPDATE notification_outbox SET status=?,attempts=2,last_error=?,
                    claim_token=?,claimed_until=CASE WHEN ?='SENDING' THEN NOW()+INTERVAL '5 minutes' ELSE NULL END,
                    sent_at=CASE WHEN ?='SENT' THEN NOW() ELSE NULL END WHERE id=?
                """, state, "Detalle conservado de " + state, "SENDING".equals(state) ? UUID.randomUUID() : null,
                    state, state, id);
            notificationIds.add(id);
        }
        var before = db.queryForList("SELECT * FROM notification_outbox ORDER BY id");
        long auditCount = db.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class);
        for (long id : notificationIds) {
            var saved = db.queryForMap("SELECT * FROM notification_outbox WHERE id=?", id);
            JsonNode detail = success(admin.get("/notifications/" + id));
            assertThat(detail.path("status").asText()).isEqualTo(saved.get("status"));
            assertThat(detail.path("attempts").asInt()).isEqualTo(2);
            assertThat(detail.path("lastError").asText()).isEqualTo(saved.get("last_error"));
            assertThat(detail.path("body").asText()).isEqualTo(saved.get("body"));
            assertThat(detail.path("sentAt").isNull()).isEqualTo(saved.get("sent_at") == null);
        }
        assertStatus(admin.get("/notifications/9223372036854775807"), 404);
        assertThat(db.queryForList("SELECT * FROM notification_outbox ORDER BY id")).isEqualTo(before);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class)).isEqualTo(auditCount);
    }

    @Test
    void onlyAdministratorsCanReadEvenInternalNotificationDetails() throws Exception {
        long institution = institution(30, 30);
        long caseId = supportCase(institution, "EMAIL");
        addEntry(admin, caseId, "Información privada para administración", null, 0, true, 200);
        long id = db.queryForObject("SELECT id FROM notification_outbox WHERE case_id=? AND internal", Long.class, caseId);
        Client user = institutionUser(institution);
        String billingName = unique("detailbilling");
        success(admin.post("/users", Map.of("username", billingName, "displayName", "Cobranza detalle",
                "password", PASSWORD, "role", "BILLING", "active", true, "email", "cobranza@example.test")));
        Client billing = login(billingName, PASSWORD);
        for (Client unauthorized : new Client[]{user, billing}) {
            for (long deniedId : new long[]{id, Long.MAX_VALUE}) {
                var response = unauthorized.get("/notifications/" + deniedId);
                assertStatus(response, 403);
                assertThat(response.getResponse().getContentAsString()).doesNotContain("Información privada", "body", "recipient");
            }
        }
        assertStatus(new Client().get("/notifications/" + id), 401);
    }
}
