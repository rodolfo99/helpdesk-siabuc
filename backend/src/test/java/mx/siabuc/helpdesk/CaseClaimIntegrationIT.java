package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CaseClaimIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    @Test
    void customerOpensOwnCaseAndAdministratorClaimsItWithPublicHistoryAndNoticesOnlyOnce() throws Exception {
        long institution = institution(20, 30);
        Client customer = institutionUser(institution);
        JsonNode opened = success(customer.post("/cases", casePayload(institution, "EMAIL")));
        long caseId = opened.path("id").asLong();
        assertThat(opened.path("status").asText()).isEqualTo("OPEN");
        assertThat(opened.path("assignedAdminId").isNull()).isTrue();
        assertThat(opened.path("createdBy").asText()).isEqualTo(json(customer.get("/auth/me")).path("username").asText());
        assertThat(ids(json(admin.get("/cases")))).contains(caseId);

        JsonNode administrator = json(admin.get("/auth/me"));
        JsonNode claimed = success(admin.post("/cases/" + caseId + "/claim", null));
        assertThat(claimed.path("assignedAdminId").asLong()).isEqualTo(administrator.path("id").asLong());
        assertThat(claimed.path("assignedAdminName").asText()).isEqualTo(administrator.path("displayName").asText());
        assertThat(claimed.path("entries").size()).isEqualTo(1);
        JsonNode entry = claimed.path("entries").get(0);
        assertThat(entry.path("description").asText()).contains("Caso tomado por", administrator.path("displayName").asText(), "testadmin");
        assertThat(entry.path("internal").asBoolean()).isFalse();
        assertThat(entry.path("minutes").asLong()).isZero();
        assertThat(entry.path("channel").isNull()).isTrue();
        assertThat(json(customer.get("/cases/" + caseId)).path("entries")).isEqualTo(claimed.path("entries"));
        assertBalance(institution, 0, 20, 0, 30);
        List<Map<String, Object>> notices = claimNotices(caseId);
        assertThat(notices).hasSize(2);
        assertThat(notices.stream().map(row -> row.get("recipient")))
                .containsExactlyInAnyOrder("contacto@example.test", "testadmin@example.test");
        assertThat(notices).allSatisfy(row -> assertThat(row.get("body").toString())
                .contains("Administrativo que atiende: " + administrator.path("displayName").asText(), "Caso tomado por", "Estado: Abierto"));

        assertThat(success(admin.post("/cases/" + caseId + "/claim", null))).isEqualTo(claimed);
        assertThat(claimNotices(caseId)).isEqualTo(notices);
        assertThat(claimAuditCount(caseId)).isEqualTo(1);
        Client unrelatedCustomer = institutionUser(institution(20, 20));
        assertStatus(unrelatedCustomer.get("/cases/" + caseId), 404);
        assertThat(ids(json(unrelatedCustomer.get("/cases")))).doesNotContain(caseId);
    }

    @Test
    void onlyAdministratorsCanClaimAndRequestsRequireAuthenticationAndCsrf() throws Exception {
        long institution = institution(20, 20);
        Client customer = institutionUser(institution);
        long caseId = success(customer.post("/cases", casePayload(institution, "EMAIL"))).path("id").asLong();
        assertStatus(customer.post("/cases/" + caseId + "/claim", null), 403);
        Client billing = staff("BILLING");
        assertStatus(billing.post("/cases/" + caseId + "/claim", null), 403);
        Client anonymous = new Client();
        assertStatus(anonymous.post("/cases/" + caseId + "/claim", null), 401);
        assertStatus(admin.raw("POST", "/cases/" + caseId + "/claim", null, false), 403);
        assertThat(json(admin.get("/cases/" + caseId)).path("assignedAdminId").isNull()).isTrue();
        assertThat(claimNotices(caseId)).isEmpty();
        assertThat(claimAuditCount(caseId)).isZero();

        long otherInstitution = institution(20, 20);
        assertStatus(customer.post("/cases", casePayload(otherInstitution, "EMAIL")), 403);
        Map<String, Object> forcedAssignment = new LinkedHashMap<>(casePayload(institution, "EMAIL"));
        forcedAssignment.put("assignedAdminId", json(admin.get("/auth/me")).path("id").asLong());
        assertStatus(customer.post("/cases", forcedAssignment), 403);
    }

    @Test
    void staleClaimsCannotReplaceAnotherResponsibleOrClaimClosedOrInactiveCases() throws Exception {
        long institution = institution(20, 20);
        Client customer = institutionUser(institution);
        long claimedId = success(customer.post("/cases", casePayload(institution, "EMAIL"))).path("id").asLong();
        JsonNode claimed = success(admin.post("/cases/" + claimedId + "/claim", null));
        Client anotherAdministrator = staff("ADMIN");
        assertStatus(anotherAdministrator.post("/cases/" + claimedId + "/claim", null), 409);
        assertThat(json(admin.get("/cases/" + claimedId))).isEqualTo(claimed);
        assertThat(claimNotices(claimedId)).hasSize(2);
        assertThat(claimAuditCount(claimedId)).isEqualTo(1);

        long closedId = success(customer.post("/cases", casePayload(institution, "EMAIL"))).path("id").asLong();
        JsonNode closed = success(admin.post("/cases/" + closedId + "/status", Map.of("status", "CLOSED", "reason", "Solicitud duplicada")));
        assertStatus(admin.post("/cases/" + closedId + "/claim", null), 409);
        assertThat(json(admin.get("/cases/" + closedId))).isEqualTo(closed);
        assertThat(claimNotices(closedId)).isEmpty();
        assertThat(claimAuditCount(closedId)).isZero();
        success(admin.post("/cases/" + claimedId + "/status", Map.of("status", "CLOSED", "reason", "Atención finalizada")));
        assertStatus(admin.post("/cases/" + claimedId + "/claim", null), 409);

        long inactiveId = success(customer.post("/cases", casePayload(institution, "EMAIL"))).path("id").asLong();
        assertStatus(admin.delete("/institutions/" + institution), 204);
        assertStatus(admin.post("/cases/" + inactiveId + "/claim", null), 409);
        assertThat(json(admin.get("/cases/" + inactiveId)).path("assignedAdminId").isNull()).isTrue();
        assertThat(claimNotices(inactiveId)).isEmpty();
        assertThat(claimAuditCount(inactiveId)).isZero();
        assertStatus(admin.post("/cases/9223372036854775807/claim", null), 404);
    }

    @Test
    void customerCaseCreationKeepsConfiguredDefaultResponsibleAndRequiresChannelBalance() throws Exception {
        long adminId = json(admin.get("/auth/me")).path("id").asLong();
        Map<String, Object> data = institutionPayload("Institución con responsable " + unique("cliente"), 0, 20);
        data.put("supportAdminId", adminId);
        long institution = success(admin.post("/institutions", data)).path("id").asLong();
        Client customer = institutionUser(institution);
        assertStatus(customer.post("/cases", casePayload(institution, "EMAIL")), 409);
        JsonNode opened = success(customer.post("/cases", casePayload(institution, "REMOTE")));
        long caseId = opened.path("id").asLong();
        assertThat(opened.path("assignedAdminId").asLong()).isEqualTo(adminId);
        assertThat(success(admin.post("/cases/" + caseId + "/claim", null))).isEqualTo(opened);
        assertThat(claimAuditCount(caseId)).isZero();
        assertThat(claimNotices(caseId)).isEmpty();
        assertBalance(institution, 0, 0, 0, 20);
    }

    @Test
    void twoAdministratorsSubmittingClaimsTogetherProduceOneResponsibleAndOneHistoryEvent() throws Exception {
        long institution = institution(20, 20);
        Client customer = institutionUser(institution);
        long caseId = success(customer.post("/cases", casePayload(institution, "EMAIL"))).path("id").asLong();
        Client first = login("testadmin", "PruebaSegura123!");
        Client second = staff("ADMIN");
        long firstId = json(first.get("/auth/me")).path("id").asLong();
        long secondId = json(second.get("/auth/me")).path("id").asLong();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses;
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Client client : List.of(first, second)) {
                results.add(pool.submit(() -> {
                    client.refreshCsrf();
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("No inició concurrencia");
                    return client.raw("POST", "/cases/" + caseId + "/claim", null, true).getResponse().getStatus();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            statuses = List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        JsonNode claimed = json(customer.get("/cases/" + caseId));
        assertThat(claimed.path("assignedAdminId").asLong()).isEqualTo(statuses.get(0) == 200 ? firstId : secondId);
        assertThat(claimed.path("entries").size()).isEqualTo(1);
        assertThat(claimAuditCount(caseId)).isEqualTo(1);
        assertThat(claimNotices(caseId)).hasSize(2);
        assertBalance(institution, 0, 20, 0, 20);
    }

    private Client staff(String role) throws Exception {
        String username = unique(role.toLowerCase());
        success(admin.post("/users", Map.of("username", username, "displayName", "Personal " + role,
                "password", PASSWORD, "role", role, "active", true, "email", username + "@example.test")));
        return login(username, PASSWORD);
    }

    private List<Map<String, Object>> claimNotices(long caseId) {
        return db.queryForList("SELECT * FROM notification_outbox WHERE case_id=? AND event_type='CLAIM_CASE' ORDER BY id", caseId);
    }

    private long claimAuditCount(long caseId) {
        return db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='CLAIM_CASE' AND entity_type='CASE' AND entity_id=?", Long.class, caseId);
    }
}
