package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Integration tests against a real PostgreSQL database. No mocked services,
 * repositories or security: requests use the same session + CSRF handshake as
 * the Angular application. Run with: mvn -Ppostgres-it verify and DB_* variables.
 * Use a dedicated database. Each test creates uniquely named records and keeps
 * them so that audit/history preservation is also exercised.
 */
class HelpdeskIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    @Test
    void requiresAuthenticationAndRealCsrfAndLogoutEndsSession() throws Exception {
        Client anonymous = new Client();
        assertStatus(anonymous.get("/auth/me"), 401);
        assertStatus(admin.raw("POST", "/institutions", institutionPayload("No CSRF", 10, 10), false), 403);

        Client invalid = new Client();
        assertStatus(invalid.authenticate("testadmin", "incorrecta123!"), 401);
        assertStatus(admin.post("/auth/logout", null), 204);
        assertStatus(admin.get("/auth/me"), 401);
    }

    @Test
    void userCanOnlyReadOwnInstitutionCasesAndPublicEntries() throws Exception {
        long own = institution(90, 60);
        long foreign = institution(90, 60);
        Client user = institutionUser(own);
        long ownCase = supportCase(own, "EMAIL");
        long foreignCase = supportCase(foreign, "REMOTE");
        String privateText = "Nota interna " + UUID.randomUUID();
        addEntry(admin, ownCase, "Atención pública", "EMAIL", 5, false, 200);
        addEntry(admin, ownCase, privateText, null, 0, true, 200);

        JsonNode institutions = json(user.get("/institutions"));
        assertThat(institutions.size()).isEqualTo(1);
        assertThat(institutions.get(0).path("id").asLong()).isEqualTo(own);
        assertThat(institutions.get(0).path("notes").asText()).isEmpty();
        JsonNode cases = json(user.get("/cases"));
        assertThat(ids(cases)).contains(ownCase).doesNotContain(foreignCase);
        assertThat(json(user.get("/cases/" + ownCase)).path("entries").size()).isEqualTo(1);
        assertThat(user.get("/cases/" + ownCase).getResponse().getContentAsString()).doesNotContain(privateText);
        assertThat(json(admin.get("/cases/" + ownCase)).path("entries").size()).isEqualTo(2);
        assertThat(user.get("/cases/" + foreignCase).getResponse().getStatus()).isIn(403, 404);
        assertStatus(user.get("/audit"), 403);
        assertStatus(user.get("/users"), 403);
        assertStatus(user.post("/institutions", institutionPayload("Prohibida", 10, 10)), 403);
        assertStatus(user.post("/cases", casePayload(foreign, "EMAIL")), 403);
        assertStatus(user.post("/cases/" + ownCase + "/status", Map.of("status", "CLOSED", "reason", "Intento sin permiso")), 403);
        addEntry(user, ownCase, "Cobro no autorizado", "EMAIL", 1, false, 403);
        addEntry(user, ownCase, "Nota interna no autorizada", null, 0, true, 403);
        addEntry(user, ownCase, "Respuesta del usuario", null, 0, false, 200);
        assertBalance(own, 5, 85, 0, 60);
    }

    @Test
    void emailAndRemoteHaveIndependentBalancesAndInsufficientChargeIsAtomic() throws Exception {
        long institution = institution(60, 40);
        long emailCase = supportCase(institution, "EMAIL");
        long remoteCase = supportCase(institution, "REMOTE");
        addEntry(admin, emailCase, "Correo de diagnóstico", "EMAIL", 10, false, 200);
        addEntry(admin, remoteCase, "Sesión remota", "REMOTE", 15, false, 200);
        assertBalance(institution, 10, 50, 15, 25);

        addEntry(admin, emailCase, "Exceso correo", "EMAIL", 51, false, 409);
        addEntry(admin, remoteCase, "Exceso remoto", "REMOTE", 26, false, 409);
        assertBalance(institution, 10, 50, 15, 25);
        assertThat(json(admin.get("/cases/" + emailCase)).path("entries").size()).isEqualTo(1);
        assertThat(json(admin.get("/cases/" + remoteCase)).path("entries").size()).isEqualTo(1);
        assertThat(json(admin.get("/cases/" + emailCase)).path("totalMinutes").asInt()).isEqualTo(10);
    }

    @Test
    void refusesNewCasesOnEmptyChannelButAllowsOtherChannel() throws Exception {
        long institution = institution(0, 20);
        Client user = institutionUser(institution);
        assertStatus(admin.post("/cases", casePayload(institution, "EMAIL")), 409);
        assertStatus(user.post("/cases", casePayload(institution, "EMAIL")), 409);
        assertThat(success(user.post("/cases", casePayload(institution, "REMOTE"))).path("channel").asText())
                .isEqualTo("REMOTE");
        assertBalance(institution, 0, 0, 0, 20);
    }

    @Test
    void concurrentChargesCannotOverdrawTheLastMinutes() throws Exception {
        long institution = institution(10, 30);
        long firstCase = supportCase(institution, "EMAIL");
        long secondCase = supportCase(institution, "EMAIL");
        Client first = login("testadmin", "PruebaSegura123!");
        Client second = login("testadmin", "PruebaSegura123!");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            List<Client> clients = List.of(first, second);
            List<Long> caseIds = List.of(firstCase, secondCase);
            for (int i = 0; i < clients.size(); i++) {
                Client client = clients.get(i);
                long caseId = caseIds.get(i);
                results.add(pool.submit(() -> {
                    client.refreshCsrf();
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("No inició concurrencia");
                    return client.raw("POST", "/cases/" + caseId + "/entries",
                            entryPayload("Cargo concurrente", "EMAIL", 8, false), true)
                            .getResponse().getStatus();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertBalance(institution, 8, 2, 0, 30);
        int firstEntries = json(admin.get("/cases/" + firstCase)).path("entries").size();
        int secondEntries = json(admin.get("/cases/" + secondCase)).path("entries").size();
        assertThat(firstEntries + secondEntries).isEqualTo(1);
    }

    @Test
    void voidPreservesHistoryAndRefundsExactlyOnce() throws Exception {
        long institution = institution(50, 25);
        long caseId = supportCase(institution, "EMAIL");
        JsonNode charged = addEntry(admin, caseId, "Consumo a corregir", "EMAIL", 17, false, 200);
        long entryId = charged.path("entries").get(0).path("id").asLong();
        String path = "/cases/" + caseId + "/entries/" + entryId + "/void";
        JsonNode corrected = success(admin.post(path, Map.of("reason", "Tiempo duplicado por error")));
        assertBalance(institution, 0, 50, 0, 25);
        assertThat(corrected.path("entries").size()).isEqualTo(1);
        assertThat(corrected.path("entries").get(0).path("voided").asBoolean()).isTrue();
        assertThat(corrected.path("entries").get(0).path("voidReason").asText()).isEqualTo("Tiempo duplicado por error");
        assertThat(corrected.path("totalMinutes").asInt()).isZero();

        assertStatus(admin.post(path, Map.of("reason", "Segundo intento")), 409);
        assertBalance(institution, 0, 50, 0, 25);
        assertThat(json(admin.get("/cases/" + caseId)).path("entries").size()).isEqualTo(1);
    }

    @Test
    void closedCasesRejectEntriesAndReopeningRequiresCredit() throws Exception {
        long institution = institution(10, 20);
        long caseId = supportCase(institution, "EMAIL");
        addEntry(admin, caseId, "Consume saldo", "EMAIL", 10, false, 200);
        addEntry(admin, caseId, "Comentario sin consumo aun sin saldo", null, 0, false, 200);
        assertStatus(admin.post("/cases/" + caseId + "/status", Map.of("status", "CLOSED", "reason", "Resuelto")), 200);
        addEntry(admin, caseId, "Comentario tardío", null, 0, false, 409);
        assertStatus(admin.post("/cases/" + caseId + "/status", Map.of("status", "OPEN", "reason", "Nuevo seguimiento")), 409);
        assertThat(json(admin.get("/cases/" + caseId)).path("status").asText()).isEqualTo("CLOSED");
        assertBalance(institution, 10, 0, 0, 20);

        Map<String, Object> update = institutionPayload("Recarga " + UUID.randomUUID(), 30, 20);
        update.put("active", true);
        assertStatus(admin.put("/institutions/" + institution, update), 200);
        assertStatus(admin.post("/cases/" + caseId + "/status", Map.of("status", "OPEN", "reason", "Saldo recargado")), 200);
        addEntry(admin, caseId, "Seguimiento tras recarga", "EMAIL", 4, false, 200);
        assertBalance(institution, 14, 16, 0, 20);
    }

    @Test
    void allocationCannotBeReducedBelowUsageAndMinutesMustBeValid() throws Exception {
        long institution = institution(50, 30);
        long caseId = supportCase(institution, "EMAIL");
        addEntry(admin, caseId, "Trabajo registrado", "EMAIL", 20, false, 200);
        Map<String, Object> invalid = institutionPayload("Edición inválida " + UUID.randomUUID(), 19, 30);
        invalid.put("active", true);
        assertStatus(admin.put("/institutions/" + institution, invalid), 409);
        assertBalance(institution, 20, 30, 0, 30);
        addEntry(admin, caseId, "Negativo", "EMAIL", -1, false, 400);
        addEntry(admin, caseId, "Sin canal", null, 1, false, 400);
        Map<String, Object> fractional = entryPayload("Fracción inválida", "EMAIL", 1, false);
        fractional.put("minutes", 1.5);
        assertStatus(admin.post("/cases/" + caseId + "/entries", fractional), 400);
        fractional.put("minutes", 1_000_000_001L);
        assertStatus(admin.post("/cases/" + caseId + "/entries", fractional), 400);
        assertStatus(admin.post("/institutions", institutionPayload("Negativa", -1, 0)), 400);
        assertBalance(institution, 20, 30, 0, 30);
    }

    @Test
    void institutionDeletionPreservesHistoryAndBlocksAnExistingUserSession() throws Exception {
        long institution = institution(40, 40);
        long caseId = supportCase(institution, "EMAIL");
        addEntry(admin, caseId, "Histórico antes de baja", "EMAIL", 3, false, 200);
        Client user = institutionUser(institution);
        assertStatus(user.get("/cases"), 200);
        MvcResult deleted = admin.delete("/institutions/" + institution);
        assertThat(deleted.getResponse().getStatus()).isIn(200, 204);
        assertThat(user.get("/cases").getResponse().getStatus()).isIn(401, 403);
        assertThat(findInstitution(institution).path("active").asBoolean()).isFalse();
        JsonNode history = json(admin.get("/cases/" + caseId));
        assertThat(history.path("entries").size()).isEqualTo(1);
        assertThat(history.path("totalMinutes").asInt()).isEqualTo(3);
        assertBalance(institution, 3, 37, 0, 40);
    }

    @Test
    void disablingUserImmediatelyInvalidatesAccessAndCannotMoveInstitutions() throws Exception {
        long institution = institution(30, 30);
        long other = institution(30, 30);
        String username = unique("usuario");
        JsonNode created = createUser(username, institution);
        Client user = login(username, PASSWORD);
        long id = created.path("id").asLong();
        Map<String, Object> update = userPayload(username, other, true);
        update.put("password", "");
        assertStatus(admin.put("/users/" + id, update), 409);
        update.put("institutionId", institution);
        update.put("active", false);
        assertStatus(admin.put("/users/" + id, update), 200);
        assertThat(user.get("/cases").getResponse().getStatus()).isIn(401, 403);
        assertThat(new Client().authenticate(username, PASSWORD).getResponse().getStatus()).isIn(401, 403);
    }

    @Test
    @Transactional // Roll back temporary account state, never modify an operational database.
    void lastAdministratorCannotBeDeactivated() throws Exception {
        JsonNode current = json(admin.get("/auth/me"));
        // Other integration tests intentionally leave additional administrators in their
        // disposable database. Isolate this test's last-admin scenario transactionally.
        db.update("UPDATE app_users SET active=FALSE WHERE role='ADMIN' AND active AND id<>?", current.path("id").asLong());
        int activeAdmins = 0;
        for (JsonNode user : json(admin.get("/users"))) {
            if (user.path("role").asText().equals("ADMIN") && user.path("active").asBoolean()) activeAdmins++;
        }
        assertThat(activeAdmins).as("Ejecute la suite en una base exclusiva de pruebas, con su administrador inicial").isEqualTo(1);
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("username", current.path("username").asText());
        update.put("displayName", "Administrador de pruebas");
        update.put("password", "");
        update.put("role", "ADMIN");
        update.put("institutionId", null);
        update.put("active", false);
        assertStatus(admin.put("/users/" + current.path("id").asLong(), update), 409);
        assertThat(json(admin.get("/auth/me")).path("active").asBoolean()).isTrue();
        assertStatus(login("testadmin", "PruebaSegura123!").get("/users"), 200);
    }

    @Test
    void voidCannotRefundAnEntryThroughAnotherCase() throws Exception {
        long firstInstitution = institution(40, 40);
        long secondInstitution = institution(50, 50);
        long firstCase = supportCase(firstInstitution, "EMAIL");
        long secondCase = supportCase(secondInstitution, "EMAIL");
        addEntry(admin, firstCase, "Atención primera institución", "EMAIL", 7, false, 200);
        JsonNode second = addEntry(admin, secondCase, "Atención segunda institución", "EMAIL", 13, false, 200);
        long secondEntry = second.path("entries").get(0).path("id").asLong();
        assertStatus(admin.post("/cases/" + firstCase + "/entries/" + secondEntry + "/void", Map.of("reason", "Identificador cruzado")), 404);
        assertBalance(firstInstitution, 7, 33, 0, 40);
        assertBalance(secondInstitution, 13, 37, 0, 50);
        assertThat(json(admin.get("/cases/" + secondCase)).path("entries").get(0).path("voided").asBoolean()).isFalse();
    }

    @Test
    void passwordChangeChecksCurrentPasswordAndNewLoginUsesUpdatedPassword() throws Exception {
        long institution = institution(30, 30);
        String username = unique("clave");
        createUser(username, institution);
        Client user = login(username, PASSWORD);
        Client otherSession = login(username, PASSWORD);
        String newPassword = "NuevaClaveSegura456!";
        assertThat(user.put("/auth/password", Map.of("currentPassword", "equivocada123!", "newPassword", newPassword))
                .getResponse().getStatus()).isIn(400, 403);
        assertStatus(user.put("/auth/password", Map.of("currentPassword", PASSWORD, "newPassword", "corta")), 400);
        assertStatus(user.put("/auth/password", Map.of("currentPassword", PASSWORD, "newPassword", "é".repeat(40))), 400);
        assertThat(user.put("/auth/password", Map.of("currentPassword", PASSWORD, "newPassword", newPassword))
                .getResponse().getStatus()).isIn(200, 204);
        assertStatus(user.get("/auth/me"), 200);
        assertStatus(otherSession.get("/auth/me"), 401);
        assertStatus(new Client().authenticate(username, PASSWORD), 401);
        assertStatus(login(username, newPassword).get("/auth/me"), 200);
    }

    @Test
    void administrativeCaseEditsAreAuditedWithoutErasingServiceHistory() throws Exception {
        long institution = institution(60, 60);
        long caseId = supportCase(institution, "EMAIL");
        Client user = institutionUser(institution);
        addEntry(admin, caseId, "Diagnóstico conservado", "EMAIL", 11, false, 200);
        String revisedTitle = "Título corregido " + UUID.randomUUID();
        Map<String, Object> update = Map.of("title", revisedTitle, "description", "Descripción ampliada", "channel", "REMOTE");
        assertStatus(user.put("/cases/" + caseId, update), 403);
        assertStatus(admin.put("/cases/" + caseId, update), 200);
        JsonNode detail = json(admin.get("/cases/" + caseId));
        assertThat(detail.path("title").asText()).isEqualTo(revisedTitle);
        assertThat(detail.path("entries").size()).isEqualTo(2);
        assertThat(detail.path("entries").get(0).path("description").asText()).isEqualTo("Diagnóstico conservado");
        assertThat(detail.path("entries").get(1).path("minutes").asInt()).isZero();
        assertThat(detail.path("entries").get(1).path("description").asText()).contains(revisedTitle);
        assertBalance(institution, 11, 49, 0, 60);
        JsonNode audit = json(admin.get("/audit"));
        assertThat(audit.isArray()).isTrue();
        assertThat(audit.size()).isGreaterThan(0);
        boolean found = false;
        for (JsonNode event : audit) {
            if (event.path("entityId").asLong() == caseId && event.path("entityType").asText().equals("CASE")
                    && event.path("action").asText().toUpperCase().contains("UPDATE")
                    && event.path("actor").asText().equals("testadmin")) found = true;
        }
        assertThat(found).as("La modificación del caso debe dejar auditoría").isTrue();
        assertThat(audit.toString()).doesNotContain(PASSWORD, "PruebaSegura123!");
        assertStatus(user.get("/audit"), 403);
    }

}
