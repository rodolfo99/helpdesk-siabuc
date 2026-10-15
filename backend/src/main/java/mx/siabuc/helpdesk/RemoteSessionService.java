package mx.siabuc.helpdesk;

import static mx.siabuc.helpdesk.Models.*;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Scheduling is independent of purchased support; an appointment never charges minutes. */
@Service
public class RemoteSessionService {
    private final JdbcTemplate db;
    private final HelpdeskService helpdesk;
    private final NotificationService notifications;
    public RemoteSessionService(JdbcTemplate db,HelpdeskService helpdesk,NotificationService notifications) { this.db=db; this.helpdesk=helpdesk; this.notifications=notifications; }
    public record Session(long id,long caseId,long adminId,String adminName,Instant startsAt,Instant endsAt,String note,String status,
                          String response,String respondedBy,Long replacesId,Instant createdAt,Instant updatedAt) {}
    public record Proposal(Instant startsAt,Instant endsAt,String note,Long adminId) {}
    public record Response(String response) {}
    private static final String SELECT="SELECT s.*,a.display_name AS admin_name,r.display_name AS respondent FROM remote_sessions s JOIN app_users a ON a.id=s.admin_id LEFT JOIN app_users r ON r.id=s.responded_by";
    private final RowMapper<Session> row=(r,n)->new Session(r.getLong("id"),r.getLong("case_id"),r.getLong("admin_id"),r.getString("admin_name"),
        r.getTimestamp("starts_at").toInstant(),r.getTimestamp("ends_at").toInstant(),r.getString("note"),r.getString("status"),r.getString("response"),
        r.getString("respondent"),r.getObject("replaces_id",Long.class),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());

