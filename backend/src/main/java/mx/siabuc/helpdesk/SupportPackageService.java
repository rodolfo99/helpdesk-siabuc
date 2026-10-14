package mx.siabuc.helpdesk;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class SupportPackageService {
 public record Input(String code,String name,String description,BigDecimal amount,Long emailMinutes,Long remoteMinutes,Integer expectedRevision){}
 public record Active(Boolean active){}
 public record Version(long id,long packageId,String code,int revision,String name,String description,BigDecimal amount,String currency,long emailMinutes,long remoteMinutes,boolean active,String createdBy,Instant createdAt){}
 public record Detail(Version current,List<Version> versions){}
 private final JdbcTemplate db;private final HelpdeskService helpdesk;
 public SupportPackageService(JdbcTemplate db,HelpdeskService helpdesk){this.db=db;this.helpdesk=helpdesk;}
 private static final String SQL="SELECT v.*,p.code,p.active,u.username AS creator FROM support_package_versions v JOIN support_packages p ON p.id=v.package_id JOIN app_users u ON u.id=v.created_by";
 private static final RowMapper<Version> ROW=(r,n)->new Version(r.getLong("id"),r.getLong("package_id"),r.getString("code"),r.getInt("revision"),r.getString("name"),r.getString("description"),r.getBigDecimal("amount"),r.getString("currency"),r.getLong("email_minutes"),r.getLong("remote_minutes"),r.getBoolean("active"),r.getString("creator"),r.getTimestamp("created_at").toInstant());
 public List<Version> list(){actor();return db.query(SQL+" WHERE v.revision=p.current_revision ORDER BY p.code",ROW);}
 public Detail detail(long id){actor();return data(id);}
 private Detail data(long id){List<Version> rows=db.query(SQL+" WHERE p.id=? ORDER BY v.revision DESC",ROW,id);if(rows.isEmpty())throw ApiException.missing();return new Detail(rows.get(0),rows);}
 @Transactional public Detail create(Input i){Models.User a=actor();validate(i);String code=i.code().strip().toUpperCase(java.util.Locale.ROOT);if(db.queryForObject("SELECT COUNT(*) FROM support_packages WHERE code=?",Long.class,code)>0)throw ApiException.conflict("El código del paquete ya existe.");long id=db.queryForObject("INSERT INTO support_packages(code,created_by) VALUES(?,?) RETURNING id",Long.class,code,a.id());insert(id,1,i,a);audit(a,"CREATE_PACKAGE",id,"Paquete "+code+", revisión 1.");return data(id);}
 @Transactional public Detail update(long id,Input i){Models.User a=actor();validate(i);lock(id);Detail before=data(id);if(i.expectedRevision()==null||i.expectedRevision()!=before.current().revision())throw ApiException.conflict("El paquete cambió. Actualice y revise la nueva tarifa.");if(!before.current().code().equals(i.code().strip().toUpperCase(java.util.Locale.ROOT)))throw ApiException.conflict("El código del paquete no puede cambiarse.");int revision=before.current().revision()+1;insert(id,revision,i,a);db.update("UPDATE support_packages SET current_revision=?,updated_at=NOW() WHERE id=?",revision,id);audit(a,"UPDATE_PACKAGE",id,"Nueva revisión "+revision+". Las versiones usadas por cotizaciones se conservan.");return data(id);}
 @Transactional public Detail active(long id,Active i){Models.User a=actor();if(i==null||i.active()==null)throw ApiException.bad("Indique si el paquete está activo.");lock(id);db.update("UPDATE support_packages SET active=?,updated_at=NOW() WHERE id=?",i.active(),id);audit(a,"PACKAGE_ACTIVE",id,"Activo: "+i.active());return data(id);}
 /** Only immutable server-owned versions can be referenced. Retaining a draft's existing version is permitted. */
 Version forQuote(long versionId,Long retainedVersion){List<Long> ids=db.query("SELECT package_id FROM support_package_versions WHERE id=?",(r,n)->r.getLong(1),versionId);if(ids.isEmpty())throw ApiException.missing();lock(ids.get(0));Version v=db.queryForObject(SQL+" WHERE v.id=?",ROW,versionId);if(!Long.valueOf(versionId).equals(retainedVersion)){int current=db.queryForObject("SELECT current_revision FROM support_packages WHERE id=?",Integer.class,v.packageId());if(!v.active()||current!=v.revision())throw ApiException.conflict("La tarifa seleccionada cambió o fue desactivada. Actualice el catálogo y seleccione su versión vigente.");}return v;}
 Version snapshot(Long id){return id==null?null:db.queryForObject(SQL+" WHERE v.id=?",ROW,id);}
 private void lock(long id){if(db.query("SELECT id FROM support_packages WHERE id=? FOR UPDATE",(r,n)->r.getLong(1),id).isEmpty())throw ApiException.missing();}
 private void insert(long id,int revision,Input i,Models.User a){db.update("INSERT INTO support_package_versions(package_id,revision,name,description,amount,email_minutes,remote_minutes,created_by) VALUES(?,?,?,?,?,?,?,?)",id,revision,i.name().strip(),i.description().strip(),i.amount(),i.emailMinutes(),i.remoteMinutes(),a.id());}
 private void audit(Models.User a,String action,long id,String text){db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES(?,?,'SUPPORT_PACKAGE',?,?)",a.username(),action,id,text);}
 private Models.User actor(){Models.User a=helpdesk.me();if(a.role()!=Models.Role.ADMIN&&a.role()!=Models.Role.BILLING)throw ApiException.forbidden();return a;}
 private static void validate(Input i){if(i==null)throw ApiException.bad("Indique el paquete.");text(i.code(),"Código",60);if(!i.code().strip().matches("[A-Za-z0-9_-]+"))throw ApiException.bad("Use letras, números, guion o guion bajo en el código.");text(i.name(),"Nombre",180);text(i.description(),"Descripción",10000);amount(i.amount());if(i.emailMinutes()==null||i.remoteMinutes()==null||i.emailMinutes()<0||i.remoteMinutes()<0||i.emailMinutes()>1000000000L||i.remoteMinutes()>1000000000L||i.emailMinutes()+i.remoteMinutes()==0)throw ApiException.bad("Indique minutos enteros de soporte entre 0 y 1000000000, con al menos un canal positivo.");}
 static void amount(BigDecimal v){if(v==null||v.scale()>2||v.signum()<=0||v.compareTo(new BigDecimal("999999999.99"))>0)throw ApiException.bad("Importe positivo en MXN, máximo 999999999.99 y hasta dos decimales.");}
 static void text(String v,String name,int max){if(v==null||v.isBlank()||v.length()>max||v.indexOf('\0')>=0)throw ApiException.bad(name+" es obligatorio y admite hasta "+max+" caracteres.");}
}
