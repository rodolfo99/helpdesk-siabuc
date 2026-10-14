package mx.siabuc.helpdesk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class SavedViewService {
 public record Filters(String search,String status,String assignment,Long institutionId,String category,String priority) {}
 public record Input(String name,JsonNode filters,Integer expectedRevision) {}
 public record View(long id,String name,Filters filters,int revision,Instant updatedAt) {}
 private final JdbcTemplate db;private final HelpdeskService helpdesk;private final ObjectMapper json;
 private static final Set<String> KEYS=Set.of("search","status","assignment","institutionId","category","priority");
 public SavedViewService(JdbcTemplate db,HelpdeskService helpdesk,ObjectMapper json){this.db=db;this.helpdesk=helpdesk;this.json=json;}
 @Transactional(readOnly=true) public List<View> list(){
  Models.User actor=actor();List<View> result=new ArrayList<>();
  for(View value:db.query("SELECT * FROM saved_case_views WHERE owner_id=? ORDER BY lower(name),id",(r,n)->row(r),actor.id())){
   // A previous institution/role does not survive an account transfer as a usable saved filter.
   if(allowedScope(value.filters(),actor)) result.add(value);
  }return result;
 }
 public View get(long id){Models.User actor=actor();View value=owned(id,actor,false);if(!allowedScope(value.filters(),actor))throw ApiException.missing();return value;}
 @Transactional public View create(Input input){
  Models.User actor=actor();String name=name(input);Filters filters=filters(input.filters(),actor);
  db.queryForObject("SELECT id FROM app_users WHERE id=? FOR UPDATE",Long.class,actor.id());
  if(db.queryForObject("SELECT COUNT(*) FROM saved_case_views WHERE owner_id=?",Long.class,actor.id())>=50)throw ApiException.conflict("Puede guardar hasta 50 vistas personales.");
  try {long id=db.queryForObject("INSERT INTO saved_case_views(owner_id,name,filters) VALUES (?,?,?::jsonb) RETURNING id",Long.class,actor.id(),name,encode(filters));return owned(id,actor,false);}
  catch(DuplicateKeyException e){throw ApiException.conflict("Ya tiene una vista guardada con ese nombre.");}
 }
 @Transactional public View update(long id,Input input){
  Models.User actor=actor();View old=owned(id,actor,true);String name=name(input);Filters filters=filters(input.filters(),actor);
  if(input.expectedRevision()==null||input.expectedRevision()!=old.revision())throw ApiException.conflict("La vista cambió. Recárguela antes de guardar.");
  try {db.update("UPDATE saved_case_views SET name=?,filters=?::jsonb,revision=revision+1,updated_at=NOW() WHERE id=? AND owner_id=?",name,encode(filters),id,actor.id());}
  catch(DuplicateKeyException e){throw ApiException.conflict("Ya tiene una vista guardada con ese nombre.");}
  return owned(id,actor,false);
 }
 @Transactional public void delete(long id){Models.User actor=actor();owned(id,actor,true);db.update("DELETE FROM saved_case_views WHERE id=? AND owner_id=?",id,actor.id());}
 private View owned(long id,Models.User actor,boolean lock){var rows=db.query("SELECT * FROM saved_case_views WHERE id=? AND owner_id=?"+(lock?" FOR UPDATE":""),(r,n)->row(r),id,actor.id());if(rows.isEmpty())throw ApiException.missing();return rows.get(0);}
 private View row(java.sql.ResultSet r)throws java.sql.SQLException {try{return new View(r.getLong("id"),r.getString("name"),json.readValue(r.getString("filters"),Filters.class),r.getInt("revision"),r.getTimestamp("updated_at").toInstant());}catch(java.io.IOException e){throw new IllegalStateException("Vista almacenada inválida",e);}}
 private Models.User actor(){Models.User actor=helpdesk.me();if(!Set.of("ADMIN","TECH","USER").contains(actor.role().name()))throw ApiException.forbidden();return actor;}
 private boolean allowedScope(Filters f,Models.User actor){return actor.role()!=Models.Role.USER || ("all".equals(f.assignment())&&(f.institutionId()==null||Objects.equals(f.institutionId(),actor.institutionId())));}
 private String name(Input input){if(input==null||input.name()==null||input.name().isBlank()||input.name().length()>100||input.name().indexOf('\0')>=0)throw ApiException.bad("Escriba un nombre de vista de 1 a 100 caracteres.");return input.name().trim();}
 private Filters filters(JsonNode value,Models.User actor){
  if(value==null||!value.isObject())throw ApiException.bad("Indique los filtros de la vista.");
  value.fieldNames().forEachRemaining(key->{if(!KEYS.contains(key))throw ApiException.bad("Filtro no permitido: "+key);});
  String search=text(value,"search","",300),status=choice(value,"status",Set.of("all","OPEN","CLOSED")),assignment=choice(value,"assignment",Set.of("all","mine","unassigned"));
  String category=choice(value,"category",Set.of("all","INSTALLATION","CATALOGING","LOANS","REPORTS","IMPORT","OTHER")),priority=choice(value,"priority",Set.of("all","LOW","NORMAL","HIGH","URGENT"));
  Long institution=null;JsonNode raw=value.get("institutionId");
  if(raw!=null&&!raw.isNull()){if(!raw.isIntegralNumber()||!raw.canConvertToLong()||raw.longValue()<=0)throw ApiException.bad("La institución debe ser un identificador válido.");institution=raw.longValue();}
  Filters f=new Filters(search,status,assignment,institution,category,priority);
  if(!allowedScope(f,actor))throw ApiException.forbidden();
  if(institution!=null&&db.queryForList("SELECT id FROM institutions WHERE id=?",Long.class,institution).isEmpty())throw ApiException.bad("La institución indicada no existe.");
  return f;
 }
 private String choice(JsonNode value,String key,Set<String> allowed){String result=text(value,key,"all",40);if(!allowed.contains(result))throw ApiException.bad("Valor no permitido para "+key+".");return result;}
 private String text(JsonNode value,String key,String fallback,int max){JsonNode node=value.get(key);if(node==null||node.isNull())return fallback;if(!node.isTextual()||node.textValue().length()>max||node.textValue().indexOf('\0')>=0)throw ApiException.bad("Valor inválido para "+key+".");return node.textValue().trim();}
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
}
