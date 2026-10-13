package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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

/** Shared HTTP harness: real session and CSRF cookie/header, no mocked business services. */
@SpringBootTest(properties = {
        "app.admin-username=testadmin", "app.admin-password=PruebaSegura123!",
        "app.admin-email=testadmin@example.test", "app.mail.enabled=false",
        "server.servlet.session.cookie.secure=false", "app.reporting-time-zone=America/Mexico_City"
})
@AutoConfigureMockMvc
abstract class ApiIntegrationSupport {
    protected static final String PASSWORD = "UsuarioSeguro123!";
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    protected Client admin;

    @BeforeEach
    void authenticateAdministrator() throws Exception {
        admin = login("testadmin", "PruebaSegura123!");
        assertThat(json(admin.get("/auth/me")).path("role").asText()).isEqualTo("ADMIN");
    }

    protected long institution(int emailMinutes, int remoteMinutes) throws Exception {
        return success(admin.post("/institutions", institutionPayload("Institución " + UUID.randomUUID(), emailMinutes, remoteMinutes)))
                .path("id").asLong();
    }

    protected Map<String, Object> institutionPayload(String name, int emailMinutes, int remoteMinutes) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", name);
        value.put("contactName", "Contacto de pruebas");
        value.put("email", "contacto@example.test");
        value.put("phone", "3120000000");
        value.put("notes", "Registro para integración PostgreSQL");
        value.put("emailAllocatedMinutes", emailMinutes);
        value.put("remoteAllocatedMinutes", remoteMinutes);
        return value;
    }

    protected Client institutionUser(long institution) throws Exception {
        String username = unique("usuario");
        createUser(username, institution);
        return login(username, PASSWORD);
    }

    protected JsonNode createUser(String username, long institution) throws Exception {
        return success(admin.post("/users", userPayload(username, institution, true)));
    }

    protected Map<String, Object> userPayload(String username, long institution, boolean active) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", username);
        data.put("displayName", "Usuario de pruebas");
        data.put("password", PASSWORD);
        data.put("role", "USER");
        data.put("institutionId", institution);
        data.put("active", active);
        return data;
    }

    protected long supportCase(long institution, String channel) throws Exception {
        return success(admin.post("/cases", casePayload(institution, channel))).path("id").asLong();
    }

    protected Map<String, Object> casePayload(long institution, String channel) {
        return Map.of("institutionId", institution, "title", "Caso de prueba " + UUID.randomUUID(),
                "description", "Descripción de la solicitud de soporte SIABUC", "channel", channel);
    }

    protected Map<String, Object> entryPayload(String description, String channel, int minutes, boolean internal) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("description", description);
        value.put("channel", channel);
        value.put("minutes", minutes);
        value.put("internal", internal);
        return value;
    }

    protected JsonNode addEntry(Client client, long caseId, String description, String channel, int minutes, boolean internal, int status) throws Exception {
        MvcResult result = client.post("/cases/" + caseId + "/entries", entryPayload(description, channel, minutes, internal));
        assertStatus(result, status);
        return json(result);
    }

    protected void assertBalance(long id, int emailUsed, int emailRemaining, int remoteUsed, int remoteRemaining) throws Exception {
        JsonNode value = findInstitution(id);
        assertThat(value.path("emailUsedMinutes").asInt()).isEqualTo(emailUsed);
        assertThat(value.path("emailRemainingMinutes").asInt()).isEqualTo(emailRemaining);
        assertThat(value.path("remoteUsedMinutes").asInt()).isEqualTo(remoteUsed);
        assertThat(value.path("remoteRemainingMinutes").asInt()).isEqualTo(remoteRemaining);
    }

    protected JsonNode findInstitution(long id) throws Exception {
        for (JsonNode institution : json(admin.get("/institutions"))) {
            if (institution.path("id").asLong() == id) return institution;
        }
        throw new AssertionError("Institución no encontrada: " + id);
    }

    protected List<Long> ids(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        array.forEach(item -> ids.add(item.path("id").asLong()));
        return ids;
    }

    protected static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    protected JsonNode success(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).withFailMessage("HTTP %s: %s", result.getResponse().getStatus(), result.getResponse().getContentAsString())
                .isIn(200, 201);
        return json(result);
    }

    protected static void assertStatus(MvcResult result, int expected) throws Exception {
        assertThat(result.getResponse().getStatus()).withFailMessage("HTTP esperado %s, recibido %s: %s", expected,
                result.getResponse().getStatus(), result.getResponse().getContentAsString()).isEqualTo(expected);
    }

    protected Client login(String username, String password) throws Exception {
        Client client = new Client();
        assertStatus(client.authenticate(username, password), 200);
        return client;
    }

    protected final class Client {
        protected MockHttpSession session;
        protected final Map<String, Cookie> cookies = new LinkedHashMap<>();
        protected String csrf;

        protected MvcResult authenticate(String username, String password) throws Exception {
            refreshCsrf();
            MockHttpServletRequestBuilder builder = builder("POST", "/auth/login", true)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .content("username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                            + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8));
            return perform(builder);
        }

        protected void refreshCsrf() throws Exception {
            MvcResult result = get("/auth/csrf");
            assertStatus(result, 200);
            csrf = json(result).path("token").asText();
            assertThat(csrf).isNotBlank();
        }

        protected MvcResult get(String path) throws Exception { return raw("GET", path, null, false); }
        protected MvcResult post(String path, Object body) throws Exception { return mutate("POST", path, body); }
        protected MvcResult put(String path, Object body) throws Exception { return mutate("PUT", path, body); }
        protected MvcResult delete(String path) throws Exception { return mutate("DELETE", path, null); }

        protected MvcResult mutate(String method, String path, Object body) throws Exception {
            refreshCsrf();
            return raw(method, path, body, true);
        }

        protected MvcResult raw(String method, String path, Object body, boolean useCsrf) throws Exception {
            MockHttpServletRequestBuilder builder = builder(method, path, useCsrf);
            if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
            return perform(builder);
        }

        protected MockHttpServletRequestBuilder builder(String method, String path, boolean useCsrf) {
            MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), "/api" + path);
            if (session != null && !session.isInvalid()) builder.session(session);
            if (!cookies.isEmpty()) builder.cookie(cookies.values().toArray(Cookie[]::new));
            if (useCsrf) builder.header("X-XSRF-TOKEN", csrf);
            return builder;
        }

        protected MvcResult perform(MockHttpServletRequestBuilder builder) throws Exception {
            MvcResult result = mvc.perform(builder).andReturn();
            session = (MockHttpSession) result.getRequest().getSession(false);
            for (Cookie cookie : result.getResponse().getCookies()) {
                if (cookie.getMaxAge() == 0) cookies.remove(cookie.getName());
                else cookies.put(cookie.getName(), cookie);
            }
            return result;
        }
    }
}
