package mx.siabuc.helpdesk;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
class ReplyTemplateIntegrationIT extends ApiIntegrationSupport {
 @Autowired JdbcTemplate db;
 private Map<String,Object> input(String title){return new LinkedHashMap<>(Map.of("title",title,"category","INSTALLATION","siabucVersion","SIABUC 9.1","body","1. Verifique el servicio.\n2. Adjunte el diagnóstico."));}
 private Client staff(String role)throws Exception {String username=unique(role.toLowerCase());var value=new LinkedHashMap<>(userPayload(username,institution(0,0),true));value.put("role",role);value.put("institutionId",null);success(admin.post("/users",value));return login(username,PASSWORD);}
 @Test void technicianCanPreviewButCannotManageTemplatesOrReadEditorialHistory()throws Exception{
  Client tech=staff("TECH");JsonNode value=success(admin.post("/reply-templates",input(unique("Diagnóstico"))));long id=value.path("id").asLong();
  assertThat(success(tech.get("/reply-templates/"+id)).path("body").asText()).contains("diagnóstico");
  assertStatus(tech.post("/reply-templates",input("No permitido")),403);assertStatus(tech.put("/reply-templates/"+id,input("Cambio")),403);
  assertStatus(tech.post("/reply-templates/"+id+"/status",Map.of("status","ARCHIVED","expectedRevision",1)),403);assertStatus(tech.get("/reply-templates/"+id+"/history"),403);
  assertThat(success(admin.get("/reply-templates/"+id)).path("revision").asInt()).isEqualTo(1);
 }
 @Test void clientsAndBillingCannotReadTemplatesAndCsrfGuardsWrites()throws Exception{
  Client user=institutionUser(institution(60,30)),billing=staff("BILLING");long id=success(admin.post("/reply-templates",input(unique("Privada")))).path("id").asLong();
  for(Client actor:new Client[]{user,billing}){assertStatus(actor.get("/reply-templates"),403);assertStatus(actor.get("/reply-templates/"+id),403);assertStatus(actor.post("/reply-templates",input("No")),403);}
  assertStatus(admin.raw("POST","/reply-templates",input("Sin csrf"),false),403);
 }
 @Test void revisionsArchiveAndSnapshotsProtectHistoricalResponses()throws Exception{
  long institution=institution(60,30),caseId=supportCase(institution,"EMAIL");JsonNode value=success(admin.post("/reply-templates",input(unique("Historial"))));long id=value.path("id").asLong();
  String old=value.path("body").asText();addEntry(admin,caseId,old,null,0,false,200);
  var update=input("Respuesta corregida");update.put("body","Procedimiento nuevo.");update.put("expectedRevision",1);
  success(admin.put("/reply-templates/"+id,update));assertStatus(admin.put("/reply-templates/"+id,update),409);
  success(admin.post("/reply-templates/"+id+"/status",Map.of("status","ARCHIVED","expectedRevision",2)));
  update.put("expectedRevision",3);assertStatus(admin.put("/reply-templates/"+id,update),409);
  JsonNode history=success(admin.get("/reply-templates/"+id+"/history"));assertThat(history.size()).isEqualTo(3);assertThat(history.get(2).path("snapshot").path("body").asText()).isEqualTo(old);
  assertThat(db.queryForObject("SELECT description FROM case_entries WHERE case_id=? ORDER BY id DESC LIMIT 1",String.class,caseId)).isEqualTo(old);
  assertBalance(institution,0,60,0,30);
 }
 @Test void archivedContentIsHiddenFromTechniciansEvenWhenRequestedAndCanReactivate()throws Exception{
  Client tech=staff("TECH");String marker=unique("Archivada");long id=success(admin.post("/reply-templates",input(marker))).path("id").asLong();
  success(admin.post("/reply-templates/"+id+"/status",Map.of("status","ARCHIVED","expectedRevision",1)));
  assertStatus(tech.get("/reply-templates/"+id),404);assertThat(success(tech.get("/reply-templates?q="+marker+"&includeArchived=true")).size()).isZero();
  assertThat(success(admin.get("/reply-templates?q="+marker+"&includeArchived=true")).size()).isEqualTo(1);
  success(admin.post("/reply-templates/"+id+"/status",Map.of("status","ACTIVE","expectedRevision",2)));
  assertThat(success(tech.get("/reply-templates/"+id)).path("status").asText()).isEqualTo("ACTIVE");
 }
 @Test void searchLiteralTextValidationAndPreviewNeverCreateEntriesOrCharge()throws Exception{
  long institution=institution(60,30),caseId=supportCase(institution,"EMAIL");long before=db.queryForObject("SELECT COUNT(*) FROM case_entries WHERE case_id=?",Long.class,caseId);
  String title=unique("Plantilla")+" 100%_";var data=input(title);String plain="<script>alert('texto')</script>\nMensaje literal";data.put("body",plain);
  JsonNode value=success(admin.post("/reply-templates",data));long id=value.path("id").asLong();
  JsonNode list=success(admin.perform(admin.builder("GET","/reply-templates",false).param("q",title).param("category","INSTALLATION").param("siabucVersion","9.1")));assertThat(list.size()).isEqualTo(1);assertThat(list.get(0).has("body")).isFalse();
  assertThat(success(admin.get("/reply-templates/"+id)).path("body").asText()).isEqualTo(plain);
  data.put("body","x".repeat(10001));assertStatus(admin.post("/reply-templates",data),400);data.put("body","\u0000");assertStatus(admin.post("/reply-templates",data),400);data.put("body"," ");assertStatus(admin.post("/reply-templates",data),400);
  assertStatus(admin.get("/reply-templates?category=INVALID"),400);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM case_entries WHERE case_id=?",Long.class,caseId)).isEqualTo(before);assertBalance(institution,0,60,0,30);
 }
}
