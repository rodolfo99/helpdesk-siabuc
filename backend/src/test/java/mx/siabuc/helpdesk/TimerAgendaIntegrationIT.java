package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** HTTP/session/CSRF integration against the real PostgreSQL accounting and notification paths. */
class TimerAgendaIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    @Test
    void stopwatchPersistsPauseAndFinishWithoutChargingThenCommitsExactlyOnce() throws Exception {
        Client worker=staff("ADMIN"); long institution=institution(20,20); long caseId=supportCase(institution,"EMAIL");
        JsonNode timer=success(worker.post("/cases/"+caseId+"/timer/start",Map.of())); long id=timer.path("id").asLong();
        assertThat(success(worker.post("/cases/"+caseId+"/timer/start",Map.of())).path("id").asLong()).isEqualTo(id);
        db.update("UPDATE work_timers SET segment_started_at=NOW()-INTERVAL '61 seconds' WHERE id=?",id);
        JsonNode paused=success(worker.post("/timers/"+id+"/pause",Map.of()));
        assertThat(paused.path("status").asText()).isEqualTo("PAUSED");
        assertThat(paused.path("elapsedSeconds").asLong()).isGreaterThanOrEqualTo(61);
        assertThat(success(worker.get("/cases/"+caseId+"/timer"))).isEqualTo(paused);
        assertBalance(institution,0,20,0,20);
        assertThat(success(worker.post("/timers/"+id+"/resume",Map.of())).path("status").asText()).isEqualTo("RUNNING");
        JsonNode finished=success(worker.post("/timers/"+id+"/finish",Map.of()));
        assertThat(finished.path("suggestedMinutes").asLong()).isEqualTo(2);
        assertBalance(institution,0,20,0,20);
        JsonNode committed=success(worker.post("/timers/"+id+"/commit",commit(2,"EMAIL","Atención medida con cronómetro",false,"")));
        assertThat(committed.path("status").asText()).isEqualTo("COMMITTED");
        assertThat(committed.path("entryId").asLong()).isPositive();
        assertBalance(institution,2,18,0,20);
        JsonNode retry=success(worker.post("/timers/"+id+"/commit",commit(5,"REMOTE","Reintento HTTP no duplica cargo",false,"Otro valor no se aplica")));
        assertThat(retry).isEqualTo(committed);
        assertBalance(institution,2,18,0,20);
        assertThat(db.queryForObject("SELECT count(*) FROM case_entries WHERE case_id=? AND minutes>0",Long.class,caseId)).isEqualTo(1);
    }

    @Test
    void simultaneousCommitRequestsChargeOnlyOneEntry() throws Exception {
        Client worker=staff("ADMIN");String username=success(worker.get("/auth/me")).path("username").asText();Client duplicate=login(username,PASSWORD);
        long institution=institution(10,10),caseId=supportCase(institution,"EMAIL");
        long id=success(worker.post("/cases/"+caseId+"/timer/start",Map.of())).path("id").asLong();
        db.update("UPDATE work_timers SET segment_started_at=NOW()-INTERVAL '61 seconds' WHERE id=?",id);
        success(worker.post("/timers/"+id+"/finish",Map.of()));
        worker.refreshCsrf();duplicate.refreshCsrf();CountDownLatch start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->{start.await();return worker.raw("POST","/timers/"+id+"/commit",commit(2,"EMAIL","Confirmación simultánea",false,""),true);});
            var second=pool.submit(()->{start.await();return duplicate.raw("POST","/timers/"+id+"/commit",commit(2,"EMAIL","Confirmación simultánea",false,""),true);});
            start.countDown();JsonNode a=success(first.get(30,TimeUnit.SECONDS)),b=success(second.get(30,TimeUnit.SECONDS));
            assertThat(a.path("entryId").asLong()).isEqualTo(b.path("entryId").asLong());
            assertBalance(institution,2,8,0,10);
            assertThat(db.queryForObject("SELECT count(*) FROM case_entries WHERE case_id=? AND minutes>0",Long.class,caseId)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void timerRequiresOwnerAndAdministratorAndPreventsParallelPendingTimers() throws Exception {
        Client worker=staff("ADMIN"),another=staff("ADMIN"),billing=staff("BILLING");
        long institution=institution(10,10),caseId=supportCase(institution,"EMAIL"),otherCase=supportCase(institution,"EMAIL"); Client client=institutionUser(institution);
        assertStatus(client.post("/cases/"+caseId+"/timer/start",Map.of()),403);
        assertStatus(billing.get("/cases/"+caseId+"/timer"),403);
        assertStatus(worker.raw("POST","/cases/"+caseId+"/timer/start",Map.of(),false),403);
        long id=success(worker.post("/cases/"+caseId+"/timer/start",Map.of())).path("id").asLong();
        assertStatus(worker.post("/cases/"+otherCase+"/timer/start",Map.of()),409);
        assertStatus(another.post("/timers/"+id+"/pause",Map.of()),404);
        assertStatus(client.post("/timers/"+id+"/cancel",Map.of()),403);
        assertStatus(worker.post("/timers/"+id+"/commit",commit(1,"EMAIL","Sin finalizar",false,"")),409);
        success(worker.post("/timers/"+id+"/cancel",Map.of()));
        assertThat(success(worker.post("/cases/"+otherCase+"/timer/start",Map.of())).path("id").asLong()).isNotEqualTo(id);
        assertBalance(institution,0,10,0,10);
    }

    @Test
    void failedBalanceOrClosedCaseRetainsFinishedTimeAndAdjustmentRequiresReason() throws Exception {
        Client worker=staff("ADMIN");long institution=institution(1,1),caseId=supportCase(institution,"EMAIL");
        long id=success(worker.post("/cases/"+caseId+"/timer/start",Map.of())).path("id").asLong();
        db.update("UPDATE work_timers SET segment_started_at=NOW()-INTERVAL '121 seconds' WHERE id=?",id);
        success(worker.post("/timers/"+id+"/finish",Map.of()));
        assertStatus(worker.post("/timers/"+id+"/commit",commit(3,"EMAIL","Atención sin saldo suficiente",false,"")),409);
        assertThat(success(worker.get("/cases/"+caseId+"/timer")).path("status").asText()).isEqualTo("FINISHED");
        assertBalance(institution,0,1,0,1);
        assertStatus(worker.post("/timers/"+id+"/commit",commit(1,"EMAIL","Ajuste sin motivo",false,"")),400);
        success(admin.post("/cases/"+caseId+"/status",Map.of("status","CLOSED","reason","Cierre durante revisión de atención")));
        assertStatus(worker.post("/timers/"+id+"/commit",commit(1,"EMAIL","Atención revisada",false,"Se cobra sólo el minuto útil")),409);
        assertThat(success(worker.get("/cases/"+caseId+"/timer")).path("status").asText()).isEqualTo("FINISHED");
        success(admin.post("/cases/"+caseId+"/status",Map.of("status","OPEN","reason","Continuar revisión")));
        JsonNode committed=success(worker.post("/timers/"+id+"/commit",commit(1,"EMAIL","Atención revisada",true,"Se cobra sólo el minuto útil")));
        assertThat(committed.path("adjustmentReason").asText()).contains("minuto útil");
        assertBalance(institution,1,0,0,1);
        Client customer=institutionUser(institution);
        assertThat(ids(success(customer.get("/cases/"+caseId)).path("entries"))).doesNotContain(committed.path("entryId").asLong());
    }

    @Test
    void remoteProposalOnlyOwnCustomerCanConfirmAndRescheduleRequiresFreshConfirmation() throws Exception {
        Client worker=staff("ADMIN");long institution=institution(10,10),caseId=supportCase(institution,"REMOTE");
        Client client=institutionUser(institution),outsider=institutionUser(institution(10,10)),billing=staff("BILLING");
        Instant start=Instant.now().plusSeconds(86400);
        assertStatus(client.post("/cases/"+caseId+"/remote-sessions",proposal(start)),403);
        JsonNode proposal=success(worker.post("/cases/"+caseId+"/remote-sessions",proposal(start)));long id=proposal.path("id").asLong();
        assertThat(proposal.path("status").asText()).isEqualTo("PROPOSED");
        assertThat(success(client.get("/cases/"+caseId+"/remote-sessions"))).hasSize(1);
        assertStatus(outsider.get("/cases/"+caseId+"/remote-sessions"),404);
        assertStatus(outsider.post("/remote-sessions/"+id+"/confirm",Map.of("response","")),404);
        assertStatus(worker.post("/remote-sessions/"+id+"/confirm",Map.of("response","")),403);
        assertStatus(billing.get("/cases/"+caseId+"/remote-sessions"),403);
        assertThat(success(client.post("/remote-sessions/"+id+"/confirm",Map.of("response","Horario confirmado"))).path("status").asText()).isEqualTo("CONFIRMED");
        assertBalance(institution,0,10,0,10);
        JsonNode replacement=success(worker.post("/remote-sessions/"+id+"/reschedule",proposal(start.plusSeconds(3600))));
        assertThat(replacement.path("status").asText()).isEqualTo("PROPOSED");
        assertThat(replacement.path("replacesId").asLong()).isEqualTo(id);
        assertStatus(client.post("/remote-sessions/"+id+"/confirm",Map.of("response","Confirmación obsoleta")),409);
        assertThat(db.queryForObject("SELECT status FROM remote_sessions WHERE id=?",String.class,id)).isEqualTo("CANCELLED");
        long newId=replacement.path("id").asLong();
        assertThat(success(client.post("/remote-sessions/"+newId+"/decline",Map.of("response","Solicito otro horario"))).path("status").asText()).isEqualTo("DECLINED");
        assertBalance(institution,0,10,0,10);
        JsonNode detail=success(client.get("/cases/"+caseId));
        assertThat(detail.path("entries")).hasSize(5);
        assertThat(detail.path("entries").get(0).path("description").asText()).contains("Sesión remota", "propuesta");
        assertThat(db.queryForObject("SELECT count(*) FROM notification_outbox WHERE case_id=? AND event_type='REMOTE_SESSION_PROPOSED'",Long.class,caseId)).isEqualTo(4);
    }

    @Test
    void agendaPreventsOverlapAndInvalidRescheduleRollsBackOldConfirmation() throws Exception {
        Client worker=staff("ADMIN");long institution=institution(10,10),caseId=supportCase(institution,"REMOTE"),other=supportCase(institution,"REMOTE");
        Client client=institutionUser(institution);Instant firstStart=Instant.now().plusSeconds(172800);
        long id=success(worker.post("/cases/"+caseId+"/remote-sessions",proposal(firstStart))).path("id").asLong();
        success(client.post("/remote-sessions/"+id+"/confirm",Map.of("response","")));
        assertStatus(worker.post("/cases/"+other+"/remote-sessions",proposal(firstStart.plusSeconds(60))),409);
        // Exactly adjacent slots are allowed.
        success(worker.post("/cases/"+other+"/remote-sessions",proposal(firstStart.plusSeconds(1800))));
        assertStatus(worker.post("/remote-sessions/"+id+"/reschedule",proposal(firstStart.plusSeconds(1860))),409);
        assertThat(db.queryForObject("SELECT status FROM remote_sessions WHERE id=?",String.class,id)).isEqualTo("CONFIRMED");
        assertThat(db.queryForObject("SELECT count(*) FROM remote_sessions WHERE replaces_id=?",Long.class,id)).isZero();
        assertStatus(worker.post("/remote-sessions/"+id+"/complete",Map.of("response","No ha empezado")),409);
        assertStatus(worker.post("/cases/"+caseId+"/remote-sessions",proposal(Instant.now().minusSeconds(3600))),400);
        assertBalance(institution,0,10,0,10);
    }

    @Test
    void simultaneousOverlappingProposalsAcrossInstitutionsReserveOnlyOneSlot() throws Exception {
        Client worker=staff("ADMIN");String username=success(worker.get("/auth/me")).path("username").asText();Client duplicate=login(username,PASSWORD);
        long firstInstitution=institution(10,10),secondInstitution=institution(10,10);
        long firstCase=supportCase(firstInstitution,"REMOTE"),secondCase=supportCase(secondInstitution,"REMOTE");
        Instant at=Instant.now().plusSeconds(432000);
        worker.refreshCsrf();duplicate.refreshCsrf();CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->{ready.countDown();start.await();return worker.raw("POST","/cases/"+firstCase+"/remote-sessions",proposal(at),true);});
            var second=pool.submit(()->{ready.countDown();start.await();return duplicate.raw("POST","/cases/"+secondCase+"/remote-sessions",proposal(at.plusSeconds(60)),true);});
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            var a=first.get(30,TimeUnit.SECONDS);var b=second.get(30,TimeUnit.SECONDS);
            assertThat(new int[]{a.getResponse().getStatus(),b.getResponse().getStatus()}).containsExactlyInAnyOrder(200,409);
            JsonNode winner=success(a.getResponse().getStatus()==200?a:b);
            assertThat(winner.path("status").asText()).isEqualTo("PROPOSED");
            assertThat(db.queryForObject("SELECT count(*) FROM remote_sessions WHERE case_id IN (?,?)",Long.class,firstCase,secondCase)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT count(*) FROM case_entries WHERE case_id IN (?,?) AND description LIKE 'Sesión remota%propuesta%'",Long.class,firstCase,secondCase)).isEqualTo(1);
            assertBalance(firstInstitution,0,10,0,10);assertBalance(secondInstitution,0,10,0,10);
        } finally { start.countDown();pool.shutdownNow(); }
    }

    @Test
    void simultaneousCrossAssignmentsDoNotDeadlockOnAdministratorForeignKeys() throws Exception {
        Client firstAdmin=staff("ADMIN"),secondAdmin=staff("ADMIN");
        long firstId=success(firstAdmin.get("/auth/me")).path("id").asLong(),secondId=success(secondAdmin.get("/auth/me")).path("id").asLong();
        long firstCase=supportCase(institution(10,10),"REMOTE"),secondCase=supportCase(institution(10,10),"REMOTE");
        Instant at=Instant.now().plusSeconds(518400);
        Map<String,Object> forSecond=new LinkedHashMap<>(proposal(at)),forFirst=new LinkedHashMap<>(proposal(at));
        forSecond.put("adminId",secondId);forFirst.put("adminId",firstId);
        firstAdmin.refreshCsrf();secondAdmin.refreshCsrf();CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->{ready.countDown();start.await();return firstAdmin.raw("POST","/cases/"+firstCase+"/remote-sessions",forSecond,true);});
            var second=pool.submit(()->{ready.countDown();start.await();return secondAdmin.raw("POST","/cases/"+secondCase+"/remote-sessions",forFirst,true);});
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            JsonNode a=success(first.get(30,TimeUnit.SECONDS)),b=success(second.get(30,TimeUnit.SECONDS));
            assertThat(a.path("adminId").asLong()).isEqualTo(secondId);assertThat(b.path("adminId").asLong()).isEqualTo(firstId);
            assertThat(a.path("status").asText()).isEqualTo("PROPOSED");assertThat(b.path("status").asText()).isEqualTo("PROPOSED");
            assertThat(db.queryForObject("SELECT count(*) FROM remote_sessions WHERE case_id IN (?,?)",Long.class,firstCase,secondCase)).isEqualTo(2);
        } finally { start.countDown();pool.shutdownNow(); }
    }

    @Test
    void closedCaseBlocksNewSessionsAndConfirmationButAllowsCancellationWithoutCharge() throws Exception {
        Client worker=staff("ADMIN");long institution=institution(5,5),caseId=supportCase(institution,"REMOTE");Client client=institutionUser(institution);
        long id=success(worker.post("/cases/"+caseId+"/remote-sessions",proposal(Instant.now().plusSeconds(259200)))).path("id").asLong();
        success(admin.post("/cases/"+caseId+"/status",Map.of("status","CLOSED","reason","Caso resuelto sin sesión")));
        assertStatus(client.post("/remote-sessions/"+id+"/confirm",Map.of("response","")),409);
        assertStatus(worker.post("/cases/"+caseId+"/remote-sessions",proposal(Instant.now().plusSeconds(345600))),409);
        assertThat(success(worker.post("/remote-sessions/"+id+"/cancel",Map.of("response","Ya no se requiere"))).path("status").asText()).isEqualTo("CANCELLED");
        assertBalance(institution,0,5,0,5);
        assertThat(success(client.get("/cases/"+caseId)).path("status").asText()).isEqualTo("CLOSED");
    }

    private Client staff(String role) throws Exception {
        String name=unique(role.toLowerCase());Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("username",name);payload.put("displayName","Personal cronómetro y agenda");payload.put("password",PASSWORD);payload.put("role",role);payload.put("active",true);payload.put("email",name+"@example.test");
        success(admin.post("/users",payload));return login(name,PASSWORD);
    }
    private Map<String,Object> proposal(Instant start) { return Map.of("startsAt",start.toString(),"endsAt",start.plusSeconds(1800).toString(),"note","Revisión de configuración SIABUC; tener disponible la captura del error."); }
    private Map<String,Object> commit(long minutes,String channel,String description,boolean internal,String reason) { return Map.of("minutes",minutes,"channel",channel,"description",description,"internal",internal,"adjustmentReason",reason); }
}
