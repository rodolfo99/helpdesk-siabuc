package mx.siabuc.helpdesk;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
class SlaAlertIntegrationIT extends ApiIntegrationSupport {
 @Autowired JdbcTemplate db;
 private record Staff(Client client,long id){}
 private Staff staff(String role)throws Exception {String username=unique(role.toLowerCase());var value=new LinkedHashMap<>(userPayload(username,institution(0,0),true));value.put("role",role);value.put("institutionId",null);long id=success(admin.post("/users",value)).path("id").asLong();return new Staff(login(username,PASSWORD),id);}
 private void due(long caseId)throws Exception{
  var goal=Map.of("firstResponseMinutes",10,"resolutionMinutes",20);
  var calendar=Map.of("timezone","UTC","workDays",List.of(1,2,3,4,5,6,7),"startTime","00:00","endTime","23:59","holidays",List.of(),"goals",Map.of("LOW",goal,"NORMAL",goal,"HIGH",goal,"URGENT",goal));
  db.update("UPDATE case_profiles SET calendar_snapshot=?,opened_at=NOW()-INTERVAL '2 hours',resolution_started_at=NOW()-INTERVAL '2 hours',first_responded_at=NULL,resolved_at=NULL WHERE case_id=?",mapper.writeValueAsString(calendar),caseId);
 }
 private long count(long caseId,long recipient){return db.queryForObject("SELECT COUNT(*) FROM portal_notifications WHERE case_id=? AND recipient_id=? AND event_type='SLA_DUE'",Long.class,caseId,recipient);}
 private void configure(boolean enabled)throws Exception{success(admin.put("/sla-alerts/settings",Map.of("enabled",enabled,"leadMinutes",60)));}
 private void restore(JsonNode value){db.update("UPDATE sla_alert_settings SET enabled=?,lead_minutes=? WHERE id=1",value.path("enabled").asBoolean(),value.path("leadMinutes").asInt());}
 @Test void dueGoalsNotifyAssignedTechnicianOnceWithMailPausedAndNoTimeCharge()throws Exception{
  JsonNode old=success(admin.get("/sla-alerts/settings"));try{
   configure(false);long institution=institution(60,30),caseId=supportCase(institution,"EMAIL");Staff tech=staff("TECH"),other=staff("TECH");Client client=institutionUser(institution);Staff billing=staff("BILLING");
   db.update("UPDATE support_cases SET assigned_admin_id=? WHERE id=?",tech.id(),caseId);due(caseId);long entries=db.queryForObject("SELECT COUNT(*) FROM case_entries WHERE case_id=?",Long.class,caseId);
   configure(true);success(admin.post("/sla-alerts/scan",Map.of()));success(admin.post("/sla-alerts/scan",Map.of()));
   assertThat(count(caseId,tech.id())).isEqualTo(2);assertThat(count(caseId,other.id())).isZero();assertThat(count(caseId,billing.id())).isZero();
   JsonNode inbox=success(tech.client().get("/inbox"));assertThat(inbox.toString()).contains("SLA_DUE");
   long notice=db.queryForObject("SELECT MIN(id) FROM portal_notifications WHERE case_id=? AND recipient_id=? AND event_type='SLA_DUE'",Long.class,caseId,tech.id());
   assertStatus(tech.client().post("/inbox/"+notice+"/read",Map.of()),204);assertStatus(client.post("/inbox/"+notice+"/read",Map.of()),404);
   success(admin.post("/sla-alerts/scan",Map.of()));assertThat(count(caseId,tech.id())).isEqualTo(2);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM case_entries WHERE case_id=?",Long.class,caseId)).isEqualTo(entries);assertBalance(institution,0,60,0,30);
  }finally{restore(old);}
 }
 @Test void disabledClosedRespondedAndInactiveInstitutionsDoNotGenerateAlerts()throws Exception{
  JsonNode old=success(admin.get("/sla-alerts/settings"));try{
   configure(false);Staff tech=staff("TECH");long inst=institution(60,30),caseId=supportCase(inst,"EMAIL");db.update("UPDATE support_cases SET assigned_admin_id=? WHERE id=?",tech.id(),caseId);due(caseId);
   success(admin.post("/sla-alerts/scan",Map.of()));assertThat(count(caseId,tech.id())).isZero();
   db.update("UPDATE case_profiles SET first_responded_at=NOW(),resolved_at=NOW() WHERE case_id=?",caseId);configure(true);success(admin.post("/sla-alerts/scan",Map.of()));assertThat(count(caseId,tech.id())).isZero();
   due(caseId);db.update("UPDATE support_cases SET status='CLOSED' WHERE id=?",caseId);success(admin.post("/sla-alerts/scan",Map.of()));assertThat(count(caseId,tech.id())).isZero();
   db.update("UPDATE support_cases SET status='OPEN' WHERE id=?",caseId);db.update("UPDATE institutions SET active=FALSE WHERE id=?",inst);success(admin.post("/sla-alerts/scan",Map.of()));assertThat(count(caseId,tech.id())).isZero();
  }finally{restore(old);}
 }
 @Test void newDeadlineAndNewAssigneeReceiveNewDedupeKeysAndUnassignedQueueReachesTechnicians()throws Exception{
  JsonNode old=success(admin.get("/sla-alerts/settings"));try{
   configure(false);Staff tech=staff("TECH"),second=staff("TECH");long caseId=supportCase(institution(60,30),"EMAIL");db.update("UPDATE support_cases SET assigned_admin_id=NULL WHERE id=?",caseId);due(caseId);configure(true);success(admin.post("/sla-alerts/scan",Map.of()));assertThat(count(caseId,tech.id())).isEqualTo(2);assertThat(count(caseId,second.id())).isEqualTo(2);
   db.update("UPDATE support_cases SET assigned_admin_id=? WHERE id=?",tech.id(),caseId);db.update("UPDATE case_profiles SET resolution_started_at=resolution_started_at+INTERVAL '1 minute' WHERE case_id=?",caseId);
   success(admin.post("/sla-alerts/scan",Map.of()));assertThat(count(caseId,tech.id())).isEqualTo(3);assertThat(count(caseId,second.id())).isEqualTo(2);
   db.update("UPDATE support_cases SET assigned_admin_id=? WHERE id=?",second.id(),caseId);success(admin.post("/sla-alerts/scan",Map.of()));assertThat(count(caseId,second.id())).isEqualTo(3);
  }finally{restore(old);}
 }
 @Test void warningStartsBeforeDueOnlyWithinConfiguredLead()throws Exception{
  JsonNode old=success(admin.get("/sla-alerts/settings"));try{
   configure(false);Staff tech=staff("TECH");long caseId=supportCase(institution(60,30),"EMAIL");due(caseId);db.update("UPDATE support_cases SET assigned_admin_id=? WHERE id=?",tech.id(),caseId);
   db.update("UPDATE case_profiles SET opened_at=NOW()+INTERVAL '2 hours',resolution_started_at=NOW()+INTERVAL '2 hours' WHERE case_id=?",caseId);configure(true);
   success(admin.post("/sla-alerts/scan",Map.of()));assertThat(count(caseId,tech.id())).isZero();
   db.update("UPDATE case_profiles SET opened_at=NOW(),resolution_started_at=NOW() WHERE case_id=?",caseId);
   success(admin.post("/sla-alerts/scan",Map.of()));assertThat(count(caseId,tech.id())).isEqualTo(2);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM sla_alert_deliveries WHERE case_id=? AND recipient_id=? AND due_at>NOW()",Long.class,caseId,tech.id())).isEqualTo(2);
  }finally{restore(old);}
 }
 @Test void settingsAndScanEnforceRolesValidationAndCsrf()throws Exception{
  Staff tech=staff("TECH"),billing=staff("BILLING");Client client=institutionUser(institution(60,30));success(tech.client().get("/sla-alerts/settings"));
  for(Client actor:new Client[]{tech.client(),billing.client(),client}){assertStatus(actor.put("/sla-alerts/settings",Map.of("enabled",false,"leadMinutes",60)),403);assertStatus(actor.post("/sla-alerts/scan",Map.of()),403);}
  assertStatus(client.get("/sla-alerts/settings"),403);assertStatus(billing.client().get("/sla-alerts/settings"),403);
  assertStatus(admin.put("/sla-alerts/settings",Map.of("enabled",true,"leadMinutes",0)),400);assertStatus(admin.put("/sla-alerts/settings",Map.of("enabled",true,"leadMinutes",43201)),400);assertStatus(admin.put("/sla-alerts/settings",Map.of("leadMinutes",60)),400);
  assertStatus(admin.raw("POST","/sla-alerts/scan",Map.of(),false),403);
 }
}
