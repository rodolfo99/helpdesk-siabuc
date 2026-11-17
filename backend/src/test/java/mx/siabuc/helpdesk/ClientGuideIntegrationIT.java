package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** First-visit tutorial state is kept on the server, isolated by account and role. */
class ClientGuideIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    @Test void completionPersistsAcrossSessionsAndIsIdempotent() throws Exception {
        long institutionId=institution(120,60);
        String username=unique("guia");
        JsonNode account=createUser(username,institutionId);
        long userId=account.path("id").asLong();

        Client first=login(username,PASSWORD);
        JsonNode before=success(first.get("/client-portal/guide"));
        assertThat(before.path("completed").asBoolean()).isFalse();
        assertThat(before.path("completedAt").isNull()).isTrue();

        JsonNode saved=success(first.post("/client-portal/guide/complete",java.util.Map.of()));
        assertThat(saved.path("completed").asBoolean()).isTrue();
        assertThat(saved.path("completedAt").asText()).isNotBlank();

        JsonNode retry=success(first.post("/client-portal/guide/complete",java.util.Map.of()));
        assertThat(retry.path("completedAt")).isEqualTo(saved.path("completedAt"));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM client_guide_completed WHERE user_id=?",Long.class,userId)).isEqualTo(1L);

        Client second=login(username,PASSWORD);
        assertThat(success(second.get("/client-portal/guide")).path("completed").asBoolean()).isTrue();
    }

    @Test void completionIsNotSharedWithOtherUsersOrInstitutions() throws Exception {
        long institutionId=institution(120,60);
        String firstName=unique("primero"),secondName=unique("segundo");
        createUser(firstName,institutionId);
        createUser(secondName,institutionId);
        Client first=login(firstName,PASSWORD);
        Client second=login(secondName,PASSWORD);
        success(first.post("/client-portal/guide/complete",java.util.Map.of()));
        assertThat(success(second.get("/client-portal/guide")).path("completed").asBoolean()).isFalse();

        Client other=institutionUser(institution(120,60));
        assertThat(success(other.get("/client-portal/guide")).path("completed").asBoolean()).isFalse();
    }

    @Test void onlyClientsMayReadOrChangeGuideState() throws Exception {
        assertStatus(admin.get("/client-portal/guide"),403);
        assertStatus(admin.post("/client-portal/guide/complete",java.util.Map.of()),403);
        Client guest=new Client();
        assertStatus(guest.get("/client-portal/guide"),401);
        assertStatus(guest.post("/client-portal/guide/complete",java.util.Map.of()),401);
    }
}
