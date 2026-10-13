package mx.siabuc.helpdesk;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Financial-only data; never joins support cases, descriptions of attention or attachments. */
@Service
public class BillingReportService {
    private static final BigDecimal ZERO=new BigDecimal("0.00");
    private static final int MAX_ROWS=5000;
    private final JdbcTemplate db;private final HelpdeskService helpdesk;private final ZoneId zone;
    public BillingReportService(JdbcTemplate db,HelpdeskService helpdesk,@Value("${app.reporting-time-zone:America/Mexico_City}")String timeZone){this.db=db;this.helpdesk=helpdesk;zone=ZoneId.of(timeZone);}
    public record Filters(Long institutionId,Long policyId,String method,String currency,String status,LocalDate from,LocalDate to){}
    public record PaymentRow(long id,long institutionId,String institutionName,Long policyId,String policyFolio,
        String reference,BigDecimal amount,String currency,LocalDate paidOn,String method,long emailMinutes,long remoteMinutes,
        String notes,String createdBy,Instant createdAt,boolean voided,String voidReason,String voidedBy,Instant voidedAt){}
    public record MoneyTotal(String currency,long activeCount,BigDecimal activeAmount,long voidedCount,BigDecimal voidedAmount,long emailMinutes,long remoteMinutes){}
    public record Group(long institutionId,String institutionName,String method,String currency,long activeCount,BigDecimal activeAmount,
        long voidedCount,BigDecimal voidedAmount,long emailMinutes,long remoteMinutes){}
    public record InstitutionTotal(long institutionId,String institutionName,String currency,long activeCount,BigDecimal activeAmount,
        long voidedCount,BigDecimal voidedAmount,long emailMinutes,long remoteMinutes){}
    public record PolicyBalance(long id,long institutionId,String institutionName,String folio,LocalDate validFrom,LocalDate validUntil,
        String status,String currency,BigDecimal agreedAmount,BigDecimal paidAmount,BigDecimal remainingAmount,
        long emailMinutes,long remoteMinutes,long creditedEmailMinutes,long creditedRemoteMinutes){}
    public record PolicyTotals(long activePolicies,long cancelledPolicies,BigDecimal agreedMXN,BigDecimal paidMXN,BigDecimal pendingMXN){}
    public record Report(Instant generatedAt,String timeZone,Filters filters,List<PaymentRow> payments,List<MoneyTotal> totals,
        List<Group> groups,List<InstitutionTotal> institutionTotals,List<PolicyBalance> policyBalances,PolicyTotals policyTotals){}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Report report(Long institutionId,Long policyId,String method,String currency,String status,String from,String to){
        Models.Role role=helpdesk.me().role();if(role!=Models.Role.ADMIN&&role!=Models.Role.BILLING)throw ApiException.forbidden();
        if((institutionId!=null&&institutionId<=0)||(policyId!=null&&policyId<=0))throw ApiException.bad("Los identificadores deben ser positivos.");
        method=normalize(method);currency=normalize(currency);status=status==null||status.isBlank()?"ALL":status;
        if(method!=null&&!List.of("TRANSFER","DEPOSIT","CASH","CREDIT_CARD","DEBIT_CARD","CHECK","OTHER","CARD").contains(method))throw ApiException.bad("Método de pago inválido.");
        if(currency!=null&&!currency.matches("[A-Z]{3}"))throw ApiException.bad("Moneda inválida; use ALL o un código de tres letras mayúsculas.");
        if(!List.of("ALL","ACTIVE","VOIDED").contains(status))throw ApiException.bad("Estado inválido; use ALL, ACTIVE o VOIDED.");
        LocalDate today=LocalDate.now(zone),first=date(from,today.withDayOfMonth(1)),last=date(to,today);
        if(first.isAfter(last)||ChronoUnit.DAYS.between(first,last)>3659)throw ApiException.bad("El periodo debe estar ordenado y abarcar como máximo 3660 días.");
        Filters filters=new Filters(institutionId,policyId,method,currency==null?"ALL":currency,status,first,last);
        List<Object> params=new ArrayList<>(List.of(Date.valueOf(first),Date.valueOf(last)));
        String where=" WHERE p.paid_on>=? AND p.paid_on<=?";
        if(institutionId!=null){where+=" AND p.institution_id=?";params.add(institutionId);}
        if(policyId!=null){where+=" AND p.policy_id=?";params.add(policyId);}
        if(method!=null){where+=" AND p.method=?";params.add(method);}
        if(currency!=null){where+=" AND p.currency=?";params.add(currency);}
        if(!status.equals("ALL")){where+=" AND p.voided=?";params.add(status.equals("VOIDED"));}
        List<PaymentRow> rows=db.query("""
            SELECT p.*,i.name AS institution_name,sp.folio AS policy_folio,u.username AS author,v.username AS voider
            FROM payments p JOIN institutions i ON i.id=p.institution_id JOIN app_users u ON u.id=p.created_by
            LEFT JOIN app_users v ON v.id=p.voided_by LEFT JOIN support_policies sp ON sp.id=p.policy_id
            """+where+" ORDER BY p.paid_on,p.id LIMIT "+(MAX_ROWS+1),(r,n)->new PaymentRow(r.getLong("id"),r.getLong("institution_id"),r.getString("institution_name"),r.getObject("policy_id",Long.class),r.getString("policy_folio"),r.getString("reference"),r.getBigDecimal("amount"),r.getString("currency"),r.getDate("paid_on").toLocalDate(),r.getString("method"),r.getLong("email_minutes"),r.getLong("remote_minutes"),r.getString("notes"),r.getString("author"),r.getTimestamp("created_at").toInstant(),r.getBoolean("voided"),r.getString("void_reason"),r.getString("voider"),r.getTimestamp("voided_at")==null?null:r.getTimestamp("voided_at").toInstant()),params.toArray());
        List<Object> pp=new ArrayList<>();String pw=" WHERE 1=1";
        if(institutionId!=null){pw+=" AND sp.institution_id=?";pp.add(institutionId);}
        if(policyId!=null){pw+=" AND sp.id=?";pp.add(policyId);}
        List<PolicyBalance> policies=db.query("""
            SELECT sp.*,i.name AS institution_name,COALESCE(p.paid_amount,0) AS paid_amount,
              COALESCE(p.email,0) AS credited_email,COALESCE(p.remote,0) AS credited_remote
            FROM support_policies sp JOIN institutions i ON i.id=sp.institution_id
            LEFT JOIN (SELECT policy_id,SUM(amount) AS paid_amount,SUM(email_minutes) AS email,SUM(remote_minutes) AS remote
              FROM payments WHERE NOT voided AND policy_id IS NOT NULL GROUP BY policy_id) p ON p.policy_id=sp.id
            """+pw+" ORDER BY i.name,sp.id LIMIT "+(MAX_ROWS+1),(r,n)->{
                BigDecimal amount=r.getBigDecimal("agreed_amount"),paid=r.getBigDecimal("paid_amount");
                return new PolicyBalance(r.getLong("id"),r.getLong("institution_id"),r.getString("institution_name"),r.getString("folio"),r.getDate("valid_from").toLocalDate(),r.getDate("valid_until").toLocalDate(),r.getString("status"),r.getString("currency"),amount,paid,amount.subtract(paid),r.getLong("email_minutes"),r.getLong("remote_minutes"),r.getLong("credited_email"),r.getLong("credited_remote"));
            },pp.toArray());
        if(rows.size()+policies.size()>MAX_ROWS)throw ApiException.bad("El reporte supera 5000 registros entre pagos y pólizas. Reduzca el periodo o filtre institución/póliza; no se han omitido filas.");
        if(rows.stream().mapToLong(p->p.notes().length()+(p.voidReason()==null?0:p.voidReason().length())).sum()>2_000_000)throw ApiException.bad("Las descripciones del reporte son demasiado extensas. Reduzca el periodo o agregue filtros.");
        Map<String,MoneyTotal> totals=new LinkedHashMap<>();Map<String,Group> groups=new LinkedHashMap<>();Map<String,InstitutionTotal> institutionTotals=new LinkedHashMap<>();
        for(PaymentRow p:rows){
            MoneyTotal prior=totals.getOrDefault(p.currency(),new MoneyTotal(p.currency(),0,ZERO,0,ZERO,0,0));
            totals.put(p.currency(),new MoneyTotal(p.currency(),prior.activeCount()+(p.voided()?0:1),prior.activeAmount().add(p.voided()?ZERO:p.amount()),prior.voidedCount()+(p.voided()?1:0),prior.voidedAmount().add(p.voided()?p.amount():ZERO),prior.emailMinutes()+(p.voided()?0:p.emailMinutes()),prior.remoteMinutes()+(p.voided()?0:p.remoteMinutes())));
            String institutionKey=p.institutionId()+":"+p.currency();InstitutionTotal it=institutionTotals.getOrDefault(institutionKey,new InstitutionTotal(p.institutionId(),p.institutionName(),p.currency(),0,ZERO,0,ZERO,0,0));
            institutionTotals.put(institutionKey,new InstitutionTotal(p.institutionId(),p.institutionName(),p.currency(),it.activeCount()+(p.voided()?0:1),it.activeAmount().add(p.voided()?ZERO:p.amount()),it.voidedCount()+(p.voided()?1:0),it.voidedAmount().add(p.voided()?p.amount():ZERO),it.emailMinutes()+(p.voided()?0:p.emailMinutes()),it.remoteMinutes()+(p.voided()?0:p.remoteMinutes())));
            String key=p.institutionId()+":"+p.method()+":"+p.currency();Group g=groups.getOrDefault(key,new Group(p.institutionId(),p.institutionName(),p.method(),p.currency(),0,ZERO,0,ZERO,0,0));
            groups.put(key,new Group(p.institutionId(),p.institutionName(),p.method(),p.currency(),g.activeCount()+(p.voided()?0:1),g.activeAmount().add(p.voided()?ZERO:p.amount()),g.voidedCount()+(p.voided()?1:0),g.voidedAmount().add(p.voided()?p.amount():ZERO),g.emailMinutes()+(p.voided()?0:p.emailMinutes()),g.remoteMinutes()+(p.voided()?0:p.remoteMinutes())));
        }
        long active=0,cancelled=0;BigDecimal agreed=ZERO,paid=ZERO,pending=ZERO;
        for(PolicyBalance p:policies){if(!p.status().equals("ACTIVE")){cancelled++;continue;}active++;agreed=agreed.add(p.agreedAmount());paid=paid.add(p.paidAmount());pending=pending.add(p.remainingAmount());}
        return new Report(Instant.now(),zone.getId(),filters,List.copyOf(rows),totals.values().stream().sorted(java.util.Comparator.comparing(MoneyTotal::currency)).toList(),List.copyOf(groups.values()),List.copyOf(institutionTotals.values()),List.copyOf(policies),new PolicyTotals(active,cancelled,agreed,paid,pending));
    }
    private static String normalize(String value){return value==null||value.isBlank()||value.equals("ALL")?null:value;}
    private static LocalDate date(String value,LocalDate fallback){if(value==null||value.isBlank())return fallback;try{if(!value.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException();LocalDate date=LocalDate.parse(value);if(date.getYear()<1||date.getYear()>9999)throw new IllegalArgumentException();return date;}catch(RuntimeException e){throw ApiException.bad("Fecha inválida. Utilice AAAA-MM-DD.");}}
}