    public List<Session> list(long caseId) {
        helpdesk.caseDetail(caseId);
        return db.query(SELECT+" WHERE s.case_id=? ORDER BY s.starts_at DESC,s.id DESC",row,caseId);
    }
    @Transactional
    public Session propose(long caseId,Proposal input) {
        User actor=admin(); CaseDetail c=lockCase(caseId); requireOpen(c);
        return create(c,input,actor,null);
    }
    @Transactional
    public Session reschedule(long id,Proposal input) {
        User actor=admin(); Session initial=find(id); CaseDetail c=lockCase(initial.caseId()); requireOpen(c);
        Session old=locked(id);
        requireManage(actor,old.adminId());
        if(!List.of("PROPOSED","CONFIRMED").contains(old.status())) throw ApiException.conflict("Sólo se pueden reprogramar sesiones pendientes.");
        Proposal replacement=new Proposal(input.startsAt(),input.endsAt(),input.note(),input.adminId()==null?old.adminId():input.adminId());
        // Keep the same order as propose: administrator before notification settings.
        requireManage(actor,replacement.adminId());
        lockAdmin(replacement.adminId());
        // Old confirmation is explicitly invalidated. All updates roll back if the new slot conflicts.
        db.update("UPDATE remote_sessions SET status='CANCELLED',response='Reprogramada; requiere una nueva confirmación.',responded_by=?,updated_at=NOW() WHERE id=?",actor.id(),id);
        history(old.caseId(),actor,"REMOTE_SESSION_CANCELLED","Sesión remota #"+id+" cancelada para reprogramar. La confirmación anterior deja de ser válida.");
        return create(c,replacement,actor,id);
    }
    @Transactional
    public Session respond(long id,String action,Response input) {
        User actor=helpdesk.me();
        boolean clientAction=action.equals("confirm")||action.equals("decline");
        if(clientAction ? actor.role()!=Role.USER : !isSupportStaff(actor.role())) throw ApiException.forbidden();
        Session initial=find(id); CaseDetail c=lockCase(initial.caseId()); Session session=locked(id);
        if(!clientAction) requireManage(actor,session.adminId());
        String next=switch(action) { case "confirm"->"CONFIRMED"; case "decline"->"DECLINED"; case "cancel"->"CANCELLED"; case "complete"->"COMPLETED"; default->throw ApiException.bad("Acción de agenda inválida."); };
        if(session.status().equals(next)) return session;
        if(clientAction) {
            requireOpen(c);
            if(!session.status().equals("PROPOSED")) throw ApiException.conflict("La propuesta ya fue respondida o sustituida. Actualice la agenda.");
            if(!session.startsAt().isAfter(Instant.now())) throw ApiException.conflict("La hora propuesta ya pasó. Solicite una nueva fecha al administrativo.");
        } else if(action.equals("complete")) {
            if(!session.status().equals("CONFIRMED")) throw ApiException.conflict("Sólo una sesión confirmada puede marcarse realizada.");
            if(session.startsAt().isAfter(Instant.now())) throw ApiException.conflict("La sesión todavía no ha comenzado.");
        } else if(!List.of("PROPOSED","CONFIRMED").contains(session.status())) throw ApiException.conflict("Esta sesión ya terminó.");
        String response=clean(input==null?null:input.response(),2000,"Respuesta",!action.equals("confirm"));
        db.update("UPDATE remote_sessions SET status=?,response=?,responded_by=?,updated_at=NOW() WHERE id=?",next,response,actor.id(),id);
        String label=switch(next) { case "CONFIRMED"->"confirmada por el cliente"; case "DECLINED"->"rechazada por el cliente"; case "CANCELLED"->"cancelada"; default->"realizada (sin descuento automático de minutos)"; };
        history(session.caseId(),actor,"REMOTE_SESSION_"+next,"Sesión remota #"+id+" "+label+". Administrativo: "+session.adminName()+". Inicio: "+session.startsAt()+"; fin: "+session.endsAt()+" (UTC)."+(response.isBlank()?"":"\n"+response));
        return find(id);
    }
    private Session create(CaseDetail c,Proposal input,User actor,Long replacesId) {
        if(input==null || input.startsAt()==null || input.endsAt()==null || !input.startsAt().isAfter(Instant.now()) || !input.endsAt().isAfter(input.startsAt()) || Duration.between(input.startsAt(),input.endsAt()).compareTo(Duration.ofHours(12))>0)
            throw ApiException.bad("Indique una sesión futura con inicio y fin válidos; duración máxima de 12 horas.");
        String note=clean(input.note(),5000,"Motivo de la sesión",true);
        long adminId=input.adminId()==null?actor.id():input.adminId();
        requireManage(actor,adminId);
        lockAdmin(adminId);
        // Administrative user row serializes conflict detection across cases and institutions.
        Long overlaps=db.queryForObject("SELECT count(*) FROM remote_sessions WHERE admin_id=? AND status IN ('PROPOSED','CONFIRMED') AND starts_at < ? AND ends_at > ?",Long.class,adminId,Timestamp.from(input.endsAt()),Timestamp.from(input.startsAt()));
        if(overlaps!=null && overlaps>0) throw ApiException.conflict("El administrativo ya tiene una sesión propuesta o confirmada en ese horario.");
        long id=db.queryForObject("INSERT INTO remote_sessions(case_id,admin_id,starts_at,ends_at,note,replaces_id,created_by) VALUES (?,?,?,?,?,?,?) RETURNING id",Long.class,c.id(),adminId,Timestamp.from(input.startsAt()),Timestamp.from(input.endsAt()),note,replacesId,actor.id());
        Session session=find(id);
        history(c.id(),actor,"REMOTE_SESSION_PROPOSED","Sesión remota #"+id+" propuesta. Administrativo: "+session.adminName()+". Inicio: "+input.startsAt()+"; fin: "+input.endsAt()+" (UTC).\n"+note+(replacesId==null?"":"\nSustituye la sesión #"+replacesId+"; pendiente de nueva confirmación."));
        return session;
    }
    private void requireManage(User actor,long responsibleId) {
        if(actor.role()==Role.TECH && actor.id()!=responsibleId) throw ApiException.forbidden();
    }
    private void lockAdmin(long adminId) {
        // NO KEY UPDATE serializes schedules without conflicting with actor foreign-key checks.
        List<Long> users=db.query("SELECT id FROM app_users WHERE id=? AND role IN ('ADMIN','TECH') AND active FOR NO KEY UPDATE",(r,n)->r.getLong(1),adminId);
        if(users.isEmpty()) throw ApiException.bad("Seleccione un administrador o técnico de soporte activo.");
    }
    private Session find(long id) { List<Session> rows=db.query(SELECT+" WHERE s.id=?",row,id); if(rows.isEmpty()) throw ApiException.missing(); return rows.get(0); }
    private Session locked(long id) { db.queryForObject("SELECT id FROM remote_sessions WHERE id=? FOR UPDATE",Long.class,id); return find(id); }
    private User admin() { User actor=helpdesk.me(); if(!isSupportStaff(actor.role())) throw ApiException.forbidden(); return actor; }
    private CaseDetail lockCase(long id) {
        CaseDetail initial=helpdesk.caseDetail(id);
        db.queryForObject("SELECT id FROM institutions WHERE id=? FOR UPDATE",Long.class,initial.institutionId());
        db.queryForObject("SELECT id FROM support_cases WHERE id=? FOR UPDATE",Long.class,id);
        return helpdesk.caseDetail(id);
    }
    private void requireOpen(CaseDetail c) {
        if(!c.status().active()) throw ApiException.conflict("El caso está resuelto o cerrado. Reábralo para programar o confirmar sesiones.");
        if(!Boolean.TRUE.equals(db.queryForObject("SELECT active FROM institutions WHERE id=?",Boolean.class,c.institutionId()))) throw ApiException.conflict("La institución está dada de baja.");
    }
    private String clean(String value,int max,String label,boolean required) {
        String result=value==null?"":value.trim();
        if((required&&result.isBlank())||result.length()>max) throw ApiException.bad(label+(required?" es obligatorio y":"")+" admite hasta "+max+" caracteres.");
        return result;
    }
    private void history(long caseId,User actor,String event,String description) {
        db.update("INSERT INTO case_entries(case_id,description,channel,minutes,internal,created_by) VALUES (?,?,NULL,0,FALSE,?)",caseId,description,actor.id());
        db.update("UPDATE support_cases SET updated_at=NOW() WHERE id=?",caseId);
        db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,?,'CASE',?,?)",actor.username(),event,caseId,description);
        notifications.caseEvent(caseId,event,description,false,actor.username());
    }
}
