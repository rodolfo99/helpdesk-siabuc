package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class IncidentIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    private Map<String,Object> input(String title){return Map.of("title",title,"description","Diagnóstico interno reservado", "status","OPEN","note","Apertura del seguimiento interno");}
    private JsonNode create(String title)throws Exception{return success(admin.post("/incidents",input(title)));}
    private JsonNode publish(JsonNode value,String title,String summary)throws Exception{
        JsonNode incident=value.path("incident");return success(admin.post("/incidents/"+incident.path("id").asLong()+"/publication",Map.of("published",true,"publicTitle",title,"publicSummary",summary,"expectedRevision",incident.path("revision").asInt())));
    }
    private JsonNode update(JsonNode value,String title,String status)throws Exception{
        var payload=new LinkedHashMap<>(input(title));payload.put("status",status);payload.put("expectedRevision",value.path("incident").path("revision").asInt());
        return success(admin.put("/incidents/"+value.path("incident").path("id").asLong(),payload));
    }
    private Client staff(String role)throws Exception{
        String username=unique(role.toLowerCase());var body=new LinkedHashMap<>(userPayload(username,institution(0,0),true));body.put("role",role);body.put("institutionId",null);
        success(admin.post("/users",body));return login(username,PASSWORD);
    }
    private void link(long incidentId,long caseId)throws Exception{success(admin.post("/incidents/"+incidentId+"/cases",Map.of("caseId",caseId)));}

    @Test void customerSeesOnlyExplicitPublicSnapshotOnOwnLinkedCase()throws Exception{
        long institution=institution(90,60),other=institution(50,30),caseId=supportCase(institution,"EMAIL"),otherCase=supportCase(other,"REMOTE");
        Client client=institutionUser(institution),outsider=institutionUser(other);String secret=unique("PRIVADO");JsonNode value=create(secret);long id=value.path("incident").path("id").asLong();
        link(id,caseId);link(id,otherCase);
        assertThat(success(client.get("/cases/"+caseId+"/incidents")).isEmpty()).isTrue();
        value=publish(value,"Aviso de catálogo","Se investiga una inconsistencia. Consulte el avance en esta plataforma.");
        JsonNode result=success(client.get("/cases/"+caseId+"/incidents"));assertThat(result.size()).isEqualTo(1);JsonNode item=result.get(0);
        var fields=new TreeSet<String>();item.fieldNames().forEachRemaining(fields::add);assertThat(fields).containsExactly("publishedAt","status","summary","title");
        assertThat(item.path("title").asText()).isEqualTo("Aviso de catálogo");assertThat(item.toString()).doesNotContain(secret,"Diagnóstico","institutionId","incidentId","caseId","actor","cases");
        assertStatus(outsider.get("/cases/"+caseId+"/incidents"),404);assertStatus(client.get("/cases/"+otherCase+"/incidents"),404);
        assertStatus(client.get("/incidents/"+id),403);assertStatus(client.get("/incidents"),403);
    }
    @Test void internalEditsAndProgressNeverChangePublicSnapshotUntilRepublished()throws Exception{
        long institution=institution(10,10),caseId=supportCase(institution,"EMAIL");Client client=institutionUser(institution);JsonNode value=create(unique("Interno"));long id=value.path("incident").path("id").asLong();link(id,caseId);
        value=publish(value,"Aviso inicial","Se revisa el problema.");
        value=update(value,"Error confirmado interno","RESOLVED");
        value=success(admin.post("/incidents/"+id+"/progress",Map.of("note","Dato privado: credenciales y servidor nunca se copian","expectedRevision",value.path("incident").path("revision").asInt())));
        JsonNode before=success(client.get("/cases/"+caseId+"/incidents")).get(0);
        assertThat(before.path("title").asText()).isEqualTo("Aviso inicial");assertThat(before.path("status").asText()).isEqualTo("OPEN");assertThat(before.toString()).doesNotContain("Error confirmado","credenciales");
        value=publish(value,"Servicio restablecido","La solución está disponible.");
        JsonNode after=success(client.get("/cases/"+caseId+"/incidents")).get(0);assertThat(after.path("status").asText()).isEqualTo("RESOLVED");assertThat(after.path("title").asText()).isEqualTo("Servicio restablecido");
        assertThat(success(admin.get("/cases/"+caseId)).path("status").asText()).isEqualTo("OPEN");assertBalance(institution,0,10,0,10);
        assertThat(value.path("history").toString()).contains("PROGRESS","Dato privado","Aviso inicial");
    }
    @Test void unlinkAndUnpublishHideEverythingWhilePreservingAuditAndAllowingRelink()throws Exception{
        long institution=institution(30,30),caseId=supportCase(institution,"EMAIL");Client client=institutionUser(institution);JsonNode value=create(unique("Ocultación"));long id=value.path("incident").path("id").asLong();
        link(id,caseId);link(id,caseId);value=publish(value,"Aviso visible","Información pública.");
        success(admin.delete("/cases/"+caseId+"/incidents/"+id));success(admin.delete("/cases/"+caseId+"/incidents/"+id));
        assertThat(success(client.get("/cases/"+caseId+"/incidents")).size()).isZero();assertThat(db.queryForObject("SELECT active FROM incident_case_links WHERE incident_id=? AND case_id=?",Boolean.class,id,caseId)).isFalse();
        link(id,caseId);assertThat(success(client.get("/cases/"+caseId+"/incidents")).size()).isEqualTo(1);
        success(admin.post("/incidents/"+id+"/publication",Map.of("published",false,"expectedRevision",value.path("incident").path("revision").asInt())));
        assertThat(success(client.get("/cases/"+caseId+"/incidents")).toString()).isEqualTo("[]");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM incident_history WHERE incident_id=? AND action IN ('LINK','UNLINK')",Long.class,id)).isEqualTo(3);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_type='INCIDENT' AND entity_id=?",Long.class,id)).isEqualTo(6);
        assertBalance(institution,0,30,0,30);
    }
    @Test void technicianCanCoordinateButBillingAndCustomersCannotMutateOrReadInternalData()throws Exception{
        Client tech=staff("TECH"),billing=staff("BILLING");long institution=institution(40,40),caseId=supportCase(institution,"EMAIL");Client client=institutionUser(institution);
        JsonNode value=success(tech.post("/incidents",input(unique("Técnico"))));long id=value.path("incident").path("id").asLong();
        success(tech.post("/cases/"+caseId+"/incidents",Map.of("incidentId",id)));
        success(tech.post("/incidents/"+id+"/progress",Map.of("note","Validando una solución común.","expectedRevision",1)));
        success(tech.post("/incidents/"+id+"/publication",Map.of("published",true,"publicTitle","En revisión","publicSummary","El equipo está revisando la solicitud.","expectedRevision",2)));
        assertThat(success(tech.get("/incidents/"+id)).path("cases").size()).isEqualTo(1);
        assertStatus(billing.get("/incidents"),403);assertStatus(billing.get("/incidents/"+id),403);assertStatus(billing.get("/cases/"+caseId+"/incidents"),403);
        for(Client blocked:new Client[]{client,billing}){
            assertStatus(blocked.post("/incidents",input("Sin permiso")),403);
            assertStatus(blocked.post("/cases/"+caseId+"/incidents",Map.of("incidentId",id)),403);
            assertStatus(blocked.delete("/cases/"+caseId+"/incidents/"+id),403);
            assertStatus(blocked.post("/incidents/"+id+"/publication",Map.of("published",false,"expectedRevision",3)),403);
            assertStatus(blocked.get("/incidents/"+id+"/case-options?q=prueba"),403);
        }
        success(tech.delete("/incidents/"+id+"/cases/"+caseId));assertThat(success(tech.get("/cases/"+caseId+"/incidents")).size()).isZero();
    }
    @Test void staleRevisionAndInvalidInputsCannotOverwriteOrPublish()throws Exception{
        JsonNode value=create(unique("Validación"));long id=value.path("incident").path("id").asLong();value=update(value,"Segunda versión","IN_PROGRESS");
        var stale=new LinkedHashMap<>(input("No sobrescribir"));stale.put("expectedRevision",1);assertStatus(admin.put("/incidents/"+id,stale),409);
        assertStatus(admin.post("/incidents/"+id+"/publication",Map.of("published",true,"publicTitle","Aviso","publicSummary","Válido","expectedRevision",1)),409);
        assertStatus(admin.post("/incidents/"+id+"/publication",Map.of("published",true,"publicTitle","   ","publicSummary","Válido","expectedRevision",2)),400);
        assertStatus(admin.post("/incidents/"+id+"/publication",Map.of("published",true,"publicTitle","Aviso","publicSummary","x".repeat(10001),"expectedRevision",2)),400);
        assertStatus(admin.post("/incidents/"+id+"/progress",Map.of("note","Texto\u0000binario","expectedRevision",2)),400);
        assertStatus(admin.post("/incidents/"+id+"/progress",Map.of("note","Avance")),400);
        var invalid=new LinkedHashMap<>(input("Prueba"));invalid.put("description","x".repeat(20001));assertStatus(admin.post("/incidents",invalid),400);
        JsonNode actual=success(admin.get("/incidents/"+id));assertThat(actual.path("incident").path("title").asText()).isEqualTo("Segunda versión");assertThat(actual.path("incident").path("revision").asInt()).isEqualTo(2);assertThat(actual.path("history").size()).isEqualTo(2);
    }
    @Test void linkingOrResolvingNeverCreatesEntriesMailPaymentsOrChangesBudgets()throws Exception{
        long institution=institution(120,90),caseId=supportCase(institution,"EMAIL");addEntry(admin,caseId,"Atención previa privada","EMAIL",4,true,200);
        JsonNode before=success(admin.get("/cases/"+caseId));JsonNode value=create(unique("Sin cobro"));long id=value.path("incident").path("id").asLong();
        long entries=count("case_entries"),payments=count("payments"),mail=count("notification_outbox");
        link(id,caseId);value=update(value,"Problema resuelto","RESOLVED");value=publish(value,"Resuelto","Se resolvió el problema común.");success(admin.delete("/incidents/"+id+"/cases/"+caseId));
        assertThat(success(admin.get("/cases/"+caseId))).isEqualTo(before);assertBalance(institution,4,116,0,90);
        assertThat(count("case_entries")).isEqualTo(entries);assertThat(count("payments")).isEqualTo(payments);assertThat(count("notification_outbox")).isEqualTo(mail);
        assertThat(value.path("history").toString()).doesNotContain("Atención previa privada");
    }
    private long count(String table){return db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    @Test void searchAndOptionsUseLiteralTextAndRejectInactiveInstitutions()throws Exception{
        String marker=unique("Literal");JsonNode first=create(marker+" 100%_listo");create(marker+" otro");long id=first.path("incident").path("id").asLong();
        JsonNode results=success(admin.perform(admin.builder("GET","/incidents",false).param("q",marker+" 100%_").param("status","OPEN")));assertThat(results.path("total").asInt()).isEqualTo(1);
        assertStatus(admin.get("/incidents?page=0"),400);assertStatus(admin.get("/incidents?pageSize=101"),400);assertStatus(admin.get("/incidents?status=INVALID"),400);
        long institution=institution(30,10),caseId=supportCase(institution,"REMOTE");JsonNode options=success(admin.get("/incidents/"+id+"/case-options?q="+caseId));assertThat(ids(options)).contains(caseId);
        link(id,caseId);assertThat(ids(success(admin.get("/incidents/"+id+"/case-options?q="+caseId)))).doesNotContain(caseId);
        success(admin.delete("/cases/"+caseId+"/incidents/"+id));db.update("UPDATE institutions SET active=FALSE WHERE id=?",institution);
        assertStatus(admin.post("/incidents/"+id+"/cases",Map.of("caseId",caseId)),409);assertThat(ids(success(admin.get("/incidents/"+id+"/case-options?q="+caseId)))).doesNotContain(caseId);
        assertStatus(admin.post("/incidents/"+id+"/cases",Map.of("caseId",999999999)),404);
    }
    @Test void historySnapshotsAndLiteralMarkupAreRetainedForStaffOnly()throws Exception{
        String literal="<script>alert('texto')</script>";JsonNode value=create(literal);long id=value.path("incident").path("id").asLong();
        value=publish(value,literal,"<img src=x onerror=alert(1)> Texto literal");value=update(value,"Título corregido","IN_PROGRESS");
        JsonNode history=value.path("history");assertThat(history.size()).isEqualTo(3);assertThat(history.get(2).path("title").asText()).isEqualTo(literal);assertThat(history.get(1).path("publicSummary").asText()).contains("<img");
        assertThat(history.get(0).path("actor").asText()).isNotBlank();assertThat(history.get(0).path("createdAt").asText()).isNotBlank();
        assertStatus(admin.raw("POST","/incidents/"+id+"/progress",Map.of("note","Sin token","expectedRevision",3),false),403);
        assertThat(success(admin.get("/incidents/"+id)).path("history").size()).isEqualTo(3);
    }
}
