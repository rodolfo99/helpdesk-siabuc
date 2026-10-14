package mx.siabuc.helpdesk;

import static mx.siabuc.helpdesk.Models.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {
    public enum Status { OPEN, IN_PROGRESS, RESOLVED }
    public record Incident(long id,String title,String description,Status status,int revision,String publicTitle,
        String publicSummary,Status publicStatus,boolean published,Instant publishedAt,Instant createdAt,Instant updatedAt) {}
    public record Summary(long id,String title,Status status,int revision,boolean published,Instant updatedAt) {}
    public record Page(List<Summary> items,long total,int page,int pageSize) {}
    public record Input(String title,String description,Status status,Integer expectedRevision,String note) {}
    public record PublicationInput(Boolean published,String publicTitle,String publicSummary,Integer expectedRevision) {}
    public record ProgressInput(String note,Integer expectedRevision) {}
    public record LinkInput(Long incidentId) {}
    public record CaseInput(Long caseId) {}
    public record CaseOption(long id,long institutionId,String institutionName,String title,String status) {}
    public record StaffLink(long incidentId,String title,Status status,boolean published,Instant linkedAt) {}
    // Explicit allowlist: no incident ID, staff/internal text, other cases, institutions or counts.
    public record PublicLink(String title,String summary,Status status,Instant publishedAt) {}
    public record History(long id,String action,int revision,String title,String description,Status status,
        String publicTitle,String publicSummary,Status publicStatus,boolean published,String note,Long caseId,String actor,Instant createdAt) {}
    public record Detail(Incident incident,List<CaseOption> cases,List<History> history) {}
    private record Scope(long institutionId,boolean active) {}
    private final JdbcTemplate db;
    private final HelpdeskService helpdesk;
    public IncidentService(JdbcTemplate db,HelpdeskService helpdesk){this.db=db;this.helpdesk=helpdesk;}
    private static Instant time(ResultSet r,String name)throws SQLException{return r.getTimestamp(name)==null?null:r.getTimestamp(name).toInstant();}
    private static Status status(String value){return value==null?null:Status.valueOf(value);}
    private static final RowMapper<Incident> INCIDENT=(r,n)->new Incident(r.getLong("id"),r.getString("title"),r.getString("description"),status(r.getString("status")),
        r.getInt("revision"),r.getString("public_title"),r.getString("public_summary"),status(r.getString("public_status")),r.getBoolean("published"),time(r,"published_at"),time(r,"created_at"),time(r,"updated_at"));
    private static final RowMapper<CaseOption> CASE=(r,n)->new CaseOption(r.getLong("id"),r.getLong("institution_id"),r.getString("institution_name"),r.getString("title"),r.getString("status"));

    public Page list(String q,Status status,int page,int pageSize){
        staff();if(page<1||page>100000||pageSize<1||pageSize>100)throw ApiException.bad("La página debe ser positiva y el tamaño entre 1 y 100.");
        String query=text(q,"Búsqueda",200,false);StringBuilder where=new StringBuilder(" WHERE 1=1");List<Object> args=new ArrayList<>();
        if(status!=null){where.append(" AND status=?");args.add(status.name());}
        if(!query.isEmpty()){where.append(" AND (POSITION(LOWER(?) IN LOWER(title))>0 OR POSITION(LOWER(?) IN LOWER(description))>0)");args.add(query);args.add(query);}
        long total=db.queryForObject("SELECT COUNT(*) FROM support_incidents"+where,Long.class,args.toArray());args.add(pageSize);args.add((page-1)*pageSize);
        List<Summary> items=db.query("SELECT * FROM support_incidents"+where+" ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?",(r,n)->new Summary(r.getLong("id"),r.getString("title"),status(r.getString("status")),r.getInt("revision"),r.getBoolean("published"),time(r,"updated_at")),args.toArray());
        return new Page(items,total,page,pageSize);
    }
    public Detail get(long id){staff();return detail(find(id,false));}
    @Transactional public Detail create(Input input){
        User actor=staff();Input value=validate(input);
        long id=db.queryForObject("INSERT INTO support_incidents(title,description,status,created_by,updated_by) VALUES (?,?,?,?,?) RETURNING id",Long.class,value.title(),value.description(),value.status().name(),actor.id(),actor.id());
        Incident result=find(id,false);record(result,"CREATE",value.note(),null,actor);return detail(result);
    }
    @Transactional public Detail update(long id,Input input){
        User actor=staff();Incident current=find(id,true);Input value=validate(input);revision(current,input.expectedRevision());
        db.update("UPDATE support_incidents SET title=?,description=?,status=?,revision=revision+1,updated_by=?,updated_at=NOW() WHERE id=?",value.title(),value.description(),value.status().name(),actor.id(),id);
        Incident result=find(id,false);record(result,"EDIT",value.note(),null,actor);return detail(result);
    }
    @Transactional public Detail progress(long id,ProgressInput input){
        User actor=staff();Incident current=find(id,true);if(input==null)throw ApiException.bad("Indique el avance.");revision(current,input.expectedRevision());
        String note=text(input.note(),"Avance interno",5000,true);
        db.update("UPDATE support_incidents SET revision=revision+1,updated_by=?,updated_at=NOW() WHERE id=?",actor.id(),id);
        Incident result=find(id,false);record(result,"PROGRESS",note,null,actor);return detail(result);
    }
    @Transactional public Detail publication(long id,PublicationInput input){
        User actor=staff();Incident current=find(id,true);if(input==null||input.published()==null)throw ApiException.bad("Indique si desea publicar el resumen.");revision(current,input.expectedRevision());
        if(input.published()){
            String title=text(input.publicTitle(),"Título público",220,true),summary=text(input.publicSummary(),"Resumen público",10000,true);
            db.update("UPDATE support_incidents SET public_title=?,public_summary=?,public_status=status,published=TRUE,published_at=NOW(),revision=revision+1,updated_by=?,updated_at=NOW() WHERE id=?",title,summary,actor.id(),id);
        }else{
            if(!current.published())return detail(current);
            db.update("UPDATE support_incidents SET published=FALSE,revision=revision+1,updated_by=?,updated_at=NOW() WHERE id=?",actor.id(),id);
        }
        Incident result=find(id,false);record(result,input.published()?"PUBLISH":"UNPUBLISH","",null,actor);return detail(result);
    }
    public List<CaseOption> caseOptions(long id,String q){
        staff();find(id,false);String query=text(q,"Búsqueda de caso",200,true);
        if(query.length()<2&&!query.matches("[1-9][0-9]*"))throw ApiException.bad("Escriba al menos dos caracteres o un número de caso.");
        return db.query("""
            SELECT c.id,c.institution_id,i.name AS institution_name,c.title,c.status
            FROM support_cases c JOIN institutions i ON i.id=c.institution_id WHERE i.active
            AND NOT EXISTS(SELECT 1 FROM incident_case_links l WHERE l.incident_id=? AND l.case_id=c.id AND l.active)
            AND (CAST(c.id AS VARCHAR)=? OR POSITION(LOWER(?) IN LOWER(c.title))>0 OR POSITION(LOWER(?) IN LOWER(i.name))>0)
            ORDER BY c.updated_at DESC,c.id DESC LIMIT 20
            """,CASE,id,query,query,query);
    }
    public List<?> links(long caseId){
        User actor=reader();scope(caseId,actor,false);
        if(actor.role()==Role.USER)return db.query("""
            SELECT s.public_title,s.public_summary,s.public_status,s.published_at FROM incident_case_links l
            JOIN support_incidents s ON s.id=l.incident_id WHERE l.case_id=? AND l.active AND s.published
            ORDER BY s.published_at DESC,s.id DESC
            """,(r,n)->new PublicLink(r.getString("public_title"),r.getString("public_summary"),status(r.getString("public_status")),time(r,"published_at")),caseId);
        return staffLinks(caseId);
    }
    @Transactional public List<StaffLink> linkCase(long caseId,LinkInput input){
        User actor=staff();if(input==null||input.incidentId()==null||input.incidentId()<1)throw ApiException.bad("Seleccione una incidencia.");
        mutateLink(input.incidentId(),caseId,true,actor);return staffLinks(caseId);
    }
    @Transactional public List<StaffLink> unlinkCase(long caseId,long incidentId){User actor=staff();mutateLink(incidentId,caseId,false,actor);return staffLinks(caseId);}
    @Transactional public Detail link(long id,CaseInput input){
        User actor=staff();if(input==null||input.caseId()==null||input.caseId()<1)throw ApiException.bad("Seleccione un caso.");
        mutateLink(id,input.caseId(),true,actor);return detail(find(id,false));
    }
    @Transactional public Detail unlink(long id,long caseId){User actor=staff();mutateLink(id,caseId,false,actor);return detail(find(id,false));}
    private void mutateLink(long id,long caseId,boolean add,User actor){
        Scope scope=scope(caseId,actor,true);if(!scope.active())throw ApiException.conflict("La institución está dada de baja.");
        Incident incident=find(id,true);int changed;
        if(add)changed=db.update("""
            INSERT INTO incident_case_links(incident_id,case_id,linked_by) VALUES (?,?,?)
            ON CONFLICT(incident_id,case_id) DO UPDATE SET active=TRUE,linked_by=EXCLUDED.linked_by,linked_at=NOW(),removed_by=NULL,removed_at=NULL
            WHERE NOT incident_case_links.active
            """,id,caseId,actor.id());
        else changed=db.update("UPDATE incident_case_links SET active=FALSE,removed_by=?,removed_at=NOW() WHERE incident_id=? AND case_id=? AND active",actor.id(),id,caseId);
        if(changed>0)record(incident,add?"LINK":"UNLINK","",caseId,actor);
    }
    private List<StaffLink> staffLinks(long caseId){return db.query("""
        SELECT s.id,s.title,s.status,s.published,l.linked_at FROM incident_case_links l JOIN support_incidents s ON s.id=l.incident_id
        WHERE l.case_id=? AND l.active ORDER BY l.linked_at DESC,s.id DESC
        """,(r,n)->new StaffLink(r.getLong("id"),r.getString("title"),status(r.getString("status")),r.getBoolean("published"),time(r,"linked_at")),caseId);}
    private Detail detail(Incident incident){
        List<CaseOption> cases=db.query("""
            SELECT c.id,c.institution_id,i.name AS institution_name,c.title,c.status FROM incident_case_links l
            JOIN support_cases c ON c.id=l.case_id JOIN institutions i ON i.id=c.institution_id
            WHERE l.incident_id=? AND l.active ORDER BY l.linked_at DESC,c.id DESC
            """,CASE,incident.id());
        List<History> history=db.query("""
            SELECT h.*,u.display_name AS actor_name FROM incident_history h JOIN app_users u ON u.id=h.actor WHERE h.incident_id=? ORDER BY h.id DESC
            """,(r,n)->new History(r.getLong("id"),r.getString("action"),r.getInt("revision"),r.getString("title"),r.getString("description"),status(r.getString("status")),
            r.getString("public_title"),r.getString("public_summary"),status(r.getString("public_status")),r.getBoolean("published"),r.getString("note"),r.getObject("case_id",Long.class),r.getString("actor_name"),time(r,"created_at")),incident.id());
        return new Detail(incident,cases,history);
    }
    private Incident find(long id,boolean lock){List<Incident> values=db.query("SELECT * FROM support_incidents WHERE id=?"+(lock?" FOR UPDATE":""),INCIDENT,id);if(values.isEmpty())throw ApiException.missing();return values.get(0);}
    private Scope scope(long caseId,User actor,boolean lock){
        List<Scope> values=db.query("SELECT c.institution_id,i.active FROM support_cases c JOIN institutions i ON i.id=c.institution_id WHERE c.id=?"+(lock?" FOR UPDATE OF c":""),(r,n)->new Scope(r.getLong("institution_id"),r.getBoolean("active")),caseId);
        if(values.isEmpty()||(actor.role()==Role.USER&&!Objects.equals(actor.institutionId(),values.get(0).institutionId())))throw ApiException.missing();return values.get(0);
    }
    private User reader(){User user=helpdesk.me();if(user.role()!=Role.ADMIN&&user.role()!=Role.TECH&&user.role()!=Role.USER)throw ApiException.forbidden();return user;}
    private User staff(){User user=helpdesk.me();if(user.role()!=Role.ADMIN&&user.role()!=Role.TECH)throw ApiException.forbidden();return user;}
    private static Input validate(Input input){if(input==null||input.status()==null)throw ApiException.bad("Indique el estado de la incidencia.");return new Input(text(input.title(),"Título interno",220,true),text(input.description(),"Descripción interna",20000,true),input.status(),input.expectedRevision(),text(input.note(),"Nota interna",5000,false));}
    private static void revision(Incident incident,Integer expected){if(expected==null||expected<1)throw ApiException.bad("Indique la revisión que está editando.");if(expected!=incident.revision())throw ApiException.conflict("La incidencia cambió. Recargue su versión actual antes de guardar.");}
    private static String text(String value,String label,int max,boolean required){String result=value==null?"":value.strip();if((required&&result.isEmpty())||result.length()>max||result.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))throw ApiException.bad(label+": indique "+(required?"entre 1 y ":"hasta ")+max+" caracteres de texto.");return result;}
    private void record(Incident i,String action,String note,Long caseId,User actor){
        db.update("""
            INSERT INTO incident_history(incident_id,action,revision,title,description,status,public_title,public_summary,public_status,published,note,case_id,actor)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,i.id(),action,i.revision(),i.title(),i.description(),i.status().name(),i.publicTitle(),i.publicSummary(),i.publicStatus()==null?null:i.publicStatus().name(),i.published(),note,caseId,actor.id());
        db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,?,'INCIDENT',?,?)",actor.username(),"INCIDENT_"+action,i.id(),"Revisión "+i.revision()+(caseId==null?"":"; caso #"+caseId)+"; sin descuento de minutos ni cambios automáticos de casos.");
    }
}
