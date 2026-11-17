package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Unread replies are customer-specific and never reveal internal notes. */
class ClientUnreadIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    private long lastEntry(long caseId) {
        return db.queryForObject("SELECT MAX(id) FROM case_entries WHERE case_id=?", Long.class, caseId);
    }

    private long unread(Client client, long caseId) throws Exception {
        JsonNode rows = success(client.get("/client-portal/tickets/unread"));
        for (JsonNode item : rows)
            if (item.path("caseId").asLong() == caseId)
                return item.path("unreadCount").asLong();
        return 0;
    }

    @Test void unreadCountsOnlyVisibleStaffEntriesAndPersistsPerCustomer() throws Exception {
        long institution = institution(200, 120);
        long caseId = supportCase(institution, "EMAIL");
        String firstName = unique("lector");
        String secondName = unique("lector");
        createUser(firstName, institution);
        createUser(secondName, institution);
        Client first = login(firstName, PASSWORD), second = login(secondName, PASSWORD);

        assertThat(unread(first, caseId)).isZero();
        addEntry(admin, caseId, "Nota interna no visible", null, 0, true, 200);
        assertThat(unread(first, caseId)).isZero();

        addEntry(first, caseId, "Comentario del propio cliente", null, 0, false, 200);
        assertThat(unread(first, caseId)).isZero();

        addEntry(admin, caseId, "Primera respuesta de soporte", null, 0, false, 200);
        long firstPublicId = lastEntry(caseId);
        assertThat(unread(first, caseId)).isEqualTo(1);
        assertThat(unread(second, caseId)).isEqualTo(1);

        assertStatus(first.post("/client-portal/tickets/" + caseId + "/read",
            Map.of("lastVisibleEntryId", firstPublicId)), 204);
        assertThat(unread(first, caseId)).isZero();
        assertThat(unread(second, caseId)).isEqualTo(1);

        addEntry(admin, caseId, "Nueva respuesta posterior", null, 0, false, 200);
        assertThat(unread(first, caseId)).isEqualTo(1);
        assertThat(unread(second, caseId)).isEqualTo(2);

        // A delayed client cannot regress the high-water mark or hide a newer response.
        assertStatus(first.post("/client-portal/tickets/" + caseId + "/read",
            Map.of("lastVisibleEntryId", firstPublicId)), 204);
        assertThat(unread(first, caseId)).isEqualTo(1);

        Client freshSession = login(firstName, PASSWORD);
        assertThat(unread(freshSession, caseId)).isEqualTo(1);
    }

    @Test void markReadRejectsForeignCaseAndNonPublicEntryIds() throws Exception {
        long mine = institution(120, 40), foreign = institution(120, 40);
        Client client = institutionUser(mine);
        long ownCase = supportCase(mine, "EMAIL"), foreignCase = supportCase(foreign, "EMAIL");

        addEntry(admin, ownCase, "Mensaje privado del personal", null, 0, true, 200);
        long privateId = lastEntry(ownCase);
        addEntry(admin, foreignCase, "Atención de la otra institución", null, 0, false, 200);
        long foreignId = lastEntry(foreignCase);

        assertStatus(client.post("/client-portal/tickets/" + foreignCase + "/read",
            Map.of("lastVisibleEntryId", foreignId)), 404);
        assertStatus(client.post("/client-portal/tickets/" + ownCase + "/read",
            Map.of("lastVisibleEntryId", privateId)), 400);
        assertStatus(client.post("/client-portal/tickets/" + ownCase + "/read",
            Map.of("lastVisibleEntryId", foreignId)), 400);

        assertStatus(client.post("/client-portal/tickets/" + ownCase + "/read",
            Map.of("lastVisibleEntryId", 0)), 204);
        assertThat(unread(client, ownCase)).isZero();
        assertThat(unread(client, foreignCase)).isZero();
    }

    @Test void staffAndAnonymousUsersCannotReadOrModifyUnreadMarkers() throws Exception {
        long institution = institution(120, 40);
        long caseId = supportCase(institution, "EMAIL");
        assertStatus(admin.get("/client-portal/tickets/unread"), 403);
        assertStatus(admin.post("/client-portal/tickets/" + caseId + "/read",
            Map.of("lastVisibleEntryId", 0)), 403);
        Client guest = new Client();
        assertStatus(guest.get("/client-portal/tickets/unread"), 401);
        assertStatus(guest.post("/client-portal/tickets/" + caseId + "/read",
            Map.of("lastVisibleEntryId", 0)), 401);
    }
}
