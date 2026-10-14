package mx.siabuc.helpdesk;

import static mx.siabuc.helpdesk.Models.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SatisfactionService {
    private final JdbcTemplate db; private final HelpdeskService helpdesk; private final ZoneId zone;
    public SatisfactionService(JdbcTemplate db,HelpdeskService helpdesk,@Value("${app.reporting-time-zone:America/Mexico_City}")String timeZone) {
        this.db=db;this.helpdesk=helpdesk;this.zone=ZoneId.of(timeZone);
    }
    public record Input(String requestId,Integer score,String comment) {}
    public record Survey(long id,long caseId,long institutionId,String institutionName,String caseTitle,long respondentId,
        String respondentUsername,String respondentName,Long administratorId,String administratorUsername,String administratorName,
        int score,String comment,Instant createdAt) {}
    public record View(long caseId,CaseStatus caseStatus,boolean canRespond,Survey survey) {}
    public record Filters(LocalDate from,LocalDate to,Long institutionId,Long administratorId) {}
    public record Distribution(int score,long samples) {}
    public record Group(Long administratorId,String administratorName,long samples,long scoreSum,BigDecimal averageScore) {}
    public record Report(Instant generatedAt,String timeZone,Filters filters,long samples,long scoreSum,BigDecimal averageScore,
                         List<Distribution> distribution,List<Group> administrators,List<Survey> responses) {}
    public record Option(long id,String name) {}
    public record Options(List<Option> institutions,List<Option> administrators) {}
    private record CaseSnapshot(long id,long institutionId,String institutionName,boolean active,String title,CaseStatus status,
                                Long administratorId,String administratorUsername,String administratorName) {}
    private record Stored(UUID requestId,Survey survey) {}
    private final RowMapper<Stored> mapper=(r,n)->new Stored(r.getObject("request_id",UUID.class),new Survey(r.getLong("id"),r.getLong("case_id"),
        r.getLong("institution_id"),r.getString("institution_name"),r.getString("case_title"),r.getLong("respondent_id"),r.getString("respondent_username"),r.getString("respondent_name"),
        r.getObject("administrator_id",Long.class),r.getString("administrator_username"),r.getString("administrator_name"),r.getInt("score"),r.getString("comment"),r.getTimestamp("created_at").toInstant()));

    public View view(long caseId) {
        User actor=reader(); CaseSnapshot c=authorizedCase(caseId,actor);Stored existing=find(caseId);
        return new View(caseId,c.status(),actor.role()==Role.USER&&c.active()&&c.status()==CaseStatus.CLOSED&&existing==null,existing==null?null:existing.survey());
    }

    @Transactional
    public View submit(long caseId,Input input) {
        User actor=helpdesk.me();if(actor.role()!=Role.USER||!actor.active())throw ApiException.forbidden();
        CaseSnapshot initial=authorizedCase(caseId,actor);
        // Match all support-accounting writes: institution before case. Serializes close/reopen and duplicate answers.
        db.queryForObject("SELECT id FROM institutions WHERE id=? FOR UPDATE",Long.class,initial.institutionId());
        db.queryForObject("SELECT id FROM support_cases WHERE id=? FOR UPDATE",Long.class,caseId);
        CaseSnapshot c=authorizedCase(caseId,actor);if(!c.active())throw ApiException.forbidden();
        UUID requestId=requestId(input==null?null:input.requestId());
        if(input.score()==null||input.score()<1||input.score()>5)throw ApiException.bad("La calificación debe ser un entero de 1 a 5.");
        String comment=input.comment()==null?"":input.comment().trim();if(comment.length()>2000)throw ApiException.bad("El comentario admite hasta 2000 caracteres.");
        Stored existing=find(caseId);
        if(existing!=null) {
            Survey previous=existing.survey();
            if(existing.requestId().equals(requestId)&&previous.respondentId()==actor.id()&&previous.score()==input.score()&&previous.comment().equals(comment))
                return new View(caseId,c.status(),false,previous);
            throw ApiException.conflict("La institución ya respondió esta encuesta. La respuesta original se conserva.");
        }
        if(c.status()!=CaseStatus.CLOSED)throw ApiException.conflict("La encuesta estará disponible cuando el caso esté cerrado.");
        if(db.queryForObject("SELECT COUNT(*) FROM satisfaction_surveys WHERE request_id=?",Long.class,requestId)>0)
            throw ApiException.conflict("Ese identificador de solicitud ya se utilizó. Actualice la encuesta.");
        long id=db.queryForObject("""
            INSERT INTO satisfaction_surveys(request_id,case_id,institution_id,institution_name,case_title,respondent_id,respondent_username,respondent_name,
              administrator_id,administrator_username,administrator_name,score,comment) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id
            """,Long.class,requestId,caseId,c.institutionId(),c.institutionName(),c.title(),actor.id(),actor.username(),actor.displayName(),
            c.administratorId(),c.administratorUsername(),c.administratorName(),input.score(),comment);
        db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,'RESPOND_SATISFACTION','CASE',?,?)",actor.username(),caseId,
            "Encuesta #"+id+"; puntuación="+input.score()+"; administrativo asignado al responder="+c.administratorId());
        return new View(caseId,c.status(),false,find(caseId).survey());
    }

    public Options options() {
        admin();return new Options(db.query("SELECT id,name FROM institutions ORDER BY name,id",(r,n)->new Option(r.getLong(1),r.getString(2))),
            db.query("SELECT id,display_name FROM app_users WHERE role IN ('ADMIN','TECH') OR id IN (SELECT administrator_id FROM satisfaction_surveys WHERE administrator_id IS NOT NULL) ORDER BY display_name,id",(r,n)->new Option(r.getLong(1),r.getString(2))));
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Report report(String from,String to,Long institutionId,Long administratorId) {
        admin();LocalDate today=LocalDate.now(zone),first=date(from,today.withDayOfMonth(1)),last=date(to,today);
        if(first.isAfter(last)||ChronoUnit.DAYS.between(first,last)>3659)throw ApiException.bad("Indique un periodo ordenado de hasta 3660 días.");
        if((institutionId!=null&&institutionId<=0)||(administratorId!=null&&administratorId<=0))throw ApiException.bad("Los identificadores deben ser positivos.");
        List<Object> params=new ArrayList<>(List.of(Timestamp.from(first.atStartOfDay(zone).toInstant()),Timestamp.from(last.plusDays(1).atStartOfDay(zone).toInstant())));
        String where=" WHERE created_at>=? AND created_at<?";
        if(institutionId!=null){where+=" AND institution_id=?";params.add(institutionId);}
        if(administratorId!=null){where+=" AND administrator_id=?";params.add(administratorId);}
        List<Survey> responses=db.query("SELECT * FROM satisfaction_surveys"+where+" ORDER BY created_at DESC,id DESC LIMIT 5001",mapper,params.toArray()).stream().map(Stored::survey).toList();
        if(responses.size()>5000)throw ApiException.bad("El reporte supera 5000 respuestas. Reduzca el periodo o filtre institución/administrativo; no se han omitido muestras.");
        long sum=0;long[] distribution=new long[5];var groups=new LinkedHashMap<Long,List<Survey>>();
        for(Survey survey:responses){sum+=survey.score();distribution[survey.score()-1]++;groups.computeIfAbsent(survey.administratorId(),key->new ArrayList<>()).add(survey);}
        List<Distribution> histogram=new ArrayList<>();for(int score=1;score<=5;score++)histogram.add(new Distribution(score,distribution[score-1]));
        List<Group> administrators=new ArrayList<>();
        for(var entry:groups.entrySet()) {List<Survey> samples=entry.getValue();long points=samples.stream().mapToLong(Survey::score).sum();String name=entry.getKey()==null?"Sin administrativo asignado":samples.get(0).administratorName();administrators.add(new Group(entry.getKey(),name,samples.size(),points,average(points,samples.size())));}
        return new Report(Instant.now(),zone.getId(),new Filters(first,last,institutionId,administratorId),responses.size(),sum,average(sum,responses.size()),List.copyOf(histogram),List.copyOf(administrators),responses);
    }
    private User reader(){User actor=helpdesk.me();if(actor.role()!=Role.ADMIN&&actor.role()!=Role.USER)throw ApiException.forbidden();return actor;}
    private void admin(){if(helpdesk.me().role()!=Role.ADMIN)throw ApiException.forbidden();}
    private CaseSnapshot authorizedCase(long id,User actor){
        List<CaseSnapshot> rows=db.query("""
            SELECT c.id,c.institution_id,i.name AS institution_name,i.active,c.title,c.status,c.assigned_admin_id,
              COALESCE(a.username,'') AS administrator_username,COALESCE(a.display_name,'') AS administrator_name
            FROM support_cases c JOIN institutions i ON i.id=c.institution_id LEFT JOIN app_users a ON a.id=c.assigned_admin_id WHERE c.id=?
            """,(r,n)->new CaseSnapshot(r.getLong("id"),r.getLong("institution_id"),r.getString("institution_name"),r.getBoolean("active"),r.getString("title"),CaseStatus.valueOf(r.getString("status")),
                r.getObject("assigned_admin_id",Long.class),r.getString("administrator_username"),r.getString("administrator_name")),id);
        if(rows.isEmpty())throw ApiException.missing();CaseSnapshot c=rows.get(0);
        if(actor.role()==Role.USER&&!Objects.equals(actor.institutionId(),c.institutionId()))throw ApiException.missing();return c;
    }
    private Stored find(long caseId){List<Stored> rows=db.query("SELECT * FROM satisfaction_surveys WHERE case_id=?",mapper,caseId);return rows.isEmpty()?null:rows.get(0);}
    private static BigDecimal average(long points,long samples){return samples==0?null:BigDecimal.valueOf(points).divide(BigDecimal.valueOf(samples),2,RoundingMode.HALF_UP);}
    private static UUID requestId(String value){try{UUID uuid=UUID.fromString(value);if(value.length()!=36||!uuid.toString().equals(value.toLowerCase(java.util.Locale.ROOT)))throw new IllegalArgumentException();return uuid;}catch(RuntimeException e){throw ApiException.bad("Indique un identificador UUID válido para la encuesta.");}}
    private static LocalDate date(String value,LocalDate fallback){if(value==null||value.isBlank())return fallback;try{if(!value.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException();LocalDate day=LocalDate.parse(value);if(day.getYear()<1||day.getYear()>9999)throw new IllegalArgumentException();return day;}catch(RuntimeException e){throw ApiException.bad("Fecha inválida. Utilice AAAA-MM-DD.");}}
}
