package mx.siabuc.helpdesk;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Deliberately separate projections: no internal notes, cancellation reasons or private actor identities. */
@Service
public class ClientFinanceService {
    private final JdbcTemplate db;private final ClientFinanceAccessService access;
    public ClientFinanceService(JdbcTemplate db,ClientFinanceAccessService access){this.db=db;this.access=access;}
    public record Policy(long id,String folio,LocalDate validFrom,LocalDate validUntil,BigDecimal agreedAmount,String currency,long emailMinutes,long remoteMinutes,String status,BigDecimal paidAmount,BigDecimal remainingAmount,long creditedEmailMinutes,long creditedRemoteMinutes){}
    public record Payment(long id,Long policyId,String policyFolio,String reference,BigDecimal amount,String currency,LocalDate paidOn,String method,long emailMinutes,long remoteMinutes,boolean voided,Instant createdAt){}
    public record Proof(long id,long paymentId,String filename,String contentType,long sizeBytes,String sha256,Instant createdAt){}
    public record Summary(long institutionId,String institutionName,List<Policy> policies,List<Payment> payments){}
    public record Download(Proof metadata,byte[] bytes){}
    private static final String FILES="SELECT id,payment_id,filename,content_type,size_bytes,sha256,created_at FROM payment_attachments";
    private static final RowMapper<Proof> FILE=(r,n)->new Proof(r.getLong("id"),r.getLong("payment_id"),r.getString("filename"),r.getString("content_type"),r.getLong("size_bytes"),r.getString("sha256"),r.getTimestamp("created_at").toInstant());
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Summary summary(){
        Models.User actor=access.requireClient();long institution=actor.institutionId();
        List<Policy> policies=db.query("""
            SELECT p.*,COALESCE(x.paid,0) paid,COALESCE(x.email,0) credited_email,COALESCE(x.remote,0) credited_remote
            FROM support_policies p LEFT JOIN (SELECT policy_id,SUM(amount) paid,SUM(email_minutes) email,SUM(remote_minutes) remote FROM payments WHERE NOT voided GROUP BY policy_id) x ON x.policy_id=p.id
            WHERE p.institution_id=? ORDER BY p.id DESC
            """,(r,n)->new Policy(r.getLong("id"),r.getString("folio"),r.getDate("valid_from").toLocalDate(),r.getDate("valid_until").toLocalDate(),r.getBigDecimal("agreed_amount"),r.getString("currency"),r.getLong("email_minutes"),r.getLong("remote_minutes"),r.getString("status"),r.getBigDecimal("paid"),r.getString("status").equals("ACTIVE")?r.getBigDecimal("agreed_amount").subtract(r.getBigDecimal("paid")):BigDecimal.ZERO,r.getLong("credited_email"),r.getLong("credited_remote")),institution);
        List<Payment> payments=db.query("SELECT p.id,p.policy_id,s.folio,p.reference,p.amount,p.currency,p.paid_on,p.method,p.email_minutes,p.remote_minutes,p.voided,p.created_at FROM payments p LEFT JOIN support_policies s ON s.id=p.policy_id WHERE p.institution_id=? ORDER BY p.id DESC",(r,n)->new Payment(r.getLong("id"),r.getObject("policy_id",Long.class),r.getString("folio"),r.getString("reference"),r.getBigDecimal("amount"),r.getString("currency"),r.getDate("paid_on").toLocalDate(),r.getString("method"),r.getLong("email_minutes"),r.getLong("remote_minutes"),r.getBoolean("voided"),r.getTimestamp("created_at").toInstant()),institution);
        return new Summary(institution,db.queryForObject("SELECT name FROM institutions WHERE id=?",String.class,institution),policies,payments);
    }
    public List<Proof> proofs(long paymentId){Models.User actor=access.requireClient();payment(paymentId,actor);return db.query(FILES+" WHERE payment_id=? ORDER BY id",FILE,paymentId);}
    public Download download(long id){Models.User actor=access.requireClient();List<Proof> rows=db.query(FILES+" WHERE id=?",FILE,id);if(rows.isEmpty())throw ApiException.missing();Proof proof=rows.get(0);payment(proof.paymentId(),actor);return new Download(proof,db.queryForObject("SELECT file_bytes FROM payment_attachments WHERE id=?",byte[].class,id));}
    private void payment(long id,Models.User actor){List<Long> institution=db.queryForList("SELECT institution_id FROM payments WHERE id=?",Long.class,id);if(institution.isEmpty()||!Objects.equals(institution.get(0),actor.institutionId()))throw ApiException.missing();}
}
