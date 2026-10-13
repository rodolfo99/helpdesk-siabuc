package mx.siabuc.helpdesk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persistence and SLA calculation. Authorization lives in CaseProfileController. */
@Service
public class CaseProfileService {
    public enum Category { INSTALLATION,CATALOGING,LOANS,REPORTS,IMPORT,OTHER }
    public enum Priority { LOW,NORMAL,HIGH,URGENT }
    public record Goal(Integer firstResponseMinutes,Integer resolutionMinutes) {}
    public record Settings(String timezone,List<Integer> workDays,String startTime,String endTime,
            List<LocalDate> holidays,Map<Priority,Goal> goals) {}
    public record ProfileInput(Category category,Priority priority,String siabucVersion,String operatingSystem,
            String errorMessage,String reproductionSteps) {}
    public record Summary(long caseId,Category category,Priority priority) {}
    public record Sla(String timezone,Instant firstResponseDueAt,Instant firstRespondedAt,Instant resolutionDueAt,
            Instant resolvedAt,String firstResponseState,String resolutionState,int firstResponseMinutes,
            int resolutionMinutes,boolean legacy) {}
    public record Profile(long caseId,Category category,Priority priority,String siabucVersion,String operatingSystem,
            String errorMessage,String reproductionSteps,Sla sla) {}
    private record Row(long id,Category category,Priority priority,String version,String os,String error,String steps,
            Settings settings,Instant opened,Instant resolutionStarted,Instant responded,Instant resolved,boolean legacy,String status) {}
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public CaseProfileService(JdbcTemplate db,ObjectMapper json) { this.db=db;this.json=json; }

