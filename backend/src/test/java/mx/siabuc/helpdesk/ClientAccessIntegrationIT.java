package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAccessIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    @Test void failedClientPasswordsLockButStaffLoginsRemainSeparate() throws Exception {
        long institutionId=institution(60,60);
        String username=unique("bloqueo");
        JsonNode created=createUser(username,institutionId);
        long userId=created.path("id").asLong();
        ClientLoginService.Settings settings=setting();
        JsonNode changed=success(admin.put("/auth/client-login-settings",
            Map.of("maxAttempts",3,"lockMinutes",15,"revision",settings.revision())));
        try {
            for(int i=0;i<3;i++){
                Client attacker=new Client();
                assertStatus(attacker.authenticate(username,"contraseña-incorrecta","client"),401);
            }
            long activeLocks=db.queryForObject("""
                SELECT COUNT(*) FROM app_users WHERE id=? AND login_locked_until>NOW()
                """,Long.class,userId);
            assertThat(activeLocks).isEqualTo(1L);
            Client blocked=new Client();
            assertStatus(blocked.authenticate(username,PASSWORD,"client"),401);

            // The customer's lockout policy does not affect the staff sign-in.
            Client staff=new Client();
            assertStatus(staff.authenticate("testadmin","PruebaSegura123!","staff"),200);

            assertStatus(admin.post("/auth/client-login-locks/"+userId+"/unlock",Map.of()),204);
            Client allowed=new Client();
            assertStatus(allowed.authenticate(username,PASSWORD,"client"),200);
            assertThat(db.queryForObject("""
                SELECT failed_login_attempts FROM app_users WHERE id=?
                """,Integer.class,userId)).isZero();
        }finally{
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

    @Test void recoveryCodeIsSingleUseAndChangesLocalPasswordWithoutPolicyOrLicense() throws Exception {
        long institutionId=institution(80,80);
        String username=unique("recuperar");
        JsonNode created=createUser(username,institutionId);
        long id=created.path("id").asLong();
        String code="0123456789ABCDEF0123456789ABCDEF0123";
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(code.getBytes(StandardCharsets.UTF_8)));
        db.update("""
            INSERT INTO client_password_recovery(user_id,token_hash,expires_at)
            VALUES(?,?,NOW()+INTERVAL '15 minutes')
            """,id,hash);
        Client guest=new Client();
        assertStatus(guest.post("/auth/recovery/reset",
            Map.of("username",username,"code",code,"newPassword","NuevaClaveSegura123!")),204);
        assertStatus(guest.post("/auth/recovery/reset",
            Map.of("username",username,"code",code,"newPassword","OtroIntentoSeguro123!")),400);
        Client old=new Client();
        assertStatus(old.authenticate(username,PASSWORD,"client"),401);
        Client updated=new Client();
        assertStatus(updated.authenticate(username,"NuevaClaveSegura123!","client"),200);
    }

    @Test void genericRequestDoesNotRevealUserExistenceWhenMailDisabled() throws Exception {
        Client guest=new Client();
        assertStatus(guest.post("/auth/recovery/request",
            Map.of("username",unique("desconocido"),"email","desconocido@example.test")),202);
        long institutionId=institution(20,20);
        String username=unique("cuenta");
        createUser(username,institutionId);
        assertStatus(guest.post("/auth/recovery/request",
            Map.of("username",username,"email","incorrecto@example.test")),202);
    }

    @Test void adminCanUpdateClientEmailInUserFormWithoutChangingInstitution() throws Exception {
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
