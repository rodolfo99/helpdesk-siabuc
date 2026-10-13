package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    @Test
    void billingCanOnlyViewBalancesAndRegisterPaymentsAndUsersCannotReadPayments() throws Exception {
        long institution = institution(20, 20);
        long caseId = supportCase(institution, "EMAIL");
        Client billing = billingUser();
        JsonNode institutions = json(billing.get("/institutions"));
        assertThat(ids(institutions)).contains(institution);
        for (JsonNode row : institutions) assertThat(row.path("notes").asText()).isEmpty();
        assertStatus(billing.get("/payments"), 200);
        for (String path : new String[]{"/cases", "/cases/" + caseId, "/users", "/audit", "/notifications", "/notifications/config"})
            assertStatus(billing.get(path), 403);
        assertStatus(billing.post("/institutions", institutionPayload("Alta prohibida", 10, 10)), 403);
        Map<String, Object> update = institutionPayload("Edición prohibida", 20, 20);
        update.put("active", true);
        assertStatus(billing.put("/institutions/" + institution, update), 403);
        assertStatus(billing.delete("/institutions/" + institution), 403);
        assertStatus(billing.post("/cases", casePayload(institution, "EMAIL")), 403);
        assertStatus(billing.post("/cases/" + caseId + "/entries", entryPayload("No autorizado", null, 0, false)), 403);
        assertStatus(billing.post("/notifications/test", Map.of()), 403);
        assertStatus(billing.post("/users", userPayload(unique("nopermitido"), institution, true)), 403);
        JsonNode payment = success(billing.post("/payments", paymentPayload(institution, 10, 15)));
        assertStatus(billing.post("/payments/" + payment.path("id").asLong() + "/void", Map.of("reason", "Sin permiso")), 403);
        Client user = institutionUser(institution);
        assertStatus(user.get("/payments"), 403);
        assertStatus(user.post("/payments", paymentPayload(institution, 10, 10)), 403);
        assertBalance(institution, 0, 30, 0, 35);
    }

    @Test
    void paymentCreditsBothChannelsExactlyOnceAndIdempotencyRejectsChangedContent() throws Exception {
        long institution = institution(10, 20);
        Map<String, Object> input = paymentPayload(institution, 30, 50);
        JsonNode first = success(admin.post("/payments", input));
        assertThat(first.path("emailMinutes").asLong()).isEqualTo(30);
        assertThat(first.path("remoteMinutes").asLong()).isEqualTo(50);
        assertBalance(institution, 0, 40, 0, 70);
        JsonNode retried = success(admin.post("/payments", input));
        assertThat(retried.path("id").asLong()).isEqualTo(first.path("id").asLong());
        assertThat(paymentsFor(institution)).isEqualTo(1);
        assertBalance(institution, 0, 40, 0, 70);
        input.put("amount", new BigDecimal("250.0"));
        assertThat(success(admin.post("/payments", input)).path("id").asLong()).isEqualTo(first.path("id").asLong());
        input.put("emailMinutes", 31);
        assertStatus(admin.post("/payments", input), 409);
        assertBalance(institution, 0, 40, 0, 70);
    }

    @Test
    void normalizedReferenceRemainsUniqueEvenAfterVoid() throws Exception {
        long institution = institution(10, 20);
        Map<String, Object> input = paymentPayload(institution, 30, 40);
        input.put("reference", "TransferenciaABC");
        JsonNode payment = success(admin.post("/payments", input));
        Map<String, Object> duplicate = new LinkedHashMap<>(input);
        duplicate.put("requestId", UUID.randomUUID().toString());
        duplicate.put("reference", "  transferenciaabc  ");
        assertStatus(admin.post("/payments", duplicate), 409);
        assertStatus(admin.post("/payments/" + payment.path("id").asLong() + "/void", Map.of("reason", "Registro duplicado a corregir")), 200);
        assertStatus(admin.post("/payments", duplicate), 409);
        assertBalance(institution, 0, 10, 0, 20);
        assertThat(paymentsFor(institution)).isEqualTo(1);
    }

    @Test
    void malformedPaymentsAndBudgetOverflowHaveNoPartialEffects() throws Exception {
        long institution = institution(10, 20);
        for (Map.Entry<String, Object> invalid : Map.<String, Object>of(
                "amount", new BigDecimal("0.00"), "currency", "mxn", "paidOn", "fecha-inválida",
                "requestId", "no-es-uuid", "notes", "", "emailMinutes", -1, "remoteMinutes", 1.5).entrySet()) {
            Map<String, Object> input = paymentPayload(institution, 10, 10);
            input.put(invalid.getKey(), invalid.getValue());
            assertStatus(admin.post("/payments", input), 400);
        }
        Map<String, Object> input = paymentPayload(institution, 0, 0);
        assertStatus(admin.post("/payments", input), 400);
        input = paymentPayload(institution, 10, 10);
        input.put("amount", new BigDecimal("1.001"));
        assertStatus(admin.post("/payments", input), 400);
        assertBalance(institution, 0, 10, 0, 20);
        assertThat(paymentsFor(institution)).isZero();
        long full = institution(1_000_000_000, 20);
        assertStatus(admin.post("/payments", paymentPayload(full, 1, 30)), 409);
        assertBalance(full, 0, 1_000_000_000, 0, 20);
        assertThat(paymentsFor(full)).isZero();
    }

    @Test
    void administratorCanVoidOnceWithoutDeletingEvidenceOrRecreditingOnRetry() throws Exception {
        long institution = institution(10, 20);
        Map<String, Object> input = paymentPayload(institution, 30, 40);
        JsonNode payment = success(admin.post("/payments", input));
        String path = "/payments/" + payment.path("id").asLong() + "/void";
        JsonNode voided = success(admin.post(path, Map.of("reason", "Corrección del registro")));
        assertThat(voided.path("voided").asBoolean()).isTrue();
        assertThat(voided.path("voidReason").asText()).isEqualTo("Corrección del registro");
        assertThat(voided.path("voidedBy").asText()).isEqualTo("testadmin");
        assertBalance(institution, 0, 10, 0, 20);
        assertStatus(admin.post(path, Map.of("reason", "Segundo intento")), 409);
        assertThat(success(admin.post("/payments", input)).path("voided").asBoolean()).isTrue();
        assertBalance(institution, 0, 10, 0, 20);
        assertThat(paymentsFor(institution)).isEqualTo(1);
    }

    @Test
    void cannotVoidCreditAlreadyNeededForConsumedMinutesInEitherChannel() throws Exception {
        long institution = institution(10, 20);
        JsonNode payment = success(admin.post("/payments", paymentPayload(institution, 20, 30)));
        long caseId = supportCase(institution, "REMOTE");
        addEntry(admin, caseId, "Sesión que utiliza la acreditación", "REMOTE", 21, false, 200);
        assertStatus(admin.post("/payments/" + payment.path("id").asLong() + "/void", Map.of("reason", "No procede por consumo")), 409);
        assertBalance(institution, 0, 30, 21, 29);
        for (JsonNode row : json(admin.get("/payments"))) {
            if (row.path("id").asLong() == payment.path("id").asLong()) assertThat(row.path("voided").asBoolean()).isFalse();
        }
    }

    @Test
    void upgradePreservesOriginalMigrationAndExistingInstitutionsCasesAndMinutes() throws Exception {
        byte[] v1 = new ClassPathResource("db/migration/V1__initial_schema.sql").getInputStream().readAllBytes();
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v1)))
                .isEqualTo("95ed53b528909cca7f92c656a3d9d28731b830f4aace7f6280d5124dba036660");
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
        var v2 = resolver.getResources("classpath*:db/migration/V2__*.sql");
        var v3 = resolver.getResources("classpath*:db/migration/V3__*.sql");
        assertThat(v2).hasSize(1);
        assertThat(v3).hasSize(1);
        db.execute((ConnectionCallback<Void>) connection -> {
            String originalPath;
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SHOW search_path")) {
                rows.next(); originalPath = rows.getString(1);
            }
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE SCHEMA " + schema);
                    statement.execute("SET search_path TO " + schema);
                }
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__initial_schema.sql"));
                try (var statement = connection.createStatement()) {
                    statement.execute("INSERT INTO institutions(id,name,email_allocated_minutes,email_used_minutes,remote_allocated_minutes) VALUES (1,'Institución previa',50,7,20)");
                    statement.execute("INSERT INTO app_users(id,username,display_name,password_hash,role) VALUES (1,'oldadmin','Admin anterior','unused-hash','ADMIN')");
                    statement.execute("INSERT INTO support_cases(id,institution_id,title,description,channel,created_by) VALUES (1,1,'Caso anterior','Histórico conservado','EMAIL',1)");
                    statement.execute("INSERT INTO case_entries(id,case_id,description,channel,minutes,created_by) VALUES (1,1,'Trabajo anterior','EMAIL',7,1)");
                }
                ScriptUtils.executeSqlScript(connection, v2[0]);
                ScriptUtils.executeSqlScript(connection, v3[0]);
                try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                        "SELECT i.email_allocated_minutes,i.email_used_minutes,e.minutes,c.description,u.role FROM institutions i JOIN support_cases c ON c.institution_id=i.id JOIN case_entries e ON e.case_id=c.id JOIN app_users u ON u.id=c.created_by")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1)).isEqualTo(50);
                    assertThat(rows.getLong(2)).isEqualTo(7);
                    assertThat(rows.getLong(3)).isEqualTo(7);
                    assertThat(rows.getString(4)).isEqualTo("Histórico conservado");
                    assertThat(rows.getString(5)).isEqualTo("ADMIN");
                    assertThat(rows.next()).isFalse();
                }
            } finally {
                try (var statement = connection.prepareStatement("SELECT set_config('search_path',?,false)")) {
                    statement.setString(1, originalPath); statement.execute();
                }
                try (var statement = connection.createStatement()) { statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
            }
            return null;
        });
    }

    private Client billingUser() throws Exception {
        String username = unique("cobranza");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("username", username); input.put("displayName", "Cobranza pruebas"); input.put("password", PASSWORD);
        input.put("role", "BILLING"); input.put("institutionId", null); input.put("active", true);
        input.put("email", "cobranza@example.test");
        success(admin.post("/users", input));
        return login(username, PASSWORD);
    }

    private long paymentsFor(long institution) throws Exception {
        long count = 0;
        for (JsonNode row : json(admin.get("/payments"))) if (row.path("institutionId").asLong() == institution) count++;
        return count;
    }

    static Map<String, Object> paymentPayload(long institution, long emailMinutes, long remoteMinutes) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("requestId", UUID.randomUUID().toString()); input.put("institutionId", institution);
        input.put("reference", "Pago-" + UUID.randomUUID()); input.put("amount", new BigDecimal("250.00"));
        input.put("currency", "MXN"); input.put("paidOn", "2026-10-13"); input.put("method", "TRANSFER");
        input.put("emailMinutes", emailMinutes); input.put("remoteMinutes", remoteMinutes);
        input.put("notes", "Pago recibido para ampliar el soporte de la institución");
        return input;
    }
}
