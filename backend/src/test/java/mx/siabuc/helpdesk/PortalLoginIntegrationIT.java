package mx.siabuc.helpdesk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Ensures the two public sign-in screens do not accept the wrong account type. */
class PortalLoginIntegrationIT extends ApiIntegrationSupport {

    @Test
    void clientPortalAcceptsOnlyInstitutionUsers() throws Exception {
        long institutionId = institution(60, 60);
        String username = unique("cliente");
        createUser(username, institutionId);

        Client client = new Client();
        assertStatus(client.authenticate(username, PASSWORD, "client"), 200);
        assertThat(json(client.get("/auth/me")).path("role").asText()).isEqualTo("USER");

        Client staff = new Client();
        assertStatus(staff.authenticate("testadmin", "PruebaSegura123!", "client"), 403);
        assertStatus(staff.get("/auth/me"), 401);
    }

    @Test
    void staffPortalAcceptsOnlyInternalRoles() throws Exception {
        Client staff = new Client();
        assertStatus(staff.authenticate("testadmin", "PruebaSegura123!", "staff"), 200);
        assertThat(json(staff.get("/auth/me")).path("role").asText()).isEqualTo("ADMIN");

        long institutionId = institution(60, 60);
        String username = unique("cliente");
        createUser(username, institutionId);
        Client client = new Client();
        assertStatus(client.authenticate(username, PASSWORD, "staff"), 403);
        assertStatus(client.get("/auth/me"), 401);
    }

    @Test
    void rejectsUnknownPortalWithoutPersistingSession() throws Exception {
        Client client = new Client();
        assertStatus(client.authenticate("testadmin", "PruebaSegura123!", "unknown"), 403);
        assertStatus(client.get("/auth/me"), 401);
    }

    @Test
    void legacyLoginRemainsAvailableForExistingClients() throws Exception {
        Client staff = new Client();
        assertStatus(staff.authenticate("testadmin", "PruebaSegura123!"), 200);
        assertThat(json(staff.get("/auth/me")).path("role").asText()).isEqualTo("ADMIN");
    }
}
