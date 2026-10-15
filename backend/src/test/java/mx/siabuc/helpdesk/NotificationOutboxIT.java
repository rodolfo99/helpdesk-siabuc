package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationOutboxIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    @Autowired NotificationDispatcher dispatcher;

    @Test
    void caseEventsQueueBothRecipientsWhileInternalNotesAndTheirVoidStayPrivate() throws Exception {
        long institution = institution(100, 100);
        long caseId = supportCase(institution, "EMAIL");
        assertRecipients(caseId, "CREATE_CASE", "contacto@example.test", "testadmin@example.test");
        String publicText = "Diagnóstico público " + UUID.randomUUID();
        addEntry(admin, caseId, publicText, "EMAIL", 5, false, 200);
        String privateText = "Secreto interno " + UUID.randomUUID();
        JsonNode internal = addEntry(admin, caseId, privateText, "EMAIL", 3, true, 200);
        long internalEntry = internal.path("entries").get(internal.path("entries").size() - 1).path("id").asLong();
        assertStatus(admin.post("/cases/" + caseId + "/entries/" + internalEntry + "/void", Map.of("reason", "Corrección privada")), 200);
        assertStatus(admin.put("/cases/" + caseId, Map.of("title", "Título revisado", "description", "Descripción pública revisada", "channel", "EMAIL", "assignedAdminId", json(admin.get("/auth/me")).path("id").asLong())), 200);
        assertStatus(admin.post("/cases/" + caseId + "/status", Map.of("status", "CLOSED", "reason", "Solución confirmada")), 200);
        assertRecipients(caseId, "UPDATE_CASE", "contacto@example.test", "testadmin@example.test");
        assertRecipients(caseId, "CHANGE_CASE_STATUS", "contacto@example.test", "testadmin@example.test");
        assertRecipients(caseId, "VOID_ENTRY", "testadmin@example.test");
        List<Map<String, Object>> notes = rows(caseId, "CREATE_ENTRY");
        assertThat(notes).hasSize(3);
        assertThat(notes.stream().filter(r -> r.get("body").toString().contains(privateText)).count()).isEqualTo(1);
        String assignedName = json(admin.get("/auth/me")).path("displayName").asText();
        String institutionName = findInstitution(institution).path("name").asText();
        for (Map<String, Object> row : db.queryForList("SELECT recipient,body,status FROM notification_outbox WHERE case_id=?", caseId)) {
            assertThat(row.get("status")).isEqualTo("PENDING");
            if (row.get("recipient").equals("contacto@example.test")) assertThat(row.get("body").toString()).doesNotContain(privateText);
            assertThat(row.get("body").toString())
                    .contains("Administrativo que atiende: " + assignedName,
                            "Institución cliente: " + institutionName,
                            "\n\nDescripción actual del caso:\n", "\n\nDetalle de la actualización:\n",
                            "Para mayor información, consulte la plataforma Helpdesk.")
                    .doesNotContain(PASSWORD, "PruebaSegura123!", "Estado: Activo", "Estado: Inactivo", "Estado: Desactivo");
        }
        String closed = rows(caseId, "CHANGE_CASE_STATUS").get(0).get("body").toString();
        assertThat(closed).contains("\nEstado: Cerrado\n", "Solución confirmada", "testadmin", "Caso: #" + caseId,
                "\n\nDescripción actual del caso:\nDescripción pública revisada\n\nDetalle de la actualización:\n");
    }

    @Test
    void caseNoticeIdentifiesAssignedAdministratorSeparatelyFromAuthorAndPreservesEventSnapshot() throws Exception {
        long institution = institution(100, 100);
        String username = unique("responsable");
        long assigned = success(admin.post("/users", Map.of("username", username, "displayName", "María Atención SIABUC",
                "password", PASSWORD, "role", "ADMIN", "active", true, "email", "responsable@example.test"))).path("id").asLong();
        Map<String, Object> input = new LinkedHashMap<>(casePayload(institution, "EMAIL"));
        input.put("description", "Problema original con el catálogo");
        input.put("assignedAdminId", assigned);
        long caseId = success(admin.post("/cases", input)).path("id").asLong();
        List<Map<String, Object>> initialNotices = rows(caseId, "CREATE_CASE");
        assertRecipients(caseId, "CREATE_CASE", "contacto@example.test", "responsable@example.test");
        initialNotices.forEach(row -> assertThat(row.get("body").toString())
                .contains("Administrativo que atiende: María Atención SIABUC", "\nRegistrado por: testadmin\n",
                        "\nEstado: Recibido\n", "\n\nDescripción actual del caso:\nProblema original con el catálogo\n"));

        JsonNode currentAdmin = json(admin.get("/auth/me"));
        assertStatus(admin.put("/cases/" + caseId, Map.of("title", "Catálogo revisado", "channel", "EMAIL",
                "description", "Descripción vigente después del diagnóstico", "assignedAdminId", currentAdmin.path("id").asLong())), 200);
        assertStatus(admin.post("/cases/" + caseId + "/status", Map.of("status", "CLOSED", "reason", "Índice reparado y validado")), 200);
        for (Map<String, Object> row : rows(caseId, "CHANGE_CASE_STATUS")) {
            assertThat(row.get("body").toString())
                    .contains("Administrativo que atiende: " + currentAdmin.path("displayName").asText(), "\nEstado: Cerrado\n",
                            "\n\nDescripción actual del caso:\nDescripción vigente después del diagnóstico\n\nDetalle de la actualización:\n",
                            "Índice reparado y validado", "Para mayor información, consulte la plataforma Helpdesk.")
                    .doesNotContain("Problema original con el catálogo", "María Atención SIABUC");
        }
        assertThat(rows(caseId, "CREATE_CASE")).isEqualTo(initialNotices);
        assertStatus(admin.post("/cases/" + caseId + "/status", Map.of("status", "OPEN", "reason", "Se requiere seguimiento adicional")), 200);
        List<Map<String, Object>> statusNotices = rows(caseId, "CHANGE_CASE_STATUS");
        assertThat(statusNotices).hasSize(4);
        statusNotices.subList(2, 4).forEach(row -> assertThat(row.get("body").toString())
                .contains("\nEstado: Recibido\n", "Descripción vigente después del diagnóstico", "Se requiere seguimiento adicional")
                .doesNotContain("Estado: Activo", "Estado: Inactivo", "Estado: Desactivo"));
    }

    @Test
    void inactiveAssignedAdministratorRemainsIdentifiedButDoesNotReceiveNotices() throws Exception {
        long institution = institution(100, 100);
        Map<String, Object> account = new LinkedHashMap<>(Map.of("username", unique("responsable"),
                "displayName", "José Responsable", "password", PASSWORD, "role", "ADMIN", "active", true,
                "email", "responsable-retirado@example.test"));
        long assigned = success(admin.post("/users", account)).path("id").asLong();
        Map<String, Object> input = new LinkedHashMap<>(casePayload(institution, "EMAIL"));
        input.put("assignedAdminId", assigned);
        long caseId = success(admin.post("/cases", input)).path("id").asLong();
        account.put("active", false);
        account.remove("password");
        assertStatus(admin.put("/users/" + assigned, account), 200);

        addEntry(admin, caseId, "Seguimiento público de la solicitud", "EMAIL", 2, false, 200);
        List<Map<String, Object>> notices = rows(caseId, "CREATE_ENTRY");
        assertThat(notices).hasSize(2);
        notices.forEach(row -> assertThat(row.get("body").toString()).contains("Administrativo que atiende: José Responsable"));
        assertThat(notices.stream().filter(row -> "PENDING".equals(row.get("status"))).map(row -> row.get("recipient")))
                .containsExactly("contacto@example.test");
        Map<String, Object> unavailable = notices.stream().filter(row -> "SKIPPED".equals(row.get("status"))).findFirst().orElseThrow();
        assertThat(unavailable.get("recipient")).isEqualTo("");
        assertStatus(admin.post("/notifications/" + unavailable.get("id") + "/retry", Map.of()), 409);
    }

    @Test
    void missingAddressesAreSkippedAndCanOnlyRetryAfterOriginalContactIsCorrected() throws Exception {
        Map<String, Object> input = institutionPayload("Sin correo " + UUID.randomUUID(), 50, 50);
        input.put("email", "");
        long institution = success(admin.post("/institutions", input)).path("id").asLong();
        Client user = institutionUser(institution);
        long caseId = success(user.post("/cases", casePayload(institution, "EMAIL"))).path("id").asLong();
        List<Map<String, Object>> skipped = rows(caseId, "CREATE_CASE");
        assertThat(skipped).hasSize(2);
        assertThat(skipped).allSatisfy(row -> {
            assertThat(row.get("status")).isEqualTo("SKIPPED");
            assertThat(row.get("recipient")).isEqualTo("");
            assertThat(row.get("last_error")).isNotNull();
            assertThat(row.get("body").toString()).contains("Administrativo que atiende: Sin administrativo asignado",
                    "\nEstado: Recibido\n", "Para mayor información, consulte la plataforma Helpdesk.");
        });
        long institutionNotice = db.queryForObject("SELECT id FROM notification_outbox WHERE case_id=? AND recipient_type='INSTITUTION'", Long.class, caseId);
        assertStatus(admin.post("/notifications/" + institutionNotice + "/retry", Map.of()), 409);
        input.put("email", "contacto-corregido@example.test"); input.put("active", true);
        assertStatus(admin.put("/institutions/" + institution, input), 200);
        JsonNode retried = success(admin.post("/notifications/" + institutionNotice + "/retry", Map.of()));
        assertThat(retried.path("status").asText()).isEqualTo("PENDING");
        assertThat(retried.path("recipient").asText()).isEqualTo("contacto-corregido@example.test");
    }

    @Test
    void disabledSmtpPreservesPendingAndTestEndpointUsesOnlyOwnRegisteredEmail() throws Exception {
        JsonNode config = json(admin.get("/notifications/config"));
        assertThat(config.path("enabled").asBoolean()).isFalse();
        assertThat(config.has("password")).isFalse();
        assertStatus(admin.post("/notifications/test", Map.of("recipient", "no-autorizado@example.test")), 400);
        JsonNode notice = success(admin.post("/notifications/test", Map.of()));
        assertThat(notice.path("recipient").asText()).isEqualTo("testadmin@example.test");
        long id = notice.path("id").asLong();
        dispatcher.dispatchPending();
        Map<String, Object> row = db.queryForMap("SELECT status,attempts FROM notification_outbox WHERE id=?", id);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((Number) row.get("attempts")).intValue()).isZero();
    }

    @Test
    void paymentAndVoidQueueBothRecipientsAndDuplicateOrRollbackQueueNothing() throws Exception {
        long institution = institution(20, 20);
        Map<String, Object> input = PaymentIntegrationIT.paymentPayload(institution, 15, 25);
        long payment = success(admin.post("/payments", input)).path("id").asLong();
        List<String> recipients = db.queryForList("SELECT recipient FROM notification_outbox WHERE payment_id=? AND event_type='CREATE_PAYMENT' ORDER BY recipient", String.class, payment);
        assertThat(recipients).containsExactlyInAnyOrder("contacto@example.test", "testadmin@example.test");
        assertStatus(admin.post("/payments", input), 201);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE payment_id=?", Long.class, payment)).isEqualTo(2);
        assertStatus(admin.post("/payments/" + payment + "/void", Map.of("reason", "Corrección contable")), 200);
        assertThat(db.queryForList("SELECT recipient FROM notification_outbox WHERE payment_id=? AND event_type='VOID_PAYMENT'", String.class, payment))
                .containsExactlyInAnyOrder("contacto@example.test", "testadmin@example.test");
        String body = db.queryForObject("SELECT body FROM notification_outbox WHERE payment_id=? AND event_type='CREATE_PAYMENT' LIMIT 1", String.class, payment);
        assertThat(body).contains("250.00", "MXN", "15", "25", "testadmin", "Pago: #" + payment);
        long caseId = supportCase(institution, "EMAIL");
        long before = db.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE case_id=?", Long.class, caseId);
        addEntry(admin, caseId, "No se debe enviar este evento rechazado", "EMAIL", 21, false, 409);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE case_id=?", Long.class, caseId)).isEqualTo(before);
    }

    @Test
    void identicalClientAndAdministratorAddressesAreDeduplicatedPerEvent() throws Exception {
        Map<String, Object> input = institutionPayload("Contacto compartido " + UUID.randomUUID(), 50, 50);
        input.put("email", "TESTADMIN@example.test");
        long institution = success(admin.post("/institutions", input)).path("id").asLong();
        long caseId = supportCase(institution, "EMAIL");
        assertThat(rows(caseId, "CREATE_CASE")).hasSize(1);
        addEntry(admin, caseId, "Nota privada con dirección compartida", null, 0, true, 200);
        List<Map<String, Object>> privateRows = rows(caseId, "CREATE_ENTRY");
        assertThat(privateRows).hasSize(1);
        assertThat(privateRows.get(0).get("status")).isEqualTo("SKIPPED");
        assertThat(privateRows.get(0).get("recipient")).isEqualTo("");
        long noticeId = ((Number) privateRows.get(0).get("id")).longValue();
        assertStatus(admin.post("/notifications/" + noticeId + "/retry", Map.of()), 409);
        input.put("email", "cliente-separado@example.test"); input.put("active", true);
        assertStatus(admin.put("/institutions/" + institution, input), 200);
        JsonNode retried = success(admin.post("/notifications/" + noticeId + "/retry", Map.of()));
        assertThat(retried.path("recipient").asText()).isEqualTo("testadmin@example.test");
        assertThat(retried.path("status").asText()).isEqualTo("PENDING");
    }

    private List<Map<String, Object>> rows(long caseId, String event) {
        return db.queryForList("SELECT * FROM notification_outbox WHERE case_id=? AND event_type=? ORDER BY id", caseId, event);
    }

    private void assertRecipients(long caseId, String event, String... expected) {
        assertThat(rows(caseId, event).stream().map(row -> row.get("recipient").toString()).toList()).containsExactlyInAnyOrder(expected);
    }
}
