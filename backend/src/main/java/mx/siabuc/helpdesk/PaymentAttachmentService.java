package mx.siabuc.helpdesk;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Append-only payment evidence; never credits minutes, records money or sends mail. */
@Service
public class PaymentAttachmentService {
    static final int MAX_FILE_BYTES=10*1024*1024;
    static final int MAX_PAYMENT_FILES=10;
    static final long MAX_PAYMENT_BYTES=50L*1024*1024;
    private final JdbcTemplate db;
    private final HelpdeskService helpdesk;
    public PaymentAttachmentService(JdbcTemplate db,HelpdeskService helpdesk){this.db=db;this.helpdesk=helpdesk;}
    public record Attachment(long id,long paymentId,String filename,String contentType,long sizeBytes,String sha256,
            String uploadedBy,String uploadedName,Instant createdAt) {}
    public record Download(Attachment metadata,byte[] bytes) {}
    private record Payment(long institutionId,Long policyId,String reference) {}
    private static final String SELECT="SELECT a.id,a.payment_id,a.filename,a.content_type,a.size_bytes,a.sha256,a.created_at,u.username AS uploader,u.display_name AS uploader_name FROM payment_attachments a JOIN app_users u ON u.id=a.uploaded_by";
    private static final RowMapper<Attachment> ROW=(r,n)->new Attachment(r.getLong("id"),r.getLong("payment_id"),r.getString("filename"),
        r.getString("content_type"),r.getLong("size_bytes"),r.getString("sha256"),r.getString("uploader"),r.getString("uploader_name"),r.getTimestamp("created_at").toInstant());

    public List<Attachment> list(long paymentId){actor();payment(paymentId);return db.query(SELECT+" WHERE a.payment_id=? ORDER BY a.id DESC",ROW,paymentId);}
    public Download download(long id){
        actor();List<Attachment> matches=db.query(SELECT+" WHERE a.id=?",ROW,id);
        if(matches.isEmpty())throw ApiException.missing();
        Attachment file=matches.get(0);payment(file.paymentId());
        return new Download(file,db.queryForObject("SELECT file_bytes FROM payment_attachments WHERE id=?",byte[].class,id));
    }

    @Transactional
    public Attachment upload(long paymentId,String originalFilename,byte[] bytes,UUID requestId){
        Models.User actor=actor();
        if(requestId==null)throw ApiException.bad("Indique el identificador del envío del comprobante.");
        Payment initial=payment(paymentId);
        // Same accounting order: institution -> policy (if any) -> payment. No balances are modified.
        db.queryForObject("SELECT id FROM institutions WHERE id=? FOR UPDATE",Long.class,initial.institutionId());
        if(initial.policyId()!=null)db.queryForObject("SELECT id FROM support_policies WHERE id=? FOR UPDATE",Long.class,initial.policyId());
        db.queryForObject("SELECT id FROM payments WHERE id=? FOR UPDATE",Long.class,paymentId);
        String filename=AttachmentService.filename(originalFilename);
        String extension=filename.substring(filename.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        if(!List.of("png","jpg","jpeg","pdf").contains(extension))throw ApiException.bad("El comprobante debe ser PDF, PNG o JPG/JPEG.");
        // Shared signature checks and filename normalization; diagnostics are explicitly excluded above.
        if(bytes==null || bytes.length==0)throw ApiException.bad("El comprobante está vacío.");
        String type;
        try{type=AttachmentService.contentType(filename,bytes);}
        catch(ApiException ex){
            if(ex.status()!=400)throw ex;
            throw ApiException.bad("El contenido del comprobante no corresponde a su extensión. Use PDF, PNG o JPG/JPEG.");
        }
        String digest=digest(bytes);
        List<Attachment> previous=db.query(SELECT+" WHERE a.uploaded_by=? AND a.request_id=?",ROW,actor.id(),requestId);
        if(!previous.isEmpty()){
            Attachment existing=previous.get(0);
            if(existing.paymentId()!=paymentId || !existing.filename().equals(filename) || !existing.sha256().equals(digest))
                throw ApiException.conflict("El identificador del envío ya corresponde a otro comprobante.");
            return existing;
        }
        long count=db.queryForObject("SELECT COUNT(*) FROM payment_attachments WHERE payment_id=?",Long.class,paymentId);
        long size=db.queryForObject("SELECT COALESCE(SUM(size_bytes),0) FROM payment_attachments WHERE payment_id=?",Long.class,paymentId);
        if(count>=MAX_PAYMENT_FILES || size+bytes.length>MAX_PAYMENT_BYTES)
            throw ApiException.conflict("El pago alcanzó su límite de comprobantes: 10 archivos o 50 MiB en total.");
        Long id=db.queryForObject("""
            INSERT INTO payment_attachments(payment_id,uploaded_by,request_id,filename,content_type,size_bytes,sha256,file_bytes)
            VALUES(?,?,?,?,?,?,?,?) RETURNING id
            """,Long.class,paymentId,actor.id(),requestId,filename,type,bytes.length,digest,bytes);
        String description="Comprobante #"+id+" adjuntado al pago #"+paymentId+" ("+initial.reference()+"): "+filename
            +"; "+bytes.length+" bytes; SHA-256="+digest+". Sin modificación de importe ni minutos.";
        db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,'ATTACH_PAYMENT_PROOF','PAYMENT',?,?)",actor.username(),paymentId,description);
        if(initial.policyId()!=null){
            db.update("INSERT INTO policy_history(policy_id,action,description,payment_id,created_by) VALUES (?,'ATTACH_PAYMENT_PROOF',?,?,?)",
                initial.policyId(),description,paymentId,actor.id());
            db.update("UPDATE support_policies SET updated_at=NOW() WHERE id=?",initial.policyId());
        }
        return db.queryForObject(SELECT+" WHERE a.id=?",ROW,id);
    }
    private Models.User actor(){
        Models.User actor=helpdesk.me();
        if(actor.role()!=Models.Role.ADMIN && actor.role()!=Models.Role.BILLING)throw ApiException.forbidden();
        return actor;
    }
    private Payment payment(long id){
        List<Payment> rows=db.query("SELECT institution_id,policy_id,reference FROM payments WHERE id=?",
            (r,n)->new Payment(r.getLong("institution_id"),r.getObject("policy_id",Long.class),r.getString("reference")),id);
        if(rows.isEmpty())throw ApiException.missing();return rows.get(0);
    }
    private static String digest(byte[] bytes){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
