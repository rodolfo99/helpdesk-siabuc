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

/** Persisted stopwatch. Only an explicit, idempotent commit can consume support. */
@Service
public class WorkTimerService {
    private final JdbcTemplate db;
    private final HelpdeskService helpdesk;
    public WorkTimerService(JdbcTemplate db, HelpdeskService helpdesk) { this.db=db; this.helpdesk=helpdesk; }
    public record Timer(long id,long caseId,long adminId,String status,long elapsedSeconds,long suggestedMinutes,
                        Instant segmentStartedAt,Instant createdAt,Instant updatedAt,Long entryId,Long confirmedMinutes,String adjustmentReason) {}
    public record CommitInput(Long minutes,Channel channel,String description,Boolean internal,String adjustmentReason) {}
    private record Stored(long id,long caseId,long adminId,String status,long millis,Instant startedAt,Instant createdAt,Instant updatedAt,Long entryId,Long minutes,String reason) {}
    private final RowMapper<Stored> row = (r,n) -> new Stored(r.getLong("id"),r.getLong("case_id"),r.getLong("admin_id"),r.getString("status"),
        r.getLong("elapsed_millis"),r.getTimestamp("segment_started_at")==null?null:r.getTimestamp("segment_started_at").toInstant(),
        r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),r.getObject("entry_id",Long.class),r.getObject("confirmed_minutes",Long.class),r.getString("adjustment_reason"));

    public Timer current(long caseId) {
        User actor=admin(); helpdesk.caseDetail(caseId);
        List<Stored> rows=db.query("SELECT * FROM work_timers WHERE case_id=? AND admin_id=? ORDER BY id DESC LIMIT 1",row,caseId,actor.id());
        return rows.isEmpty()?null:view(rows.get(0));
    }
    @Transactional
    public Timer start(long caseId) {
        User actor=admin(); CaseDetail c=lockCase(caseId); requireOpen(c);
        // Serialize starts across different institutions/cases, before checking the partial unique index.
        db.queryForObject("SELECT id FROM app_users WHERE id=? FOR NO KEY UPDATE",Long.class,actor.id());
        List<Stored> existing=db.query("SELECT * FROM work_timers WHERE admin_id=? AND status IN ('RUNNING','PAUSED','FINISHED') FOR UPDATE",row,actor.id());
        if(!existing.isEmpty()) {
            if(existing.get(0).caseId()!=caseId) throw ApiException.conflict("Tiene un cronómetro pendiente en el caso #"+existing.get(0).caseId()+". Confirme o descarte esa atención primero.");
            return view(existing.get(0));
        }
        long id=db.queryForObject("INSERT INTO work_timers(case_id,admin_id,status,segment_started_at) VALUES (?,?,'RUNNING',?) RETURNING id",Long.class,caseId,actor.id(),Timestamp.from(Instant.now()));
        audit(actor,"START_TIMER",id,"Caso="+caseId);
        return view(own(id,actor,false));
    }
    @Transactional
    public Timer transition(long id,String action) {
        User actor=admin(); Stored initial=own(id,actor,false); CaseDetail c=lockCase(initial.caseId()); Stored timer=own(id,actor,true);
        String next=switch(action) { case "pause"->"PAUSED"; case "resume"->"RUNNING"; case "finish"->"FINISHED"; case "cancel"->"CANCELLED"; default->throw ApiException.bad("Acción de cronómetro inválida."); };
        if(timer.status().equals(next)) return view(timer);
        if(timer.status().equals("COMMITTED") || timer.status().equals("CANCELLED")) throw ApiException.conflict("El cronómetro ya terminó.");
        if(action.equals("resume")) {
            requireOpen(c);
            if(!timer.status().equals("PAUSED")) throw ApiException.conflict("Sólo se puede continuar un cronómetro pausado.");
        }
        if(action.equals("pause") && !timer.status().equals("RUNNING")) throw ApiException.conflict("Sólo se puede pausar un cronómetro en marcha.");
        if(action.equals("finish") && !List.of("RUNNING","PAUSED").contains(timer.status())) throw ApiException.conflict("El cronómetro ya está finalizado.");
        Instant now=Instant.now(); long millis=elapsed(timer,now);
        db.update("UPDATE work_timers SET status=?,elapsed_millis=?,segment_started_at=?,updated_at=NOW() WHERE id=?",next,millis,next.equals("RUNNING")?Timestamp.from(now):null,id);
        audit(actor,"TIMER_"+next,id,"Caso="+timer.caseId()+"; milisegundos="+millis);
        return view(own(id,actor,false));
    }
    @Transactional
    public Timer commit(long id,CommitInput input) {
        User actor=admin(); Stored initial=own(id,actor,false);
        // Same order as support accounting: institution -> case -> timer, including repeated commits.
        lockCase(initial.caseId()); Stored timer=own(id,actor,true);
        if(timer.status().equals("COMMITTED")) return view(timer);
        if(!timer.status().equals("FINISHED")) throw ApiException.conflict("Finalice el cronómetro antes de confirmar los minutos.");
        if(input.minutes()==null || input.minutes()<1 || input.minutes()>1000000000L || input.channel()==null || input.internal()==null)
            throw ApiException.bad("Indique minutos positivos, canal y visibilidad de la atención.");
        String reason=input.adjustmentReason()==null?"":input.adjustmentReason().trim();
        if(reason.length()>2000) throw ApiException.bad("El motivo admite hasta 2000 caracteres.");
        long suggested=suggested(timer.millis());
        if(input.minutes()!=suggested && reason.isBlank()) throw ApiException.bad("Indique un motivo para ajustar los minutos sugeridos por el cronómetro.");
        // Reserve room for the measured-time footer and a 2000-character adjustment reason.
        if(input.description()==null || input.description().isBlank() || input.description().length()>7500)
            throw ApiException.bad("La descripción de atención es obligatoria y admite hasta 7500 caracteres.");
        String description=input.description().trim()+"\nCronómetro #"+id+": "+(timer.millis()/1000)+" segundos; minutos confirmados: "+input.minutes()+"."
            +(reason.isBlank()?"":"\nMotivo del ajuste: "+reason);
        CaseDetail result=helpdesk.addEntry(timer.caseId(),new EntryInput(description,input.channel(),input.minutes(),input.internal()));
        long entryId=result.entries().stream().mapToLong(Entry::id).max().orElseThrow();
        db.update("UPDATE work_timers SET status='COMMITTED',entry_id=?,confirmed_minutes=?,adjustment_reason=?,updated_at=NOW() WHERE id=?",entryId,input.minutes(),reason,id);
        audit(actor,"COMMIT_TIMER",id,"Caso="+timer.caseId()+"; registro="+entryId+"; minutos="+input.minutes());
        return view(own(id,actor,false));
    }
    private User admin() { User actor=helpdesk.me(); if(!isSupportStaff(actor.role())) throw ApiException.forbidden(); return actor; }
    private Stored own(long id,User actor,boolean lock) {
        List<Stored> rows=db.query("SELECT * FROM work_timers WHERE id=? AND admin_id=?"+(lock?" FOR UPDATE":""),row,id,actor.id());
        if(rows.isEmpty()) throw ApiException.missing(); return rows.get(0);
    }
    private CaseDetail lockCase(long id) {
        CaseDetail initial=helpdesk.caseDetail(id);
        db.queryForObject("SELECT id FROM institutions WHERE id=? FOR UPDATE",Long.class,initial.institutionId());
        db.queryForObject("SELECT id FROM support_cases WHERE id=? FOR UPDATE",Long.class,id);
        return helpdesk.caseDetail(id);
    }
    private void requireOpen(CaseDetail c) {
        if(!c.status().canRunTimer()) throw ApiException.conflict("El cronómetro requiere un caso Recibido o En atención.");
        if(!Boolean.TRUE.equals(db.queryForObject("SELECT active FROM institutions WHERE id=?",Boolean.class,c.institutionId()))) throw ApiException.conflict("La institución está dada de baja.");
    }
    private long elapsed(Stored t,Instant now) { return t.millis()+(t.startedAt()==null?0:Math.max(0,Duration.between(t.startedAt(),now).toMillis())); }
    private long suggested(long millis) { return (millis+59999)/60000; }
    private Timer view(Stored t) { long millis=elapsed(t,Instant.now()); return new Timer(t.id(),t.caseId(),t.adminId(),t.status(),millis/1000,suggested(millis),t.startedAt(),t.createdAt(),t.updatedAt(),t.entryId(),t.minutes(),t.reason()); }
    private void audit(User actor,String action,long id,String detail) { db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,?,'TIMER',?,?)",actor.username(),action,id,detail); }
}
