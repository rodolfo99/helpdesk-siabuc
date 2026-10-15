package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** HTTP/CSRF, real persistence and accounting for the five-state workflow. */
class CaseWorkflowIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    private JsonNode state(long id, String state) throws Exception {
        return success(admin.post("/cases/"+id+"/status",Map.of("status",state,"reason","Validación de flujo 1.8.3")));
    }
    private String stored(long id) { return db.queryForObject("SELECT status FROM support_cases WHERE id=?",String.class,id); }
    private Instant resolutionStart(long id) { return db.queryForObject("SELECT resolution_started_at FROM case_profiles WHERE case_id=?",java.sql.Timestamp.class,id).toInstant(); }
    private String waiting(long id) { return db.queryForObject("SELECT waiting_for FROM support_cases WHERE id=?",String.class,id); }

    @Test void completeWorkflowPreservesAccountingAndResolutionAtClose() throws Exception {
        long inst=institution(60,30),id=supportCase(inst,"EMAIL");
        assertThat(stored(id)).isEqualTo("OPEN");
        Instant original=resolutionStart(id);
        assertThat(state(id,"IN_PROGRESS").path("status").asText()).isEqualTo("IN_PROGRESS");
        addEntry(admin,id,"Diagnóstico inicial", "EMAIL",7,false,200);
        state(id,"WAITING_CLIENT");assertThat(waiting(id)).isEqualTo("CLIENT");
        assertThat(resolutionStart(id)).isEqualTo(original);
        Client client=institutionUser(inst);
        JsonNode response=addEntry(client,id,"Adjunto información solicitada",null,0,false,200);
        assertThat(response.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(waiting(id)).isEqualTo("ADMIN");
        assertThat(resolutionStart(id)).isEqualTo(original);
        state(id,"RESOLVED");assertThat(waiting(id)).isEqualTo("NONE");
        JsonNode resolved=success(admin.get("/cases/"+id+"/profile")).path("sla");
        state(id,"CLOSED");
        assertThat(success(admin.get("/cases/"+id+"/profile")).path("sla").path("resolvedAt")).isEqualTo(resolved.path("resolvedAt"));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_type='CASE' AND entity_id=? AND action='CHANGE_CASE_STATUS'",Long.class,id)).isEqualTo(5);
        assertThat(success(admin.get("/cases/"+id)).path("entries").toString()).contains("Requiere respuesta del cliente", "El cliente respondió");
        assertBalance(inst,7,53,0,30);
    }

    @Test void repeatedStateIsIdempotentAndInvalidOrUnauthorizedRequestsDoNotMutate() throws Exception {
        long inst=institution(30,30),id=supportCase(inst,"EMAIL");
        state(id,"IN_PROGRESS");long before=db.queryForObject("SELECT COUNT(*) FROM case_entries WHERE case_id=?",Long.class,id);
        state(id,"IN_PROGRESS");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM case_entries WHERE case_id=?",Long.class,id)).isEqualTo(before);
        assertStatus(admin.post("/cases/"+id+"/status",Map.of("status","OPEN","reason","Regresar")),409);
        assertStatus(admin.post("/cases/"+id+"/status",Map.of("status","WAITING_CLIENT","reason","  ")),400);
        assertStatus(admin.post("/cases/"+id+"/status",Map.of("status","INVENTED","reason","Prueba")),400);
        Client client=institutionUser(inst),outsider=institutionUser(institution(30,30));
        assertStatus(client.post("/cases/"+id+"/status",Map.of("status","RESOLVED","reason","Intento cliente")),403);
        assertStatus(outsider.get("/cases/"+id),404);
        assertStatus(outsider.post("/cases/"+id+"/entries",entryPayload("No autorizado",null,0,false)),404);
        assertThat(stored(id)).isEqualTo("IN_PROGRESS");assertBalance(inst,0,30,0,30);
    }

    @Test void claimingReceivedCaseStartsAttentionWithoutCompletingFirstResponse() throws Exception {
        long inst=institution(30,30);Client client=institutionUser(inst);
        long id=success(client.post("/cases",casePayload(inst,"EMAIL"))).path("id").asLong();
        Instant start=resolutionStart(id);
        assertThat(success(admin.post("/cases/"+id+"/claim",Map.of())).path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(resolutionStart(id)).isEqualTo(start);
        assertThat(success(admin.get("/cases/"+id+"/profile")).path("sla").path("firstRespondedAt").isNull()).isTrue();
        assertBalance(inst,0,30,0,30);
    }

    @Test void resolvedCasesRequireReopeningAndCreditBeforeMoreWork() throws Exception {
        long inst=institution(1,0),id=supportCase(inst,"EMAIL");Client client=institutionUser(inst);
        addEntry(admin,id,"Se utiliza el último minuto", "EMAIL",1,false,200);
        state(id,"RESOLVED");
        addEntry(admin,id,"No debe consumir", "EMAIL",1,false,409);
        addEntry(client,id,"No debe reabrir implícitamente",null,0,false,409);
        assertStatus(admin.post("/cases/"+id+"/timer/start",Map.of()),409);
        assertStatus(admin.post("/cases/"+id+"/status",Map.of("status","IN_PROGRESS","reason","Más trabajo")),409);
        assertThat(stored(id)).isEqualTo("RESOLVED");
        state(id,"CLOSED");
        assertStatus(admin.post("/cases/"+id+"/status",Map.of("status","RESOLVED","reason","Retroceso")),409);
        var refill=new LinkedHashMap<>(institutionPayload("Saldo repuesto",5,0));refill.put("active",true);
        success(admin.put("/institutions/"+inst,refill));
        state(id,"IN_PROGRESS");
        assertThat(success(admin.get("/cases/"+id+"/profile")).path("sla").path("resolvedAt").isNull()).isTrue();
        assertBalance(inst,1,4,0,0);
    }

    @Test void activeTransitionsDoNotResetSlaButReopeningDoes() throws Exception {
        long inst=institution(20,0),id=supportCase(inst,"EMAIL");
        db.update("UPDATE case_profiles SET opened_at='2020-01-02T15:00:00Z',resolution_started_at='2020-01-02T15:00:00Z' WHERE case_id=?",id);
        Instant old=resolutionStart(id);
        for(String next:new String[]{"IN_PROGRESS","WAITING_CLIENT","IN_PROGRESS"}) {
            state(id,next);assertThat(resolutionStart(id)).isEqualTo(old);
            assertThat(success(admin.get("/cases/"+id+"/profile")).path("sla").path("resolutionState").asText()).isEqualTo("OVERDUE");
        }
        state(id,"RESOLVED");
        assertThat(success(admin.get("/cases/"+id+"/profile")).path("sla").path("resolutionState").asText()).isEqualTo("BREACHED");
        state(id,"IN_PROGRESS");assertThat(resolutionStart(id)).isAfter(old);
        assertThat(success(admin.get("/cases/"+id+"/profile")).path("sla").path("resolutionState").asText()).isEqualTo("PENDING");
    }

    @Test void waitingAndResolutionPauseRunningTimeWithoutChargingOrLosingIt() throws Exception {
        long inst=institution(20,0),id=supportCase(inst,"EMAIL");
        String username=unique("timer183");
        success(admin.post("/users",Map.of("username",username,"displayName","Técnico 183","role","TECH","password",PASSWORD,"active",true)));
        Client tech=login(username,PASSWORD);
        long timer=success(tech.post("/cases/"+id+"/timer/start",Map.of())).path("id").asLong();
        try {
            db.update("UPDATE work_timers SET segment_started_at=NOW()-INTERVAL '90 seconds' WHERE id=?",timer);
            state(id,"WAITING_CLIENT");
            JsonNode paused=success(tech.get("/cases/"+id+"/timer"));
            assertThat(paused.path("status").asText()).isEqualTo("PAUSED");
            assertThat(paused.path("elapsedSeconds").asLong()).isGreaterThanOrEqualTo(90);
            assertStatus(tech.post("/timers/"+timer+"/resume",Map.of()),409);
            state(id,"IN_PROGRESS");success(tech.post("/timers/"+timer+"/resume",Map.of()));
            state(id,"RESOLVED");assertThat(success(tech.get("/cases/"+id+"/timer")).path("status").asText()).isEqualTo("PAUSED");
            success(tech.post("/timers/"+timer+"/finish",Map.of()));
            assertStatus(tech.post("/timers/"+timer+"/commit",Map.of("minutes",2,"channel","EMAIL","description","Tiempo conservado","internal",false,"adjustmentReason","Prueba")),409);
            state(id,"IN_PROGRESS");
            success(tech.post("/timers/"+timer+"/commit",Map.of("minutes",2,"channel","EMAIL","description","Tiempo conservado","internal",false,"adjustmentReason","Prueba")));
            assertBalance(inst,2,18,0,0);
        } finally {
            String status=db.queryForObject("SELECT status FROM work_timers WHERE id=?",String.class,timer);
            if(!status.equals("COMMITTED"))success(tech.post("/timers/"+timer+"/cancel",Map.of()));
        }
    }

    @Test void newStatesAreVisibleInSavedViewsReportsAndNotifications() throws Exception {
        long inst=institution(30,0),id=supportCase(inst,"EMAIL");
        state(id,"WAITING_CLIENT");
        JsonNode view=success(admin.post("/saved-views",Map.of("name",unique("Espera"),"filters",Map.of("status","WAITING_CLIENT"))));
        assertThat(view.path("filters").path("status").asText()).isEqualTo("WAITING_CLIENT");
        assertThat(db.queryForList("SELECT body FROM notification_outbox WHERE case_id=?",String.class,id).toString()).contains("Estado: Requiere respuesta del cliente");
        state(id,"RESOLVED");
        LocalDate today=LocalDate.now(ZoneId.of("America/Mexico_City"));
        JsonNode report=success(admin.get("/reports/support?institutionId="+inst+"&from="+today+"&to="+today));
        assertThat(report.at("/totals/openCases").asLong()).isZero();
        assertThat(report.at("/totals/resolvedCases").asLong()).isEqualTo(1);
        assertThat(report.at("/totals/closedCases").asLong()).isZero();
        assertThat(ReportExporter.status("WAITING_CLIENT")).isEqualTo("Requiere respuesta del cliente");
        assertThat(ReportExporter.status("RESOLVED")).isEqualTo("Resuelto");
        assertBalance(inst,0,30,0,0);
    }
}
