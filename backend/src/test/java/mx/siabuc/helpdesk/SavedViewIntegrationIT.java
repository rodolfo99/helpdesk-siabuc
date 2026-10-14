package mx.siabuc.helpdesk;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
class SavedViewIntegrationIT extends ApiIntegrationSupport {
 @Autowired JdbcTemplate db;
 private Map<String,Object> filters(){return new LinkedHashMap<>(Map.of("search","diagnóstico","status","OPEN","assignment","all","category","INSTALLATION","priority","URGENT"));}
 private Map<String,Object> input(String name){return new LinkedHashMap<>(Map.of("name",name,"filters",filters()));}
 private Client staff(String role)throws Exception {String username=unique(role.toLowerCase());var value=new LinkedHashMap<>(userPayload(username,institution(0,0),true));value.put("role",role);value.put("institutionId",null);success(admin.post("/users",value));return login(username,PASSWORD);}
 @Test void viewsArePrivateAndMutationsRequireOwnershipAndCsrf()throws Exception{
  Client owner=institutionUser(institution(60,30)),other=institutionUser(institution(60,30));
  JsonNode view=success(owner.post("/saved-views",input(unique("Privada"))));long id=view.path("id").asLong();
  assertThat(ids(success(owner.get("/saved-views")))).contains(id);assertThat(ids(success(other.get("/saved-views")))).doesNotContain(id);
  assertStatus(other.get("/saved-views/"+id),404);assertStatus(admin.get("/saved-views/"+id),404);
  assertStatus(other.put("/saved-views/"+id,input("Robar")),404);assertStatus(other.delete("/saved-views/"+id),404);
  assertStatus(owner.raw("POST","/saved-views",input("Sin csrf"),false),403);
  assertStatus(owner.delete("/saved-views/"+id),200);assertStatus(owner.get("/saved-views/"+id),404);
 }
 @Test void scopesAndUnknownFiltersAreRejectedInsteadOfStored()throws Exception{
  long institution=institution(60,30),other=institution(60,30);Client user=institutionUser(institution);var f=filters();var data=input(unique("Filtros"));
  f.put("institutionId",other);data.put("filters",f);assertStatus(user.post("/saved-views",data),403);
  f.put("institutionId",institution);f.put("assignment","mine");assertStatus(user.post("/saved-views",data),403);
  f.put("assignment","all");f.put("sql","DROP TABLE support_cases");assertStatus(user.post("/saved-views",data),400);
  f.remove("sql");f.put("institutionId",1.25);assertStatus(user.post("/saved-views",data),400);
  f.put("institutionId",institution);f.put("priority","SUPER");assertStatus(user.post("/saved-views",data),400);
  f.put("priority","URGENT");f.put("search",Map.of("unsafe","nested"));assertStatus(user.post("/saved-views",data),400);
  f.put("search","100%_ literal");JsonNode valid=success(user.post("/saved-views",data));assertThat(valid.path("filters").path("search").asText()).isEqualTo("100%_ literal");
 }
 @Test void techniciansCanSaveOwnQueueAndBillingCannotUseViews()throws Exception{
  Client tech=staff("TECH"),billing=staff("BILLING");var input=input(unique("Mis pendientes"));var f=filters();f.put("assignment","mine");input.put("filters",f);
  JsonNode created=success(tech.post("/saved-views",input));assertThat(created.path("filters").path("assignment").asText()).isEqualTo("mine");
  assertStatus(billing.get("/saved-views"),403);assertStatus(billing.post("/saved-views",input),403);
  assertThat(success(tech.get("/saved-views/"+created.path("id").asLong())).path("filters").path("status").asText()).isEqualTo("OPEN");
 }
 @Test void updatesProtectRevisionsAndNamesWhileOtherAccountsMayUseSameName()throws Exception{
  Client user=institutionUser(institution(60,30)),other=institutionUser(institution(60,30));String name=unique("Vista");JsonNode value=success(user.post("/saved-views",input(name)));long id=value.path("id").asLong();
  assertStatus(user.post("/saved-views",input(name.toUpperCase())),409);success(other.post("/saved-views",input(name)));
  var changed=input("Nombre actualizado");changed.put("expectedRevision",1);JsonNode updated=success(user.put("/saved-views/"+id,changed));assertThat(updated.path("revision").asInt()).isEqualTo(2);
  assertStatus(user.put("/saved-views/"+id,changed),409);assertThat(success(user.get("/saved-views/"+id)).path("name").asText()).isEqualTo("Nombre actualizado");
 }
 @Test void transferredClientCannotApplyOldInstitutionViewAndLimitIsEnforced()throws Exception{
  long institution=institution(60,30),other=institution(60,30);String username=unique("traslado");JsonNode account=createUser(username,institution);Client user=login(username,PASSWORD);
  var data=input("Institución anterior");var f=filters();f.put("institutionId",institution);data.put("filters",f);long id=success(user.post("/saved-views",data)).path("id").asLong();
  db.update("UPDATE app_users SET institution_id=? WHERE id=?",other,account.path("id").asLong());
  assertThat(ids(success(user.get("/saved-views")))).doesNotContain(id);assertStatus(user.get("/saved-views/"+id),404);
  for(int i=0;i<49;i++)db.update("INSERT INTO saved_case_views(owner_id,name,filters) VALUES (?,?,'{}'::jsonb)",account.path("id").asLong(),"Límite "+i);
  assertStatus(user.post("/saved-views",input("Número 51")),409);
 }
}
