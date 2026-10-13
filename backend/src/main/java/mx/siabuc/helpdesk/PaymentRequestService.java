package mx.siabuc.helpdesk;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Requests are declarations awaiting validation. Only approval can create a financial movement. */
@Service
public class PaymentRequestService {
    private final JdbcTemplate db;private final HelpdeskService helpdesk;private final ClientFinanceAccessService access;private final PaymentAttachmentService proofs;
    public PaymentRequestService(JdbcTemplate db,HelpdeskService helpdesk,ClientFinanceAccessService access,PaymentAttachmentService proofs){this.db=db;this.helpdesk=helpdesk;this.access=access;this.proofs=proofs;}
    public record Input(String requestId,Long institutionId,Long policyId,String reference,BigDecimal amount,LocalDate paidOn,Models.PaymentMethod method,Long emailMinutes,Long remoteMinutes,String description){}
    public record Request(long id,long institutionId,String institutionName,Long policyId,String policyFolio,String reference,BigDecimal amount,String currency,LocalDate paidOn,Models.PaymentMethod method,long emailMinutes,long remoteMinutes,String description,String status,String submittedBy,Instant createdAt,String decidedBy,Instant decidedAt,String decisionReason,Long paymentId){}
    public record Attachment(long id,long requestId,String filename,String contentType,long sizeBytes,String sha256,String uploadedBy,Instant createdAt){}
    public record Detail(Request request,List<Attachment> attachments){}
    public record Download(Attachment metadata,byte[] bytes){}
    private static final String SELECT="SELECT r.*,i.name AS institution_name,p.folio AS policy_folio,u.display_name AS submitter,d.display_name AS decider FROM payment_requests r JOIN institutions i ON i.id=r.institution_id LEFT JOIN support_policies p ON p.id=r.policy_id JOIN app_users u ON u.id=r.submitted_by LEFT JOIN app_users d ON d.id=r.decided_by";
    private static final RowMapper<Request> ROW=(r,n)->new Request(r.getLong("id"),r.getLong("institution_id"),r.getString("institution_name"),r.getObject("policy_id",Long.class),r.getString("policy_folio"),r.getString("reference"),r.getBigDecimal("amount"),r.getString("currency"),r.getDate("paid_on").toLocalDate(),Models.PaymentMethod.valueOf(r.getString("method")),r.getLong("email_minutes"),r.getLong("remote_minutes"),r.getString("description"),r.getString("status"),r.getString("submitter"),r.getTimestamp("created_at").toInstant(),r.getString("decider"),r.getTimestamp("decided_at")==null?null:r.getTimestamp("decided_at").toInstant(),r.getString("decision_reason"),r.getObject("payment_id",Long.class));
    private static final String FILES="SELECT a.id,a.request_id,a.filename,a.content_type,a.size_bytes,a.sha256,a.created_at,u.display_name AS uploader FROM payment_request_attachments a JOIN app_users u ON u.id=a.uploaded_by";
    private static final RowMapper<Attachment> FILE=(r,n)->new Attachment(r.getLong("id"),r.getLong("request_id"),r.getString("filename"),r.getString("content_type"),r.getLong("size_bytes"),r.getString("sha256"),r.getString("uploader"),r.getTimestamp("created_at").toInstant());
    public List<Request> list(boolean client){Models.User actor=actor(client);return client?db.query(SELECT+" WHERE r.institution_id=? ORDER BY r.id DESC",ROW,actor.institutionId()).stream().map(PaymentRequestService::publicRequest).toList():db.query(SELECT+" ORDER BY r.id DESC",ROW);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Detail detail(long id,boolean client){Models.User actor=actor(client);Request request=authorized(id,actor);return detail(client?publicRequest(request):request);}
    public List<Attachment> attachments(long id,boolean client){Models.User actor=actor(client);authorized(id,actor);return files(id);}
    public Download download(long id,boolean client){Models.User actor=actor(client);List<Attachment> files=db.query(FILES+" WHERE a.id=?",FILE,id);if(files.isEmpty())throw ApiException.missing();Attachment file=files.get(0);authorized(file.requestId(),actor);return new Download(file,db.queryForObject("SELECT file_bytes FROM payment_request_attachments WHERE id=?",byte[].class,id));}

    @Transactional public Detail create(Input input,boolean client){
        Models.User actor=actor(client);UUID submission=validate(input);
        if(client&&!Objects.equals(actor.institutionId(),input.institutionId()))throw ApiException.forbidden();
        lockInstitution(input.institutionId());
        if(client){actor=access.requireClient();if(!Objects.equals(actor.institutionId(),input.institutionId()))throw ApiException.forbidden();}
        if(!Boolean.TRUE.equals(db.queryForObject("SELECT active FROM institutions WHERE id=?",Boolean.class,input.institutionId())))throw ApiException.conflict("La institución está dada de baja.");
        List<Request> existing=db.query(SELECT+" WHERE r.submitted_by=? AND r.submission_id=?",ROW,actor.id(),submission);
        if(!existing.isEmpty()){if(!same(existing.get(0),input))throw ApiException.conflict("El identificador de solicitud ya se utilizó con otros datos.");return detail(client?publicRequest(existing.get(0)):existing.get(0));}
        if(input.policyId()!=null){
            List<String> policies=db.query("SELECT status FROM support_policies WHERE id=? AND institution_id=? FOR UPDATE",(r,n)->r.getString(1),input.policyId(),input.institutionId());
            if(policies.isEmpty())throw ApiException.missing();if(!policies.get(0).equals("ACTIVE"))throw ApiException.conflict("La póliza está cancelada.");
        }
        Long id=db.queryForObject("""
            INSERT INTO payment_requests(submission_id,payment_request_id,institution_id,policy_id,reference,amount,paid_on,method,email_minutes,remote_minutes,description,submitted_by)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id
            """,Long.class,submission,UUID.randomUUID(),input.institutionId(),input.policyId(),clean(input.reference()),input.amount(),java.sql.Date.valueOf(input.paidOn()),input.method().name(),input.emailMinutes(),input.remoteMinutes(),clean(input.description()),actor.id());
        audit(actor,"CREATE_PAYMENT_REQUEST",id,"Solicitud pendiente; sin registrar dinero ni acreditar minutos.");return detail(client?publicRequest(authorized(id,actor)):authorized(id,actor));
    }

    @Transactional public Attachment upload(long id,String original,byte[] bytes,UUID submission,boolean client){
        Models.User actor=actor(client);Request request=lock(id,actor);
        if(submission==null)throw ApiException.bad("Indique el UUID del envío.");
        String filename=AttachmentService.filename(original),type=proofType(filename,bytes),sha=digest(bytes);
        List<Attachment> previous=db.query(FILES+" WHERE a.uploaded_by=? AND a.submission_id=?",FILE,actor.id(),submission);
        if(!previous.isEmpty()){Attachment old=previous.get(0);if(old.requestId()!=id||!old.filename().equals(filename)||!old.sha256().equals(sha))throw ApiException.conflict("El identificador de envío ya corresponde a otro archivo.");return old;}
        if(!request.status().equals("PENDING"))throw ApiException.conflict("La solicitud ya fue resuelta; su evidencia está congelada.");
        long count=db.queryForObject("SELECT COUNT(*) FROM payment_request_attachments WHERE request_id=?",Long.class,id),size=db.queryForObject("SELECT COALESCE(SUM(size_bytes),0) FROM payment_request_attachments WHERE request_id=?",Long.class,id);
        if(count>=10||size+bytes.length>50L*1024*1024)throw ApiException.conflict("La solicitud admite hasta 10 archivos o 50 MiB en total.");
        Long file=db.queryForObject("INSERT INTO payment_request_attachments(request_id,uploaded_by,submission_id,filename,content_type,size_bytes,sha256,file_bytes) VALUES(?,?,?,?,?,?,?,?) RETURNING id",Long.class,id,actor.id(),submission,filename,type,bytes.length,sha,bytes);
        audit(actor,"ATTACH_REQUEST_PROOF",id,"Comprobante #"+file+": "+filename+"; SHA-256="+sha+". Sin modificación de saldo.");return db.queryForObject(FILES+" WHERE a.id=?",FILE,file);
    }

    @Transactional public Detail approve(long id,String reason){
        Models.User actor=actor(false);Request request=lock(id,actor);String decision=text(reason,"Observación de aprobación",2000,false);
        if(request.status().equals("APPROVED"))return detail(request);
        if(!request.status().equals("PENDING"))throw ApiException.conflict("La solicitud ya fue rechazada.");
        List<Attachment> files=files(id);if(files.isEmpty())throw ApiException.conflict("Adjunte al menos un comprobante antes de aprobar la solicitud.");
        String key=db.queryForObject("SELECT payment_request_id::text FROM payment_requests WHERE id=?",String.class,id);
        Models.Payment payment=helpdesk.createPayment(new Models.PaymentInput(key,request.institutionId(),request.reference(),request.amount(),"MXN",request.paidOn(),request.method(),request.emailMinutes(),request.remoteMinutes(),request.description()+"\nValidado desde la solicitud #"+id+".",request.policyId()));
        for(Attachment file:files){
            byte[] bytes=db.queryForObject("SELECT file_bytes FROM payment_request_attachments WHERE id=?",byte[].class,file.id());
            UUID proofId=UUID.nameUUIDFromBytes(("payment-request-proof:"+file.id()).getBytes(StandardCharsets.UTF_8));
            proofs.upload(payment.id(),file.filename(),bytes,proofId);
        }
        db.update("UPDATE payment_requests SET status='APPROVED',decided_by=?,decided_at=NOW(),decision_reason=?,payment_id=? WHERE id=?",actor.id(),decision,payment.id(),id);
        audit(actor,"APPROVE_PAYMENT_REQUEST",id,"Solicitud aprobada; pago #"+payment.id()+" registrado una sola vez. "+decision);return detail(authorized(id,actor));
    }
    @Transactional public Detail reject(long id,String reason){
        Models.User actor=actor(false);Request request=lock(id,actor);String decision=text(reason,"Motivo de rechazo visible al cliente",2000,true);
        if(request.status().equals("REJECTED")){if(!decision.equals(request.decisionReason()))throw ApiException.conflict("La decisión ya está registrada y no puede modificarse.");return detail(request);}
        if(!request.status().equals("PENDING"))throw ApiException.conflict("La solicitud ya fue aprobada.");
        db.update("UPDATE payment_requests SET status='REJECTED',decided_by=?,decided_at=NOW(),decision_reason=? WHERE id=?",actor.id(),decision,id);
        audit(actor,"REJECT_PAYMENT_REQUEST",id,"Solicitud rechazada sin acreditar saldo. "+decision);return detail(authorized(id,actor));
    }
    private Models.User actor(boolean client){if(client)return access.requireClient();Models.User actor=helpdesk.me();if(actor.role()!=Models.Role.ADMIN&&actor.role()!=Models.Role.BILLING)throw ApiException.forbidden();return actor;}
    private Request authorized(long id,Models.User actor){List<Request> rows=db.query(SELECT+" WHERE r.id=?",ROW,id);if(rows.isEmpty())throw ApiException.missing();Request request=rows.get(0);if(actor.role()==Models.Role.USER&&!Objects.equals(actor.institutionId(),request.institutionId()))throw ApiException.missing();return request;}
    private Request lock(long id,Models.User actor){Request initial=authorized(id,actor);lockInstitution(initial.institutionId());if(initial.policyId()!=null)db.queryForObject("SELECT id FROM support_policies WHERE id=? FOR UPDATE",Long.class,initial.policyId());db.queryForObject("SELECT id FROM payment_requests WHERE id=? FOR UPDATE",Long.class,id);if(actor.role()==Models.Role.USER)actor=access.requireClient();return authorized(id,actor);}
    private void lockInstitution(long id){if(db.queryForList("SELECT id FROM institutions WHERE id=? FOR UPDATE",Long.class,id).isEmpty())throw ApiException.missing();}
    private static Request publicRequest(Request r){return new Request(r.id(),r.institutionId(),r.institutionName(),r.policyId(),r.policyFolio(),r.reference(),r.amount(),r.currency(),r.paidOn(),r.method(),r.emailMinutes(),r.remoteMinutes(),r.description(),r.status(),r.submittedBy(),r.createdAt(),null,r.decidedAt(),r.status().equals("REJECTED")?r.decisionReason():null,r.paymentId());}
    private Detail detail(Request request){return new Detail(request,files(request.id()));}
    private List<Attachment> files(long id){return db.query(FILES+" WHERE a.request_id=? ORDER BY a.id",FILE,id);}
    private void audit(Models.User actor,String action,long id,String details){db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,?,'PAYMENT_REQUEST',?,?)",actor.username(),action,id,details);}
    private static UUID validate(Input input){
        if(input==null||input.institutionId()==null||input.institutionId()<=0)throw ApiException.bad("Indique la institución.");
        UUID id;try{id=UUID.fromString(input.requestId());if(!id.toString().equalsIgnoreCase(input.requestId()))throw new IllegalArgumentException();}catch(Exception ex){throw ApiException.bad("Indique un UUID de solicitud válido.");}
        text(input.reference(),"Referencia",100,true);text(input.description(),"Descripción",8000,true);
        if(input.policyId()!=null&&input.policyId()<=0)throw ApiException.bad("Póliza inválida.");
        if(input.amount()==null||input.amount().signum()<0||input.amount().compareTo(new BigDecimal("999999999.99"))>0||input.amount().scale()>2)throw ApiException.bad("Importe inválido; use pesos mexicanos con máximo dos decimales.");
        for(Long minutes:new Long[]{input.emailMinutes(),input.remoteMinutes()})if(minutes==null||minutes<0||minutes>1000000000)throw ApiException.bad("Indique minutos enteros entre 0 y 1000000000.");
        if((input.policyId()==null&&(input.amount().signum()==0||(input.emailMinutes()==0&&input.remoteMinutes()==0)))||(input.amount().signum()==0&&input.emailMinutes()==0&&input.remoteMinutes()==0))throw ApiException.bad("Indique importe y minutos; con póliza puede solicitar un abono monetario o una acreditación pendiente.");
        if(input.method()==null||input.paidOn()==null||input.paidOn().getYear()<1||input.paidOn().getYear()>9999)throw ApiException.bad("Indique fecha y método de pago válidos.");return id;
    }
    private static boolean same(Request r,Input i){return r.institutionId()==i.institutionId()&&Objects.equals(r.policyId(),i.policyId())&&r.reference().equals(clean(i.reference()))&&r.amount().compareTo(i.amount())==0&&r.paidOn().equals(i.paidOn())&&r.method()==i.method()&&r.emailMinutes()==i.emailMinutes()&&r.remoteMinutes()==i.remoteMinutes()&&r.description().equals(clean(i.description()));}
    private static String proofType(String name,byte[] bytes){if(!List.of("pdf","png","jpg","jpeg").contains(name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT)))throw ApiException.bad("Use comprobantes PDF, PNG o JPG/JPEG.");try{return AttachmentService.contentType(name,bytes);}catch(ApiException ex){if(ex.status()!=400)throw ex;throw ApiException.bad("Comprobante vacío o contenido incompatible. Use PDF, PNG o JPG/JPEG.");}}
    private static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
    private static String clean(String s){return s==null?"":s.trim();}
    private static String text(String value,String label,int max,boolean required){String result=clean(value);if((required&&result.isBlank())||result.length()>max||result.indexOf('\0')>=0)throw ApiException.bad(label+" admite "+(required?"de 1 a ":"hasta ")+max+" caracteres.");return result;}
}
