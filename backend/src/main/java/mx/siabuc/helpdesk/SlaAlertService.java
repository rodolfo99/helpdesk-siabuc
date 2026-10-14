package mx.siabuc.helpdesk;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Portal-only reminders. Never invokes SMTP, case entries or support accounting. */
@Service
public class SlaAlertService {
 public record Settings(boolean enabled,int leadMinutes) {}
 public record Input(Boolean enabled,Integer leadMinutes) {}
 private record CaseRow(long id,long institutionId,String title,Long assignedId) {}
 private final JdbcTemplate db;private final HelpdeskService helpdesk;private final CaseProfileService profiles;
 private final TransactionTemplate transaction;
 public SlaAlertService(JdbcTemplate db,HelpdeskService helpdesk,CaseProfileService profiles,PlatformTransactionManager manager){this.db=db;this.helpdesk=helpdesk;this.profiles=profiles;this.transaction=new TransactionTemplate(manager);}
 public Settings settings(){requireStaff();return current();}
 @Transactional public Settings update(Input input){
  Models.User actor=requireAdmin();if(input==null||input.enabled()==null||input.leadMinutes()==null||input.leadMinutes()<1||input.leadMinutes()>43200)throw ApiException.bad("La anticipación debe ser de 1 a 43200 minutos naturales.");
  db.update("UPDATE sla_alert_settings SET enabled=?,lead_minutes=?,updated_at=NOW() WHERE id=1",input.enabled(),input.leadMinutes());
  db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,?,?,1,?)",actor.username(),"UPDATE_SLA_ALERTS","SLA_ALERT_SETTINGS","Avisos="+input.enabled()+"; anticipación="+input.leadMinutes()+" minutos naturales");
  return current();
 }
 public Map<String,Integer> scanAsAdmin(){requireAdmin();return Map.of("created",scanDueAlerts());}
 public int scanDueAlerts(){
  if(!current().enabled())return 0;int total=0;
  for(Long caseId:db.queryForList("SELECT c.id FROM support_cases c JOIN institutions i ON i.id=c.institution_id WHERE c.status='OPEN' AND i.active ORDER BY c.institution_id,c.id",Long.class)){
   Integer created=transaction.execute(status->scanOne(caseId));if(created!=null)total+=created;
  }return total;
 }
 private int scanOne(long caseId){
  // Same lock order as attention/accounting: institution first, then case. Each case is its own transaction.
  List<Long> ids=db.queryForList("SELECT institution_id FROM support_cases WHERE id=?",Long.class,caseId);if(ids.isEmpty())return 0;
  Boolean active=db.queryForObject("SELECT active FROM institutions WHERE id=? FOR UPDATE",Boolean.class,ids.get(0));if(!Boolean.TRUE.equals(active))return 0;
  var rows=db.query("SELECT id,institution_id,title,assigned_admin_id FROM support_cases WHERE id=? AND status='OPEN' FOR UPDATE",(r,n)->new CaseRow(r.getLong(1),r.getLong(2),r.getString(3),r.getObject(4,Long.class)),caseId);
  Settings settings=current();if(rows.isEmpty()||!settings.enabled())return 0;
  CaseRow item=rows.get(0);var sla=profiles.profile(caseId).sla();Instant limit=Instant.now().plusSeconds(settings.leadMinutes()*60L);
  List<Long> recipients=db.queryForList("SELECT id FROM app_users WHERE active AND (role='ADMIN' OR (role='TECH' AND (?::bigint IS NULL OR id=?))) ORDER BY id",Long.class,item.assignedId(),item.assignedId());
  int sent=0;
  if(sla.firstRespondedAt()==null&&!sla.firstResponseDueAt().isAfter(limit))sent+=deliver(item,"FIRST_RESPONSE",sla.firstResponseDueAt(),recipients);
  if(sla.resolvedAt()==null&&!sla.resolutionDueAt().isAfter(limit))sent+=deliver(item,"RESOLUTION",sla.resolutionDueAt(),recipients);
  return sent;
 }
 private int deliver(CaseRow item,String goal,Instant due,List<Long> recipients){
  int sent=0;String goalName="FIRST_RESPONSE".equals(goal)?"primera respuesta":"resolución";
  String title="Caso #"+item.id()+" · Plazo de "+goalName+" · "+item.title();if(title.length()>300)title=title.substring(0,299)+"…";
  String description="El plazo de "+goalName+" vence el "+due+" (UTC). Revise la ficha del caso para la fecha en su zona horaria y el estado actual. Este aviso no descuenta minutos de soporte.";
  for(Long recipient:recipients){
   int added=db.update("INSERT INTO sla_alert_deliveries(case_id,goal,due_at,recipient_id) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",item.id(),goal,Timestamp.from(due),recipient);
   if(added==0)continue;
   db.update("INSERT INTO portal_notifications(recipient_id,institution_id,case_id,event_type,title,description,internal) VALUES (?,?,?,'SLA_DUE',?,?,TRUE)",recipient,item.institutionId(),item.id(),title,description);sent++;
  }return sent;
 }
 private Settings current(){return db.queryForObject("SELECT enabled,lead_minutes FROM sla_alert_settings WHERE id=1",(r,n)->new Settings(r.getBoolean(1),r.getInt(2)));}
 private Models.User requireStaff(){Models.User user=helpdesk.me();if(!List.of("ADMIN","TECH").contains(user.role().name()))throw ApiException.forbidden();return user;}
 private Models.User requireAdmin(){Models.User user=helpdesk.me();if(user.role()!=Models.Role.ADMIN)throw ApiException.forbidden();return user;}
}
