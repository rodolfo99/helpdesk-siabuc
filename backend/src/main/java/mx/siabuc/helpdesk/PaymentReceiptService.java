package mx.siabuc.helpdesk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stable administrative documents; snapshots are never rewritten after issuance. */
@Service
public class PaymentReceiptService {
    private final JdbcTemplate db;private final HelpdeskService helpdesk;private final ClientFinanceAccessService access;
    private final ObjectMapper json;private final String timeZone;
    public PaymentReceiptService(JdbcTemplate db,HelpdeskService helpdesk,ClientFinanceAccessService access,ObjectMapper json,
            @Value("${app.reporting-time-zone:America/Mexico_City}")String timeZone){
        this.db=db;this.helpdesk=helpdesk;this.access=access;this.json=json;this.timeZone=ZoneId.of(timeZone).getId();
    }
    public record Snapshot(long paymentId,long institutionId,String institutionName,String contactName,String institutionEmail,
        String institutionPhone,String reference,BigDecimal amount,String currency,LocalDate paidOn,String method,
        long emailMinutes,long remoteMinutes,String description,long registeredById,String registeredBy,String registeredByName,
        Instant paymentCreatedAt,Long policyId,String policyFolio,boolean voidedAtIssue,String voidReasonAtIssue,
        String voidedByAtIssue,Instant voidedAtIssueDate,String timeZone){}
    public record Receipt(long id,long paymentId,String folio,Instant issuedAt,long issuedBy,Snapshot snapshot,
        boolean voided,String voidReason,String voidedBy,String voidedByName,Instant voidedAt,Instant downloadedAt,boolean clientView){}
    private record Payment(long institutionId,Long policyId){}
    private record Stored(long id,String folio,Instant issuedAt,long issuedBy,String snapshot){}
    private record Current(boolean voided,String reason,String username,String displayName,Instant at){}

