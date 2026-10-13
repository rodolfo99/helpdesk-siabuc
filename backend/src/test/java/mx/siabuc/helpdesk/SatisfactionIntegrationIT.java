package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class SatisfactionIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    @Test
    void institutionalCustomerAnswersClosedCaseOnceWithAuthorAndResponsibilitySnapshotsWithoutConsumption() throws Exception {
        long institution=institution(10,10),caseId=supportCase(institution,"EMAIL");Client customer=institutionUser(institution);
        assertThat(success(customer.get(path(caseId))).path("canRespond").asBoolean()).isFalse();close(caseId);
        assertThat(success(customer.get(path(caseId))).path("canRespond").asBoolean()).isTrue();
        JsonNode actor=success(customer.get("/auth/me")),administrator=success(admin.get("/auth/me"));
        JsonNode answered=success(customer.post(path(caseId),input(5,"La solución fue clara y oportuna."))),survey=answered.path("survey");
        assertThat(answered.path("canRespond").asBoolean()).isFalse();assertThat(survey.path("score").asInt()).isEqualTo(5);
        assertThat(survey.path("respondentId").asLong()).isEqualTo(actor.path("id").asLong());assertThat(survey.path("respondentUsername").asText()).isEqualTo(actor.path("username").asText());
        assertThat(survey.path("administratorId").asLong()).isEqualTo(administrator.path("id").asLong());assertThat(survey.path("administratorName").asText()).isEqualTo(administrator.path("displayName").asText());
        assertThat(survey.path("createdAt").asText()).isNotBlank();assertThat(success(admin.get(path(caseId))).path("survey")).isEqualTo(survey);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='RESPOND_SATISFACTION' AND entity_id=?",Long.class,caseId)).isEqualTo(1);
        assertBalance(institution,0,10,0,10);
    }

    @Test
    void rolesInstitutionOwnershipActiveAccountsAndCsrfAreEnforced() throws Exception {
        long institution=institution(10,10),caseId=supportCase(institution,"EMAIL");Client own=institutionUser(institution),foreign=institutionUser(institution(10,10)),billing=staff("BILLING");close(caseId);
        assertStatus(admin.post(path(caseId),input(5,"Administrador no sustituye al cliente")),403);
        assertStatus(billing.get(path(caseId)),403);assertStatus(billing.post(path(caseId),input(4,"Sin permiso")),403);
        assertStatus(foreign.get(path(caseId)),404);assertStatus(foreign.post(path(caseId),input(1,"Caso ajeno")),404);
        assertStatus(own.raw("POST",path(caseId),input(3,"Sin CSRF"),false),403);
        for(String endpoint:new String[]{"/satisfaction/reports","/satisfaction/reports.csv","/satisfaction/options"}){assertStatus(own.get(endpoint),403);assertStatus(billing.get(endpoint),403);}
        assertStatus(new Client().get(path(caseId)),401);
        own.refreshCsrf();assertStatus(admin.delete("/institutions/"+institution),204);assertStatus(own.raw("POST",path(caseId),input(3,"Institución desactivada"),true),401);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM satisfaction_surveys WHERE case_id=?",Long.class,caseId)).isZero();
    }

    @Test
    void invalidScoresPayloadsCommentsAndOpenCasesNeverSaveAResponse() throws Exception {
        long institution=institution(10,10),caseId=supportCase(institution,"EMAIL");Client customer=institutionUser(institution);
        assertStatus(customer.post(path(caseId),input(5,"Caso abierto")),409);close(caseId);
        assertStatus(customer.post(path(caseId),input(0,"Fuera de rango")),400);assertStatus(customer.post(path(caseId),input(6,"Fuera de rango")),400);
        assertStatus(customer.post(path(caseId),Map.of("requestId",UUID.randomUUID().toString(),"score",2.5,"comment","No se aceptan fracciones")),400);
        assertStatus(customer.post(path(caseId),Map.of("requestId","invalid","score",3,"comment","")),400);
        assertStatus(customer.post(path(caseId),Map.of("requestId",UUID.randomUUID().toString(),"comment","Falta puntuación")),400);
        assertStatus(customer.post(path(caseId),input(3,"x".repeat(2001))),400);
        assertStatus(customer.post(path(caseId),Map.of("requestId",UUID.randomUUID().toString(),"score",3,"comment","","administratorId",1)),400);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM satisfaction_surveys WHERE case_id=?",Long.class,caseId)).isZero();assertBalance(institution,0,10,0,10);
    }

    @Test
    void idempotentRetryPreservesResponseAndReopeningNeverCreatesAnotherSurvey() throws Exception {
        long institution=institution(10,10),caseId=supportCase(institution,"EMAIL");Client customer=institutionUser(institution),colleague=institutionUser(institution);close(caseId);
        Map<String,Object> payload=input(4,"Atención recibida");JsonNode initial=success(customer.post(path(caseId),payload));
        assertThat(success(customer.post(path(caseId),payload))).isEqualTo(initial);
        assertStatus(customer.post(path(caseId),Map.of("requestId",payload.get("requestId"),"score",1,"comment","Intento de cambiar puntuación")),409);
        assertStatus(colleague.post(path(caseId),payload),409);assertStatus(colleague.post(path(caseId),input(5,"Segunda respuesta institucional")),409);
        success(admin.post("/cases/"+caseId+"/status",Map.of("status","OPEN","reason","Seguimiento adicional")));
        JsonNode reopened=success(customer.get(path(caseId)));assertThat(reopened.path("caseStatus").asText()).isEqualTo("OPEN");assertThat(reopened.path("canRespond").asBoolean()).isFalse();
        assertThat(success(customer.post(path(caseId),payload)).path("survey")).isEqualTo(initial.path("survey"));close(caseId);
        assertStatus(customer.post(path(caseId),input(5,"No reiniciar calificación")),409);
        assertThat(success(customer.get(path(caseId))).path("survey")).isEqualTo(initial.path("survey"));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM satisfaction_surveys WHERE case_id=?",Long.class,caseId)).isEqualTo(1);
        long another=supportCase(institution,"EMAIL");close(another);assertStatus(customer.post(path(another),payload),409);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM satisfaction_surveys WHERE case_id=?",Long.class,another)).isZero();
    }

    @Test
    void simultaneousInstitutionalResponsesProduceOneWinnerAndOneConflict() throws Exception {
        long institution=institution(10,10),caseId=supportCase(institution,"EMAIL");Client first=institutionUser(institution),second=institutionUser(institution);close(caseId);
        first.refreshCsrf();second.refreshCsrf();CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->{ready.countDown();go.await();return first.raw("POST",path(caseId),input(5,"Primera opinión"),true);});var b=pool.submit(()->{ready.countDown();go.await();return second.raw("POST",path(caseId),input(2,"Segunda opinión"),true);});assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(new int[]{a.get(30,TimeUnit.SECONDS).getResponse().getStatus(),b.get(30,TimeUnit.SECONDS).getResponse().getStatus()}).containsExactlyInAnyOrder(200,409);
        }finally{go.countDown();pool.shutdownNow();}
        assertThat(db.queryForObject("SELECT COUNT(*) FROM satisfaction_surveys WHERE case_id=?",Long.class,caseId)).isEqualTo(1);assertBalance(institution,0,10,0,10);
    }

    @Test
    void reportUsesResponseDateAndSampleWeightedAverageWithImmutableResponsibilityAndText() throws Exception {
        long institution=institution(10,10);Client customer=institutionUser(institution),otherAdmin=staff("ADMIN");long firstAdmin=success(admin.get("/auth/me")).path("id").asLong(),secondAdmin=success(otherAdmin.get("/auth/me")).path("id").asLong();
        long[] cases=new long[4];
        for(int index=0;index<4;index++){
            Map<String,Object> create=new LinkedHashMap<>(casePayload(institution,"EMAIL"));create.put("assignedAdminId",index==3?secondAdmin:firstAdmin);
            cases[index]=success(admin.post("/cases",create)).path("id").asLong();close(cases[index]);success(customer.post(path(cases[index]),input(index==3?1:5,"Muestra "+index)));
            db.update("UPDATE satisfaction_surveys SET created_at=? WHERE case_id=?",Timestamp.from(Instant.parse("2026-01-15T12:00:00Z")),cases[index]);
        }
        JsonNode before=success(customer.get(path(cases[0]))).path("survey");
        success(admin.put("/cases/"+cases[0],Map.of("title","Título reasignado después de respuesta","description","El histórico de encuesta no debe reescribirse.","channel","EMAIL","assignedAdminId",secondAdmin)));
        Map<String,Object> renamed=new LinkedHashMap<>(institutionPayload("Nombre institucional posterior",10,10));renamed.put("active",true);success(admin.put("/institutions/"+institution,renamed));
        assertThat(success(customer.get(path(cases[0]))).path("survey")).isEqualTo(before);
        String query="/satisfaction/reports?from=2026-01-15&to=2026-01-15&institutionId="+institution;
        JsonNode report=success(admin.get(query));assertThat(report.path("samples").asInt()).isEqualTo(4);assertThat(report.path("scoreSum").asInt()).isEqualTo(16);
        assertThat(report.path("averageScore").decimalValue()).isEqualByComparingTo(new BigDecimal("4.00"));assertThat(report.path("administrators")).hasSize(2);
        assertThat(report.path("distribution").get(0).path("samples").asInt()).isEqualTo(1);assertThat(report.path("distribution").get(4).path("samples").asInt()).isEqualTo(3);
        JsonNode first=success(admin.get(query+"&administratorId="+firstAdmin));assertThat(first.path("samples").asInt()).isEqualTo(3);assertThat(first.path("averageScore").asDouble()).isEqualTo(5);
        JsonNode second=success(admin.get(query+"&administratorId="+secondAdmin));assertThat(second.path("samples").asInt()).isEqualTo(1);assertThat(second.path("averageScore").asDouble()).isEqualTo(1);
        // CDMX day ends at 06:00 UTC on the following date. This response must move out of the Jan 15 window.
        db.update("UPDATE satisfaction_surveys SET created_at=? WHERE case_id=?",Timestamp.from(Instant.parse("2026-01-16T06:00:00Z")),cases[3]);
        assertThat(success(admin.get(query)).path("samples").asInt()).isEqualTo(3);
        JsonNode empty=success(admin.get("/satisfaction/reports?from=2026-01-17&to=2026-01-17&institutionId="+institution));assertThat(empty.path("samples").asInt()).isZero();assertThat(empty.path("averageScore").isNull()).isTrue();
        assertStatus(admin.get("/satisfaction/reports?from=2026-01-18&to=2026-01-01"),400);assertStatus(admin.get("/satisfaction/reports?from=2026-02-30"),400);
    }

    @Test
    void csvEscapesUntrustedTextAndRequiresAdministrativeAccess() throws Exception {
        long institution=institution(10,10),caseId=supportCase(institution,"EMAIL");Client customer=institutionUser(institution);close(caseId);
        String comment="=HYPERLINK(\"https://example.test\")\nSegunda línea, con coma";success(customer.post(path(caseId),input(3,comment)));
        var csv=admin.get("/satisfaction/reports.csv?institutionId="+institution);assertStatus(csv,200);
        String content=new String(csv.getResponse().getContentAsByteArray(),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).startsWith("\uFEFFEncuesta,Caso").contains("\"'=HYPERLINK(\"\"https://example.test\"\")\nSegunda línea, con coma\"");
        assertThat(csv.getResponse().getHeader("Content-Disposition")).contains("attachment", ".csv");assertThat(csv.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(csv.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");assertStatus(customer.get("/satisfaction/reports.csv"),403);
        assertThat(success(customer.get(path(caseId))).path("survey").path("comment").asText()).isEqualTo(comment);
    }

    private static String path(long caseId){return "/satisfaction/cases/"+caseId;}
    private Map<String,Object> input(int score,String comment){return Map.of("requestId",UUID.randomUUID().toString(),"score",score,"comment",comment);}
    private void close(long caseId)throws Exception{success(admin.post("/cases/"+caseId+"/status",Map.of("status","CLOSED","reason","Atención concluida; encuesta disponible al cliente")));}
    private Client staff(String role)throws Exception{String username=unique(role.toLowerCase());success(admin.post("/users",Map.of("username",username,"displayName","Personal encuesta "+username,"password",PASSWORD,"role",role,"active",true,"email",username+"@example.test")));return login(username,PASSWORD);}
}
