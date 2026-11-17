package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression tests for customer-safe read-only data under the authenticated institution. */
class ClientInformationIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    private long policy(long institution, String folio) throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("institutionId", institution);
        input.put("folio", folio);
        input.put("description", "Servicio de soporte de prueba");
        input.put("validFrom", "2026-01-01");
        input.put("validUntil", "2026-12-31");
        input.put("agreedAmount", 1200);
        input.put("emailMinutes", 60);
        input.put("remoteMinutes", 30);
        return success(admin.post("/policies", input)).path("policy").path("id").asLong();
    }

    private long license(long institution) throws Exception {
        long revision = success(admin.get("/license-support-defaults")).path("revision").asLong();
        success(admin.put("/license-support-defaults",
            Map.of("emailMinutes", 60, "remoteMinutes", 30, "revision", revision)));
        long updated = success(admin.get("/license-support-defaults")).path("revision").asLong();
        return success(admin.post("/licenses", Map.of(
            "institutionId", institution, "number", unique("LIC-"), "defaultsRevision", updated)))
            .path("id").asLong();
    }

    @Test void returnsOnlyOwnInstitutionWithoutFinanceOrPrivateActorDetails() throws Exception {
        long mine = institution(90, 30);
        long other = institution(90, 30);
        long mineLicense = license(mine);
        long otherLicense = license(other);
        long minePolicy = policy(mine, unique("POL-A-"));
        long otherPolicy = policy(other, unique("POL-B-"));
        Client client = institutionUser(mine);
        JsonNode info = success(client.get("/client-portal/my-information"));

        assertThat(info.path("institutionId").asLong()).isEqualTo(mine);
        assertThat(info.path("policies")).hasSize(1);
        assertThat(info.path("policies").get(0).path("id").asLong()).isEqualTo(minePolicy);
        assertThat(info.path("licenses")).hasSize(1);
        assertThat(info.path("licenses").get(0).path("id").asLong()).isEqualTo(mineLicense);
        String rendered = info.toString();
        assertThat(rendered).doesNotContain("agreedAmount", "paidAmount", "cancelReason",
            "createdBy", "passwordHash", String.valueOf(otherPolicy));
        assertThat(info.path("licenses").get(0).path("id").asLong()).isNotEqualTo(otherLicense);
    }

    @Test void deniesInternalRolesAndUnauthenticatedRequests() throws Exception {
        assertStatus(admin.get("/client-portal/my-information"), 403);
        Client guest = new Client();
        assertStatus(guest.get("/client-portal/my-information"), 401);
    }

    @Test void returnsEmptyListsWithoutInventingCommercialLicenses() throws Exception {
        long institution = institution(90, 30);
        Client client = institutionUser(institution);
        JsonNode info = success(client.get("/client-portal/my-information"));
        assertThat(info.path("policies")).isEmpty();
        assertThat(info.path("licenses")).isEmpty();
    }
}