    /** GET is idempotent: it lazily persists an immutable receipt, never another payment or allocation. */
    @Transactional
    public Receipt receipt(long paymentId,boolean clientMode){
        Models.User actor=clientMode?access.requireClient():staff();
        Payment payment=payment(paymentId);
        if(clientMode&&!Objects.equals(actor.institutionId(),payment.institutionId()))throw ApiException.missing();
        // Same ordering as payments/voids/proofs; issuance and status read are consistent with accounting.
        db.queryForObject("SELECT id FROM institutions WHERE id=? FOR UPDATE",Long.class,payment.institutionId());
        if(payment.policyId()!=null)db.queryForObject("SELECT id FROM support_policies WHERE id=? FOR UPDATE",Long.class,payment.policyId());
        db.queryForObject("SELECT id FROM payments WHERE id=? FOR UPDATE",Long.class,paymentId);
        // A portal switch may have changed while waiting for the institutional lock.
        if(clientMode){Models.User current=access.requireClient();if(!Objects.equals(current.institutionId(),payment.institutionId()))throw ApiException.missing();}
        List<Stored> existing=stored(paymentId);
        Stored stored;
        if(existing.isEmpty()){
            Snapshot snapshot=snapshot(paymentId);
            String folio=String.format(Locale.ROOT,"SIABUC-REC-%010d",paymentId);
            db.update("INSERT INTO payment_receipts(payment_id,folio,snapshot,issued_by) VALUES (?,?,?::jsonb,?)",paymentId,folio,encode(snapshot),actor.id());
            db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,'ISSUE_PAYMENT_RECEIPT','PAYMENT',?,?)",
                actor.username(),paymentId,"Emisión de recibo administrativo "+folio+". Se conservó una instantánea de los datos. No genera pago, crédito de minutos ni correo; no es comprobante fiscal.");
            stored=stored(paymentId).get(0);
        }else stored=existing.get(0);
        Current current=db.queryForObject("""
            SELECT p.voided,p.void_reason,p.voided_at,u.username,u.display_name
            FROM payments p LEFT JOIN app_users u ON u.id=p.voided_by WHERE p.id=?
            """,(r,n)->new Current(r.getBoolean("voided"),r.getString("void_reason"),r.getString("username"),r.getString("display_name"),r.getTimestamp("voided_at")==null?null:r.getTimestamp("voided_at").toInstant()),paymentId);
        Snapshot snapshot=decode(stored.snapshot());
        // Keep the full immutable record for staff, but never pass private fields to the client renderer.
        if(clientMode)snapshot=publicSnapshot(snapshot);
        return new Receipt(stored.id(),paymentId,stored.folio(),stored.issuedAt(),clientMode?0:stored.issuedBy(),snapshot,
            current.voided(),clientMode?null:current.reason(),clientMode?null:current.username(),clientMode?null:current.displayName(),current.at(),Instant.now(),clientMode);
    }
    private static Snapshot publicSnapshot(Snapshot s){return new Snapshot(s.paymentId(),s.institutionId(),s.institutionName(),s.contactName(),s.institutionEmail(),s.institutionPhone(),
        s.reference(),s.amount(),s.currency(),s.paidOn(),s.method(),s.emailMinutes(),s.remoteMinutes(),null,0,null,null,s.paymentCreatedAt(),s.policyId(),s.policyFolio(),
        s.voidedAtIssue(),null,null,s.voidedAtIssueDate(),s.timeZone());}
    private List<Stored> stored(long paymentId){return db.query("SELECT id,folio,issued_at,issued_by,snapshot::text FROM payment_receipts WHERE payment_id=?",
        (r,n)->new Stored(r.getLong("id"),r.getString("folio"),r.getTimestamp("issued_at").toInstant(),r.getLong("issued_by"),r.getString("snapshot")),paymentId);}
    private Models.User staff(){Models.User actor=helpdesk.me();if(actor.role()!=Models.Role.ADMIN&&actor.role()!=Models.Role.BILLING)throw ApiException.forbidden();return actor;}
    private Payment payment(long id){List<Payment> rows=db.query("SELECT institution_id,policy_id FROM payments WHERE id=?",(r,n)->new Payment(r.getLong("institution_id"),r.getObject("policy_id",Long.class)),id);if(rows.isEmpty())throw ApiException.missing();return rows.get(0);}
    private Snapshot snapshot(long id){return db.queryForObject("""
        SELECT p.*,i.name AS institution_name,i.contact_name,i.email AS institution_email,i.phone AS institution_phone,
          u.username AS author_username,u.display_name AS author_name,sp.folio AS policy_folio,v.username AS void_username
        FROM payments p JOIN institutions i ON i.id=p.institution_id JOIN app_users u ON u.id=p.created_by
        LEFT JOIN support_policies sp ON sp.id=p.policy_id LEFT JOIN app_users v ON v.id=p.voided_by WHERE p.id=?
        """,(r,n)->new Snapshot(r.getLong("id"),r.getLong("institution_id"),r.getString("institution_name"),r.getString("contact_name"),r.getString("institution_email"),r.getString("institution_phone"),
            r.getString("reference"),r.getBigDecimal("amount"),r.getString("currency"),r.getDate("paid_on").toLocalDate(),r.getString("method"),r.getLong("email_minutes"),r.getLong("remote_minutes"),r.getString("notes"),
            r.getLong("created_by"),r.getString("author_username"),r.getString("author_name"),r.getTimestamp("created_at").toInstant(),r.getObject("policy_id",Long.class),r.getString("policy_folio"),r.getBoolean("voided"),r.getString("void_reason"),r.getString("void_username"),r.getTimestamp("voided_at")==null?null:r.getTimestamp("voided_at").toInstant(),timeZone),id);}
    private String encode(Snapshot snapshot){try{return json.writeValueAsString(snapshot);}catch(JsonProcessingException e){throw new IllegalStateException("No se pudo conservar el recibo administrativo.",e);}}
    private Snapshot decode(String stored){try{return json.readValue(stored,Snapshot.class);}catch(JsonProcessingException e){throw new IllegalStateException("No se pudo leer el recibo administrativo.",e);}}
}
