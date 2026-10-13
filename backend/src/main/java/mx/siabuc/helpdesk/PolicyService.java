package mx.siabuc.helpdesk;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** JDBC-only policy ledger. Controllers authorize role; mutations always lock institution before policy. */
@Service
public class PolicyService {
    public record PolicyInput(Long institutionId,String folio,String description,LocalDate validFrom,LocalDate validUntil,
            BigDecimal agreedAmount,Long emailMinutes,Long remoteMinutes) {}
    public record Policy(long id,long institutionId,String institutionName,String folio,String description,
            LocalDate validFrom,LocalDate validUntil,BigDecimal agreedAmount,String currency,long emailMinutes,long remoteMinutes,
            String status,String validity,BigDecimal paidAmount,BigDecimal remainingAmount,long creditedEmailMinutes,
            long creditedRemoteMinutes,long remainingEmailMinutes,long remainingRemoteMinutes,long paymentCount,long activePaymentCount,
            String createdBy,Instant createdAt,Instant updatedAt,String cancelReason,String cancelledBy,Instant cancelledAt) {}
    public record History(long id,long policyId,String action,String description,Long paymentId,String createdBy,Instant createdAt) {}
    public record Detail(Policy policy,List<History> history,List<Models.Payment> payments) {}
    private final JdbcTemplate db;
    public PolicyService(JdbcTemplate db) { this.db=db; }
    private static final String SELECT="""
        SELECT s.*,i.name AS institution_name,u.username AS creator,v.username AS canceller,
          COALESCE(t.paid_amount,0) AS paid_amount,COALESCE(t.credited_email,0) AS credited_email,
          COALESCE(t.credited_remote,0) AS credited_remote,COALESCE(t.payment_count,0) AS payment_count,
          COALESCE(t.active_payment_count,0) AS active_payment_count
        FROM support_policies s JOIN institutions i ON i.id=s.institution_id
        JOIN app_users u ON u.id=s.created_by LEFT JOIN app_users v ON v.id=s.cancelled_by
        LEFT JOIN (SELECT policy_id,SUM(CASE WHEN NOT voided THEN amount ELSE 0 END) AS paid_amount,
          SUM(CASE WHEN NOT voided THEN email_minutes ELSE 0 END) AS credited_email,
          SUM(CASE WHEN NOT voided THEN remote_minutes ELSE 0 END) AS credited_remote,
          COUNT(*) AS payment_count,COUNT(*) FILTER(WHERE NOT voided) AS active_payment_count
          FROM payments WHERE policy_id IS NOT NULL GROUP BY policy_id) t ON t.policy_id=s.id
        """;
    private static final RowMapper<Policy> POLICY=(r,n)->{
        LocalDate from=r.getDate("valid_from").toLocalDate(),until=r.getDate("valid_until").toLocalDate(),today=LocalDate.now(ZoneId.of("America/Mexico_City"));
        BigDecimal amount=r.getBigDecimal("agreed_amount"),paid=r.getBigDecimal("paid_amount");
        long email=r.getLong("email_minutes"),remote=r.getLong("remote_minutes"),emailPaid=r.getLong("credited_email"),remotePaid=r.getLong("credited_remote");
        return new Policy(r.getLong("id"),r.getLong("institution_id"),r.getString("institution_name"),r.getString("folio"),r.getString("description"),
            from,until,amount,r.getString("currency"),email,remote,r.getString("status"),today.isBefore(from)?"UPCOMING":today.isAfter(until)?"EXPIRED":"CURRENT",
            paid,amount.subtract(paid),emailPaid,remotePaid,email-emailPaid,remote-remotePaid,r.getLong("payment_count"),r.getLong("active_payment_count"),
            r.getString("creator"),instant(r.getTimestamp("created_at")),instant(r.getTimestamp("updated_at")),r.getString("cancel_reason"),
            r.getString("canceller"),instant(r.getTimestamp("cancelled_at")));
    };
    public List<Policy> list(Long institutionId) {
        if(institutionId!=null && institutionId<=0) throw ApiException.bad("Identificador de institución inválido.");
        return institutionId==null ? db.query(SELECT+" ORDER BY s.id DESC",POLICY)
            : db.query(SELECT+" WHERE s.institution_id=? ORDER BY s.id DESC",POLICY,institutionId);
    }
    public Policy policy(long id) {
        List<Policy> rows=db.query(SELECT+" WHERE s.id=?",POLICY,id);
        if(rows.isEmpty()) throw ApiException.missing();return rows.get(0);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Detail detail(long id) {
        Policy p=policy(id);
        List<History> history=db.query("""
            SELECT h.*,u.username AS creator FROM policy_history h JOIN app_users u ON u.id=h.created_by WHERE h.policy_id=? ORDER BY h.id DESC
            """,(r,n)->new History(r.getLong("id"),r.getLong("policy_id"),r.getString("action"),r.getString("description"),
                r.getObject("payment_id",Long.class),r.getString("creator"),instant(r.getTimestamp("created_at"))),id);
        List<Models.Payment> payments=db.query(HelpdeskService.PAYMENT_SELECT+" WHERE p.policy_id=? ORDER BY p.id DESC",HelpdeskService.PAYMENT,id);
        return new Detail(p,history,payments);
    }
    @Transactional
    public Detail create(PolicyInput input,Models.User actor) {
        validate(input);lockInstitution(input.institutionId(),true);uniqueFolio(input.folio(),null);
        Long id=db.queryForObject("""
            INSERT INTO support_policies(institution_id,folio,description,valid_from,valid_until,agreed_amount,email_minutes,remote_minutes,created_by)
            VALUES(?,?,?,?,?,?,?,?,?) RETURNING id
            """,Long.class,input.institutionId(),clean(input.folio()),clean(input.description()),java.sql.Date.valueOf(input.validFrom()),
            java.sql.Date.valueOf(input.validUntil()),input.agreedAmount(),input.emailMinutes(),input.remoteMinutes(),actor.id());
        event(id,"CREATE_POLICY",null,actor,"Póliza creada. "+snapshot(input));
        return detail(id);
    }
    @Transactional
    public Detail update(long id,PolicyInput input,Models.User actor) {
        validate(input);Policy initial=policy(id);
        if(initial.institutionId()!=input.institutionId()) throw ApiException.conflict("No puede cambiarse la institución de una póliza existente.");
        lockInstitution(initial.institutionId(),true);lockForAccounting(id,initial.institutionId());Policy p=policy(id);
        if(!p.status().equals("ACTIVE")) throw ApiException.conflict("La póliza está cancelada y no puede modificarse.");
        if(p.paymentCount()>0) throw ApiException.conflict("La póliza ya tiene historial de pagos, incluso si fueron anulados. Su contrato no puede modificarse.");
        uniqueFolio(input.folio(),id);
        db.update("""
            UPDATE support_policies SET folio=?,description=?,valid_from=?,valid_until=?,agreed_amount=?,email_minutes=?,remote_minutes=?,updated_at=NOW() WHERE id=?
            """,clean(input.folio()),clean(input.description()),java.sql.Date.valueOf(input.validFrom()),java.sql.Date.valueOf(input.validUntil()),
            input.agreedAmount(),input.emailMinutes(),input.remoteMinutes(),id);
        event(id,"UPDATE_POLICY",null,actor,"Póliza actualizada. Antes: "+snapshot(asInput(p))+"\nDespués: "+snapshot(input));
        return detail(id);
    }
    @Transactional
    public Detail cancel(long id,String reason,Models.User actor) {
        required(reason,"Motivo de cancelación",10000);Policy initial=policy(id);
        lockInstitution(initial.institutionId(),false);lockForAccounting(id,initial.institutionId());Policy p=policy(id);
        if(!p.status().equals("ACTIVE")) throw ApiException.conflict("La póliza ya está cancelada.");
        if(p.activePaymentCount()>0) throw ApiException.conflict("No puede cancelar una póliza con pagos vigentes. Un administrador debe anularlos primero si procede.");
        db.update("UPDATE support_policies SET status='CANCELLED',cancel_reason=?,cancelled_by=?,cancelled_at=NOW(),updated_at=NOW() WHERE id=?",clean(reason),actor.id(),id);
        event(id,"CANCEL_POLICY",null,actor,"Póliza "+p.folio()+" cancelada. Motivo: "+clean(reason)+". No se eliminó el historial ni se modificaron saldos.");
        return detail(id);
    }
    /** Caller holds the institution row lock; does not lock payment rows before this call. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void lockForAccounting(long policyId,long institutionId) {
        List<Long> matches=db.query("SELECT institution_id FROM support_policies WHERE id=? FOR UPDATE",(r,n)->r.getLong(1),policyId);
        if(matches.isEmpty()) throw ApiException.missing();
        if(matches.get(0)!=institutionId) throw ApiException.conflict("La póliza no pertenece a la institución indicada en el pago.");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void validateInstallment(Models.PaymentInput input) {
        if(input.policyId()==null)return;
        lockForAccounting(input.policyId(),input.institutionId());Policy p=policy(input.policyId());
        if(!p.status().equals("ACTIVE")) throw ApiException.conflict("La póliza está cancelada; no admite nuevos pagos ni acreditaciones.");
        if(input.amount().compareTo(p.remainingAmount())>0 || input.emailMinutes()>p.remainingEmailMinutes() || input.remoteMinutes()>p.remainingRemoteMinutes())
            throw ApiException.conflict("El movimiento excede el importe o los minutos pendientes de acreditar de la póliza.");
        if(input.amount().signum()==0 && p.paidAmount().compareTo(p.agreedAmount())!=0)
            throw ApiException.conflict("Para acreditar minutos sin un nuevo cobro, la póliza debe estar liquidada monetariamente. Registre primero los abonos pendientes.");
    }
    /** A zero-amount credit is backed by monetary settlement, so its supporting money cannot disappear first. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void validatePaymentVoid(Models.Payment payment) {
        if(payment.policyId()==null || payment.amount().signum()==0)return;
        Policy p=policy(payment.policyId());
        if(p.paidAmount().subtract(payment.amount()).compareTo(p.agreedAmount())>=0)return;
        Long dependent=db.queryForObject("SELECT COUNT(*) FROM payments WHERE policy_id=? AND NOT voided AND amount=0",Long.class,p.id());
        if(dependent>0)throw ApiException.conflict("La póliza tiene acreditaciones sin nuevo cobro respaldadas por su liquidación. Anule primero esas acreditaciones, si conservan saldo suficiente, antes de anular este pago.");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void paymentEvent(Models.Payment payment,Models.User actor,boolean voided,String reason) {
        if(payment.policyId()==null)return;
        String details=(voided?"Pago anulado":"Pago o acreditación registrado")+". Referencia: "+payment.reference()+"; importe: $"+payment.amount().toPlainString()
            +" "+payment.currency()+"; método: "+methodLabel(payment.method())+"; correo: "+payment.emailMinutes()+" min; remoto: "+payment.remoteMinutes()+" min. "
            +(voided?"Motivo: "+clean(reason):"Descripción: "+payment.notes());
        db.update("UPDATE support_policies SET updated_at=NOW() WHERE id=?",payment.policyId());
        event(payment.policyId(),voided?"VOID_PAYMENT":"CREATE_PAYMENT",payment.id(),actor,details);
    }
    public static String methodLabel(Models.PaymentMethod method) {
        return switch(method) {
            case TRANSFER -> "Transferencia";case DEPOSIT -> "Depósito";case CASH -> "Efectivo";
            case CREDIT_CARD -> "Tarjeta de crédito";case DEBIT_CARD -> "Tarjeta de débito";
            case CHECK -> "Cheque";case OTHER -> "Otro";case CARD -> "Tarjeta (registro compatible)";
        };
    }
    private void lockInstitution(long id,boolean requireActive) {
        List<Boolean> rows=db.query("SELECT active FROM institutions WHERE id=? FOR UPDATE",(r,n)->r.getBoolean(1),id);
        if(rows.isEmpty())throw ApiException.missing();if(requireActive&&!rows.get(0))throw ApiException.conflict("La institución está dada de baja.");
    }
    private void uniqueFolio(String folio,Long exceptId) {
        Long count=exceptId==null?db.queryForObject("SELECT COUNT(*) FROM support_policies WHERE LOWER(BTRIM(folio))=LOWER(?)",Long.class,clean(folio))
            :db.queryForObject("SELECT COUNT(*) FROM support_policies WHERE LOWER(BTRIM(folio))=LOWER(?) AND id<>?",Long.class,clean(folio),exceptId);
        if(count>0)throw ApiException.conflict("El folio de póliza ya existe, incluso si la póliza fue cancelada.");
    }
    private void event(long policyId,String action,Long paymentId,Models.User actor,String description) {
        db.update("INSERT INTO policy_history(policy_id,action,description,payment_id,created_by) VALUES(?,?,?,?,?)",policyId,action,description,paymentId,actor.id());
        db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES(?,?, 'POLICY',?,?)",actor.username(),action,policyId,description);
    }
    private static void validate(PolicyInput p) {
        if(p==null || p.institutionId()==null || p.institutionId()<=0)throw ApiException.bad("Indique la institución de la póliza.");
        required(p.folio(),"Folio",100);required(p.description(),"Descripción",10000);
        if(p.validFrom()==null || p.validUntil()==null || p.validFrom().getYear()<1 || p.validFrom().getYear()>9999 || p.validUntil().getYear()<1 || p.validUntil().getYear()>9999 || p.validUntil().isBefore(p.validFrom()))
            throw ApiException.bad("Indique fechas válidas de inicio y fin; el fin no puede ser anterior al inicio.");
        if(p.agreedAmount()==null || p.agreedAmount().signum()<=0 || p.agreedAmount().compareTo(new BigDecimal("999999999.99"))>0 || p.agreedAmount().scale()>2)
            throw ApiException.bad("El importe contratado debe ser positivo, no exceder 999999999.99 MXN y tener como máximo dos decimales.");
        for(Long minutes:new Long[]{p.emailMinutes(),p.remoteMinutes()})if(minutes==null || minutes<0 || minutes>HelpdeskService.MAX_MINUTES)
            throw ApiException.bad("Los minutos contratados deben ser enteros de 0 a 1000000000.");
        if(p.emailMinutes()==0 && p.remoteMinutes()==0)throw ApiException.bad("La póliza debe contratar minutos en al menos un canal.");
    }
    private static PolicyInput asInput(Policy p) { return new PolicyInput(p.institutionId(),p.folio(),p.description(),p.validFrom(),p.validUntil(),p.agreedAmount(),p.emailMinutes(),p.remoteMinutes()); }
    private static String snapshot(PolicyInput p) {
        return "Folio: "+clean(p.folio())+"; vigencia: "+p.validFrom()+" a "+p.validUntil()+"; importe contratado: $"+p.agreedAmount().toPlainString()
            +" MXN; correo: "+p.emailMinutes()+" min; remoto: "+p.remoteMinutes()+" min. Descripción: "+clean(p.description());
    }
    private static void required(String value,String field,int max) { if(value==null || value.isBlank() || value.length()>max || value.indexOf('\0')>=0)throw ApiException.bad(field+" es obligatorio y admite hasta "+max+" caracteres."); }
    private static String clean(String value){return value==null?"":value.trim();}
    private static Instant instant(Timestamp value){return value==null?null:value.toInstant();}
}
