package mx.siabuc.helpdesk;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RenewalService {
    public record Settings(Boolean enabled,Integer daysBeforeExpiry) {}
    public record Candidate(long policyId,long institutionId,String institutionName,String folio,LocalDate validUntil,
        long daysUntilExpiry,BigDecimal agreedAmount,long emailMinutes,long remoteMinutes) {}
    public record Link(long id,long institutionId,String institutionName,long previousPolicyId,String previousPolicyFolio,
        long newPolicyId,String newPolicyFolio,String newPolicyStatus,long quoteId,String quoteFolio,String createdBy,Instant createdAt) {}
    public record Notice(long id,long policyId,long institutionId,String institutionName,String policyFolio,LocalDate expirationDate,
        Instant createdAt,Instant readAt,boolean stillRelevant) {}
    public record Overview(Settings settings,List<Candidate> upcoming,List<Link> links,List<Notice> notices) {}
    public record ScanResult(int created) {}
    private final JdbcTemplate db;private final HelpdeskService helpdesk;
    public RenewalService(JdbcTemplate db,HelpdeskService helpdesk){this.db=db;this.helpdesk=helpdesk;}
    public Settings settings(){actor();return currentSettings();}
    private Settings currentSettings(){return db.queryForObject("SELECT enabled,days_before_expiry FROM renewal_settings WHERE id=1",(r,n)->new Settings(r.getBoolean(1),r.getInt(2)));}
    @Transactional public Settings updateSettings(Settings input){Models.User actor=actor();if(input==null||input.enabled()==null||input.daysBeforeExpiry()==null||input.daysBeforeExpiry()<0||input.daysBeforeExpiry()>365)
        throw ApiException.bad("Indique si los avisos están activos y un umbral entero de 0 a 365 días.");
        db.queryForObject("SELECT id FROM renewal_settings WHERE id=1 FOR UPDATE",Integer.class);Settings before=currentSettings();
        db.update("UPDATE renewal_settings SET enabled=?,days_before_expiry=?,updated_at=NOW() WHERE id=1",input.enabled(),input.daysBeforeExpiry());
        db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES(?,'UPDATE_RENEWAL_SETTINGS','RENEWAL_SETTINGS',1,?)",actor.username(),"Antes: "+before+"; después: "+input);
        return currentSettings();}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Overview overview(){Models.User actor=actor();Settings s=currentSettings();return new Overview(s,candidates(s),links(),notices(actor.id()));}
    @Transactional public ScanResult scan(){actor();return scanDuePolicies();}
    /** Scheduler entry point; never reads a web session or sends email. The singleton lock serializes scanners. */
    @Transactional public ScanResult scanDuePolicies(){db.queryForObject("SELECT id FROM renewal_settings WHERE id=1 FOR UPDATE",Integer.class);Settings settings=currentSettings();if(!settings.enabled())return new ScanResult(0);int created=0;
        for(Candidate c:candidates(settings))created+=db.update("INSERT INTO renewal_notices(policy_id,institution_id,institution_name,policy_folio,expiration_date) VALUES(?,?,?,?,?) ON CONFLICT(policy_id,expiration_date) DO NOTHING",
            c.policyId(),c.institutionId(),c.institutionName(),c.folio(),java.sql.Date.valueOf(c.validUntil()));return new ScanResult(created);}
    @Transactional public void markRead(long id){Models.User actor=actor();if(db.queryForObject("SELECT COUNT(*) FROM renewal_notices WHERE id=?",Long.class,id)==0)throw ApiException.missing();
        db.update("INSERT INTO renewal_notice_reads(notice_id,user_id) VALUES(?,?) ON CONFLICT(notice_id,user_id) DO NOTHING",id,actor.id());}
    private List<Candidate> candidates(Settings settings){LocalDate today=today(),limit=today.plusDays(settings.daysBeforeExpiry());return db.query("""
        SELECT p.id,p.institution_id,i.name,p.folio,p.valid_until,p.agreed_amount,p.email_minutes,p.remote_minutes
        FROM support_policies p JOIN institutions i ON i.id=p.institution_id
        WHERE p.status='ACTIVE' AND i.active AND p.valid_until<=? AND NOT EXISTS(SELECT 1 FROM policy_renewals r WHERE r.previous_policy_id=p.id)
        ORDER BY p.valid_until,p.id
        """,(r,n)->new Candidate(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getDate(5).toLocalDate(),ChronoUnit.DAYS.between(today,r.getDate(5).toLocalDate()),r.getBigDecimal(6),r.getLong(7),r.getLong(8)),java.sql.Date.valueOf(limit));}
    private List<Link> links(){return db.query("""
        SELECT r.*,i.name AS institution_name,p.folio AS previous_folio,n.folio AS new_folio,n.status AS new_status,q.folio AS quote_folio,u.username AS creator
        FROM policy_renewals r JOIN institutions i ON i.id=r.institution_id JOIN support_policies p ON p.id=r.previous_policy_id
        JOIN support_policies n ON n.id=r.new_policy_id JOIN support_quotes q ON q.id=r.quote_id JOIN app_users u ON u.id=r.created_by ORDER BY r.id DESC
        """,(r,n)->new Link(r.getLong("id"),r.getLong("institution_id"),r.getString("institution_name"),r.getLong("previous_policy_id"),r.getString("previous_folio"),r.getLong("new_policy_id"),r.getString("new_folio"),r.getString("new_status"),r.getLong("quote_id"),r.getString("quote_folio"),r.getString("creator"),r.getTimestamp("created_at").toInstant()));}
    private List<Notice> notices(long actor){return db.query("""
        SELECT n.*,v.read_at,(p.status='ACTIVE' AND i.active AND p.valid_until=n.expiration_date
          AND NOT EXISTS(SELECT 1 FROM policy_renewals l WHERE l.previous_policy_id=p.id)) AS relevant
        FROM renewal_notices n JOIN support_policies p ON p.id=n.policy_id JOIN institutions i ON i.id=n.institution_id
        LEFT JOIN renewal_notice_reads v ON v.notice_id=n.id AND v.user_id=? ORDER BY n.id DESC LIMIT 200
        """,(r,n)->new Notice(r.getLong("id"),r.getLong("policy_id"),r.getLong("institution_id"),r.getString("institution_name"),r.getString("policy_folio"),r.getDate("expiration_date").toLocalDate(),
            r.getTimestamp("created_at").toInstant(),r.getTimestamp("read_at")==null?null:r.getTimestamp("read_at").toInstant(),r.getBoolean("relevant")),actor);}
    private Models.User actor(){Models.User user=helpdesk.me();if(user.role()!=Models.Role.ADMIN && user.role()!=Models.Role.BILLING)throw ApiException.forbidden();return user;}
    private static LocalDate today(){return LocalDate.now(ZoneId.of("America/Mexico_City"));}
}
