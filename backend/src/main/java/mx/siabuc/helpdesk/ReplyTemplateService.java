package mx.siabuc.helpdesk;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ReplyTemplateService {
 public enum Status { ACTIVE,ARCHIVED }
 public record Input(String title,CaseProfileService.Category category,String siabucVersion,String body,Integer expectedRevision) {}
 public record StatusInput(Status status,Integer expectedRevision) {}
 public record Template(long id,String title,CaseProfileService.Category category,String siabucVersion,String body,Status status,int revision,Instant updatedAt) {}
 public record Summary(long id,String title,CaseProfileService.Category category,String siabucVersion,Status status,int revision,Instant updatedAt) {}
 public record History(long id,int revision,String actor,String action,Template snapshot,Instant createdAt) {}
 private final JdbcTemplate db;private final HelpdeskService helpdesk;private final ObjectMapper json;
 public ReplyTemplateService(JdbcTemplate db,HelpdeskService helpdesk,ObjectMapper json){this.db=db;this.helpdesk=helpdesk;this.json=json;}
 public List<Summary> list(String q,CaseProfileService.Category category,String version,boolean includeArchived){
  Models.User actor=staff();String query=clean(q,300,"Búsqueda",false),v=clean(version,120,"Versión de SIABUC",false);
  var args=new ArrayList<Object>();StringBuilder sql=new StringBuilder("SELECT * FROM reply_templates WHERE 1=1");
  if(actor.role()!=Models.Role.ADMIN||!includeArchived)sql.append(" AND status='ACTIVE'");
  if(!query.isEmpty()){sql.append(" AND (position(lower(?) in lower(title))>0 OR position(lower(?) in lower(body))>0)");args.add(query);args.add(query);}
  if(category!=null){sql.append(" AND category=?");args.add(category.name());}
  if(!v.isEmpty()){sql.append(" AND position(lower(?) in lower(siabuc_version))>0");args.add(v);}
  sql.append(" ORDER BY lower(title),id");
  return db.query(sql.toString(),(r,n)->new Summary(r.getLong("id"),r.getString("title"),CaseProfileService.Category.valueOf(r.getString("category")),r.getString("siabuc_version"),Status.valueOf(r.getString("status")),r.getInt("revision"),r.getTimestamp("updated_at").toInstant()),args.toArray());
 }
 public Template get(long id){Models.User actor=staff();Template value=row(id,false);if(actor.role()!=Models.Role.ADMIN&&value.status()!=Status.ACTIVE)throw ApiException.missing();return value;}
 @Transactional public Template create(Input input){
  Models.User actor=admin();validate(input);
  long id=db.queryForObject("INSERT INTO reply_templates(title,category,siabuc_version,body,created_by) VALUES (?,?,?,?,?) RETURNING id",Long.class,input.title().trim(),input.category().name(),clean(input.siabucVersion(),120,"Versión de SIABUC",false),input.body().trim(),actor.id());
  return record(id,actor,"CREATE");
 }
 @Transactional public Template update(long id,Input input){
  Models.User actor=admin();Template before=row(id,true);validate(input);revision(input.expectedRevision(),before);
  if(before.status()!=Status.ACTIVE)throw ApiException.conflict("Reactive la plantilla antes de editarla.");
  db.update("UPDATE reply_templates SET title=?,category=?,siabuc_version=?,body=?,revision=revision+1,updated_at=NOW() WHERE id=?",input.title().trim(),input.category().name(),clean(input.siabucVersion(),120,"Versión de SIABUC",false),input.body().trim(),id);
  return record(id,actor,"UPDATE");
 }
 @Transactional public Template status(long id,StatusInput input){
  Models.User actor=admin();Template before=row(id,true);if(input==null||input.status()==null)throw ApiException.bad("Indique el estado de la plantilla.");revision(input.expectedRevision(),before);
  if(before.status()==input.status())return before;
  db.update("UPDATE reply_templates SET status=?,revision=revision+1,updated_at=NOW() WHERE id=?",input.status().name(),id);
  return record(id,actor,input.status()==Status.ARCHIVED?"ARCHIVE":"ACTIVATE");
 }
 public List<History> history(long id){admin();row(id,false);return db.query("SELECT * FROM reply_template_history WHERE template_id=? ORDER BY revision DESC",(r,n)->new History(r.getLong("id"),r.getInt("revision"),r.getString("actor"),r.getString("action"),decode(r.getString("snapshot")),r.getTimestamp("created_at").toInstant()),id);}
 private Template record(long id,Models.User actor,String action){
  Template value=row(id,false);String snapshot=encode(value);
  db.update("INSERT INTO reply_template_history(template_id,revision,actor,action,snapshot) VALUES (?,?,?,?,?::jsonb)",id,value.revision(),actor.username(),action,snapshot);
  db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,?,?,?,?)",actor.username(),action+"_REPLY_TEMPLATE","REPLY_TEMPLATE",id,"Plantilla: "+value.title()+"; revisión "+value.revision());return value;
 }
 private Template row(long id,boolean lock){var rows=db.query("SELECT * FROM reply_templates WHERE id=?"+(lock?" FOR UPDATE":""),(r,n)->new Template(r.getLong("id"),r.getString("title"),CaseProfileService.Category.valueOf(r.getString("category")),r.getString("siabuc_version"),r.getString("body"),Status.valueOf(r.getString("status")),r.getInt("revision"),r.getTimestamp("updated_at").toInstant()),id);if(rows.isEmpty())throw ApiException.missing();return rows.get(0);}
 private Models.User staff(){Models.User actor=helpdesk.me();if(!List.of("ADMIN","TECH").contains(actor.role().name()))throw ApiException.forbidden();return actor;}
 private Models.User admin(){Models.User actor=helpdesk.me();if(actor.role()!=Models.Role.ADMIN)throw ApiException.forbidden();return actor;}
 private void revision(Integer value,Template old){if(value==null||value!=old.revision())throw ApiException.conflict("La plantilla cambió. Recárguela antes de guardar.");}
 private void validate(Input input){if(input==null||input.category()==null)throw ApiException.bad("Indique la categoría de la plantilla.");clean(input.title(),180,"Título",true);clean(input.body(),10000,"Contenido",true);clean(input.siabucVersion(),120,"Versión de SIABUC",false);}
 private String clean(String value,int max,String label,boolean required){String v=value==null?"":value.trim();if((required&&v.isEmpty())||v.length()>max||v.indexOf('\0')>=0)throw ApiException.bad(label+": "+(required?"de 1 a ":"máximo ")+max+" caracteres, sin caracteres nulos.");return v;}
 private String encode(Template value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 private Template decode(String value){try{return json.readValue(value,Template.class);}catch(Exception e){throw new IllegalStateException(e);}}
}