    public Settings settings() { return decode(db.queryForObject("SELECT configuration FROM support_settings WHERE id=1",String.class)); }
    @Transactional
    public Settings updateSettings(Settings input,String actor) {
        validate(input);
        db.queryForObject("SELECT id FROM support_settings WHERE id=1 FOR UPDATE",Integer.class);
        Settings before=settings();
        db.update("UPDATE support_settings SET configuration=?,updated_at=NOW() WHERE id=1",encode(input));
        audit(actor,"UPDATE_SUPPORT_SETTINGS",null,"Configuración anterior: "+encode(before)+"; nueva: "+encode(input));
        return settings();
    }
    public List<Summary> summaries(Models.User actor) {
        String sql="SELECT p.case_id,p.category,p.priority FROM case_profiles p JOIN support_cases c ON c.id=p.case_id";
        var mapper=(org.springframework.jdbc.core.RowMapper<Summary>)(r,n)->new Summary(r.getLong(1),Category.valueOf(r.getString(2)),Priority.valueOf(r.getString(3)));
        return actor.role()==Models.Role.ADMIN ? db.query(sql+" ORDER BY p.case_id",mapper)
            : db.query(sql+" WHERE c.institution_id=? ORDER BY p.case_id",mapper,actor.institutionId());
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void initialize(long caseId) {
        String snapshot=encode(settings());
        db.update("""
            INSERT INTO case_profiles(case_id,calendar_snapshot,opened_at,resolution_started_at)
            SELECT id,?,created_at,created_at FROM support_cases WHERE id=? ON CONFLICT(case_id) DO NOTHING
            """,snapshot,caseId);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void initialize(long caseId,ProfileInput input) {
        initialize(caseId);
        if(input==null) return;
        validate(input);
        db.update("UPDATE case_profiles SET category=?,priority=?,siabuc_version=?,operating_system=?,error_message=?,reproduction_steps=? WHERE case_id=?",
            input.category().name(),input.priority().name(),clean(input.siabucVersion()),clean(input.operatingSystem()),clean(input.errorMessage()),clean(input.reproductionSteps()),caseId);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void recordPublicResponse(long caseId,long entryId) {
        // The SQL guard also protects accidental calls for client/internal entries.
        db.update("""
            UPDATE case_profiles p SET first_responded_at=e.created_at,updated_at=NOW()
            FROM case_entries e JOIN app_users u ON u.id=e.created_by
            WHERE p.case_id=? AND e.id=? AND e.case_id=p.case_id AND NOT e.internal AND u.role='ADMIN'
              AND p.first_responded_at IS NULL
            """,caseId,entryId);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void recordStatus(long caseId,Models.CaseStatus status) {
        if(status==Models.CaseStatus.CLOSED)
            db.update("UPDATE case_profiles SET resolved_at=NOW(),updated_at=NOW() WHERE case_id=?",caseId);
        else db.update("UPDATE case_profiles SET resolved_at=NULL,resolution_started_at=NOW(),updated_at=NOW() WHERE case_id=?",caseId);
    }
    public Profile profile(long caseId) { return view(row(caseId)); }
    @Transactional
    public Profile update(long caseId,ProfileInput input,Models.User actor) {
        validate(input);
        // Match the application order: institution -> case -> profile. No profile path locks a case afterwards.
        Long institutionId=db.queryForObject("SELECT institution_id FROM support_cases WHERE id=?",Long.class,caseId);
        Boolean active=db.queryForObject("SELECT active FROM institutions WHERE id=? FOR UPDATE",Boolean.class,institutionId);
        db.queryForObject("SELECT id FROM support_cases WHERE id=? FOR UPDATE",Long.class,caseId);
        if(!Boolean.TRUE.equals(active)) throw ApiException.conflict("La institución está dada de baja.");
        Row before=row(caseId);
        if(!"OPEN".equals(before.status())) throw ApiException.conflict("Reabra el caso para modificar su ficha técnica.");
        db.update("""
            UPDATE case_profiles SET category=?,priority=?,siabuc_version=?,operating_system=?,error_message=?,reproduction_steps=?,updated_at=NOW()
            WHERE case_id=?
            """,input.category().name(),input.priority().name(),clean(input.siabucVersion()),clean(input.operatingSystem()),
            clean(input.errorMessage()),clean(input.reproductionSteps()),caseId);
        db.update("UPDATE support_cases SET updated_at=NOW() WHERE id=?",caseId);
        audit(actor.username(),"UPDATE_CASE_PROFILE",caseId,"Antes: "+encode(new ProfileInput(before.category(),before.priority(),before.version(),before.os(),before.error(),before.steps()))
            +"; después: "+encode(input));
        return profile(caseId);
    }
    private Row row(long id) {
        var rows=db.query("SELECT p.*,c.status FROM case_profiles p JOIN support_cases c ON c.id=p.case_id WHERE p.case_id=?",(r,n)->new Row(
            r.getLong("case_id"),Category.valueOf(r.getString("category")),Priority.valueOf(r.getString("priority")),r.getString("siabuc_version"),
            r.getString("operating_system"),r.getString("error_message"),r.getString("reproduction_steps"),decode(r.getString("calendar_snapshot")),
            instant(r.getTimestamp("opened_at")),instant(r.getTimestamp("resolution_started_at")),instant(r.getTimestamp("first_responded_at")),
            instant(r.getTimestamp("resolved_at")),r.getBoolean("legacy"),r.getString("status")),id);
        if(rows.isEmpty()) throw ApiException.missing(); return rows.get(0);
    }
    private Profile view(Row r) {
        Goal goal=r.settings().goals().get(r.priority());
        Instant responseDue=deadline(r.opened(),goal.firstResponseMinutes(),r.settings());
        Instant resolutionDue=deadline(r.resolutionStarted(),goal.resolutionMinutes(),r.settings());
        Instant now=Instant.now(); boolean closed="CLOSED".equals(r.status());
        String responseState=state(responseDue,r.responded(),now,closed);
        String resolutionState=state(resolutionDue,r.resolved(),now,closed);
        return new Profile(r.id(),r.category(),r.priority(),r.version(),r.os(),r.error(),r.steps(),new Sla(r.settings().timezone(),responseDue,
            r.responded(),resolutionDue,r.resolved(),responseState,resolutionState,goal.firstResponseMinutes(),goal.resolutionMinutes(),r.legacy()));
    }
    private String state(Instant due,Instant completed,Instant now,boolean closed) {
        if(completed!=null) return completed.isAfter(due)?"BREACHED":"MET";
        if(closed) return "UNKNOWN";
        return now.isAfter(due)?"OVERDUE":"PENDING";
    }
    static Instant deadline(Instant from,int minutes,Settings settings) {
        return BusinessHours.addMinutes(from,minutes,ZoneId.of(settings.timezone()),Set.copyOf(settings.workDays()),
            LocalTime.parse(settings.startTime()),LocalTime.parse(settings.endTime()),Set.copyOf(settings.holidays()));
    }
    private static void validate(ProfileInput p) {
        if(p==null || p.category()==null || p.priority()==null) throw ApiException.bad("Indique categoría y prioridad.");
        bounded(p.siabucVersion(),120,"Versión de SIABUC");bounded(p.operatingSystem(),180,"Sistema operativo");
        bounded(p.errorMessage(),10000,"Mensaje de error");bounded(p.reproductionSteps(),10000,"Pasos para reproducir");
    }
    private static void validate(Settings s) {
        if(s==null) throw ApiException.bad("Indique la configuración de atención.");
        try { ZoneId.of(s.timezone()); } catch(Exception e) { throw ApiException.bad("Indique una zona horaria válida, por ejemplo America/Mexico_City."); }
        if(s.workDays()==null || s.workDays().isEmpty() || s.workDays().size()>7 || new HashSet<>(s.workDays()).size()!=s.workDays().size()
                || s.workDays().stream().anyMatch(d->d==null || d<1 || d>7)) throw ApiException.bad("Seleccione días laborales sin repetir, del 1 (lunes) al 7 (domingo).");
        try {
            if(s.startTime()==null || s.endTime()==null || !s.startTime().matches("\\d{2}:\\d{2}") || !s.endTime().matches("\\d{2}:\\d{2}")) throw new IllegalArgumentException();
            LocalTime start=LocalTime.parse(s.startTime()),end=LocalTime.parse(s.endTime());
            if(Duration.between(start,end).toMinutes()<30) throw new IllegalArgumentException();
        } catch(Exception e) { throw ApiException.bad("El horario debe usar HH:mm y cerrar al menos 30 minutos después de abrir, dentro del mismo día."); }
        if(s.holidays()==null || s.holidays().size()>3660 || s.holidays().stream().anyMatch(d->d==null || d.getYear()<1900 || d.getYear()>2200)
                || new HashSet<>(s.holidays()).size()!=s.holidays().size()) throw ApiException.bad("Indique hasta 3660 fechas inhábiles únicas entre 1900 y 2200.");
        if(s.goals()==null || s.goals().size()!=Priority.values().length) throw ApiException.bad("Configure los objetivos de las cuatro prioridades.");
        for(Priority p:Priority.values()) {
            Goal g=s.goals().get(p);
            if(g==null || g.firstResponseMinutes()==null || g.resolutionMinutes()==null || g.firstResponseMinutes()<1 || g.firstResponseMinutes()>43200
                    || g.resolutionMinutes()<g.firstResponseMinutes() || g.resolutionMinutes()>43200)
                throw ApiException.bad("Cada objetivo debe estar entre 1 y 43200 minutos laborales; resolución debe ser igual o mayor que primera respuesta.");
        }
    }
    private static void bounded(String value,int max,String label) {
        if(value!=null && (value.length()>max || value.indexOf('\0')>=0)) throw ApiException.bad(label+": máximo "+max+" caracteres, sin caracteres nulos.");
    }
    private static String clean(String value) { return value==null?"":value.trim(); }
    private static Instant instant(Timestamp value) { return value==null?null:value.toInstant(); }
    private Settings decode(String value) {
        try { return json.readValue(value,Settings.class); } catch(JsonProcessingException e) { throw new IllegalStateException("Configuración SLA almacenada inválida",e); }
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch(JsonProcessingException e) { throw new IllegalStateException("No se pudo serializar la configuración SLA",e); }
    }
    private void audit(String actor,String action,Long caseId,String details) {
        db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,?,?,?,?)",actor,action,caseId==null?"SUPPORT_SETTINGS":"CASE",caseId,details);
    }
}
