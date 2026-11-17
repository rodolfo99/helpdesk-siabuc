package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Client password recovery is strictly administrative, never public/self-service. */
class ClientAccessIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    @Test void failedClientPasswordsLockButStaffLoginsRemainSeparate() throws Exception {
        long institutionId=institution(60,60);
        String username=unique("bloqueo");
        JsonNode created=createUser(username,institutionId);
        long userId=created.path("id").asLong();
        ClientLoginService.Settings settings=setting();
        success(admin.put("/auth/client-login-settings",
            Map.of("maxAttempts",3,"lockMinutes",15,"revision",settings.revision())));
        try {
            for(int i=0;i<3;i++){
                Client attacker=new Client();
                assertStatus(attacker.authenticate(username,"contrasena-incorrecta","client"),401);
            }
            long activeLocks=db.queryForObject("""
                SELECT COUNT(*) FROM app_users WHERE id=? AND login_locked_until>NOW()
                """,Long.class,userId);
            assertThat(activeLocks).isEqualTo(1L);
            Client blocked=new Client();
            assertStatus(blocked.authenticate(username,PASSWORD,"client"),401);

            Client staff=new Client();
            assertStatus(staff.authenticate("testadmin","PruebaSegura123!","staff"),200);

            assertStatus(admin.post("/auth/client-login-locks/"+userId+"/unlock",Map.of()),204);
            Client allowed=new Client();
            assertStatus(allowed.authenticate(username,PASSWORD,"client"),200);
            assertThat(db.queryForObject("""
                SELECT failed_login_attempts FROM app_users WHERE id=?
                """,Integer.class,userId)).isZero();
        } finally {
            ClientLoginService.Settings current=setting();
            success(admin.put("/auth/client-login-settings",
                Map.of("maxAttempts",settings.maxAttempts(),"lockMinutes",settings.lockMinutes(),
                    "revision",current.revision())));
        }
    }

    @Test void settingsAreValidatedAndOnlyAdminCanChangeThem() throws Exception {
        Client client=institutionUser(institution(60,60));
        assertStatus(client.get("/auth/client-login-settings"),403);
        assertStatus(client.get("/auth/client-login-locks"),403);
        assertStatus(client.put("/auth/client-login-settings",
            Map.of("maxAttempts",3,"lockMinutes",15,"revision",1)),403);
        ClientLoginService.Settings settings=setting();
        assertStatus(admin.put("/auth/client-login-settings",
            Map.of("maxAttempts",1,"lockMinutes",15,"revision",settings.revision())),400);
        assertStatus(admin.put("/auth/client-login-settings",
            Map.of("maxAttempts",5,"lockMinutes",0,"revision",settings.revision())),400);
        assertStatus(admin.put("/auth/client-login-settings",
            Map.of("maxAttempts",5,"lockMinutes",15,"revision",settings.revision()+500)),409);
    }

    @Test void noPublicPasswordRecoveryEndpointsAreAvailable() throws Exception {
        Client guest=new Client();
        // For anonymous callers Spring Security can return 401 (entry point) or 403.
        assertThat(guest.post("/auth/recovery/request",
            Map.of("username","cualquiera","email","persona@example.test")).getResponse().getStatus()).isIn(401,403);
        assertThat(guest.post("/auth/recovery/reset",
            Map.of("username","cualquiera","code","codigo","newPassword","NuevaClaveSegura123!")).getResponse().getStatus()).isIn(401,403);
        assertStatus(admin.post("/auth/recovery/request",
            Map.of("username","cualquiera","email","persona@example.test")),403);
    }

    @Test void onlyAdminCanResetPasswordUsingUserForm() throws Exception {
        long institutionId=institution(80,80);
        String username=unique("admin-reset");
        JsonNode created=createUser(username,institutionId);
        long id=created.path("id").asLong();
        Client user=login(username,PASSWORD);
        var changed=new LinkedHashMap<String,Object>(userPayload(username,institutionId,true));
        changed.put("password","ClaveNuevaSegura123!");
        changed.put("email","contacto@example.test");

        assertStatus(user.put("/users/"+id,changed),403);
        JsonNode updated=success(admin.put("/users/"+id,changed));
        assertThat(updated.path("email").asText()).isEqualTo("contacto@example.test");
        assertStatus(user.get("/auth/me"),401);

        Client oldPassword=new Client();
        assertStatus(oldPassword.authenticate(username,PASSWORD,"client"),401);
        Client newPassword=new Client();
        assertStatus(newPassword.authenticate(username,"ClaveNuevaSegura123!","client"),200);
    }

    @Test void adminCanUpdateClientContactEmailWithoutChangingInstitution() throws Exception {
        long institutionId=institution(30,30);
        String username=unique("correo");
        JsonNode created=createUser(username,institutionId);
        var body=new LinkedHashMap<String,Object>(userPayload(username,institutionId,true));
        body.put("email","nuevo-correo@example.test");
        body.put("password","");
        JsonNode changed=success(admin.put("/users/"+created.path("id").asLong(),body));
        assertThat(changed.path("email").asText()).isEqualTo("nuevo-correo@example.test");
        assertThat(changed.path("institutionId").asLong()).isEqualTo(institutionId);
    }

    private ClientLoginService.Settings setting() throws Exception {
        JsonNode s=success(admin.get("/auth/client-login-settings"));
        return new ClientLoginService.Settings(s.path("maxAttempts").asInt(),
            s.path("lockMinutes").asInt(),s.path("revision").asLong());
    }
}
