package mx.siabuc.helpdesk;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuoteService {
    public record Input(Long institutionId,String folio,String description,LocalDate expiresOn,LocalDate validFrom,LocalDate validUntil,
        BigDecimal agreedAmount,Long emailMinutes,Long remoteMinutes,Long previousPolicyId,Long packageVersionId,String packageOverrideReason) {
        public Input(Long institutionId,String folio,String description,LocalDate expiresOn,LocalDate validFrom,LocalDate validUntil,BigDecimal agreedAmount,Long emailMinutes,Long remoteMinutes,Long previousPolicyId){this(institutionId,folio,description,expiresOn,validFrom,validUntil,agreedAmount,emailMinutes,remoteMinutes,previousPolicyId,null,null);}
    }
    public record Evidence(String evidence) {}
    public record Decision(String decision,String contactName,String evidence,LocalDate decidedOn) {}
    public record Conversion(String policyFolio) {}
    public record Quote(long id,long institutionId,String institutionName,String folio,String description,LocalDate expiresOn,
        LocalDate validFrom,LocalDate validUntil,BigDecimal agreedAmount,String currency,long emailMinutes,long remoteMinutes,
        String status,Long previousPolicyId,String previousPolicyFolio,Long convertedPolicyId,String convertedPolicyFolio,
        String createdBy,Instant createdAt,Instant updatedAt,Instant sentAt,String sentEvidence,String decisionContact,
        String decisionEvidence,LocalDate decidedOn,String decisionRecordedBy,Instant decisionRecordedAt,boolean expired) {}
    public record History(long id,long quoteId,String action,String description,String createdBy,Instant createdAt) {}
    public record Detail(Quote quote,List<History> history,SupportPackageService.Version packageSnapshot,String packageOverrideReason) {}
    private final JdbcTemplate db;private final HelpdeskService helpdesk;private final PolicyService policies;private final SupportPackageService packages;
    public QuoteService(JdbcTemplate db,HelpdeskService helpdesk,PolicyService policies,SupportPackageService packages){this.db=db;this.helpdesk=helpdesk;this.policies=policies;this.packages=packages;}
    private static final ZoneId ZONE=ZoneId.of("America/Mexico_City");
    private static final String SELECT="""
        SELECT q.*,i.name AS institution_name,u.username AS creator,a.username AS decision_actor,
          p.folio AS previous_folio,c.folio AS converted_folio
        FROM support_quotes q JOIN institutions i ON i.id=q.institution_id JOIN app_users u ON u.id=q.created_by
        LEFT JOIN app_users a ON a.id=q.decision_recorded_by LEFT JOIN support_policies p ON p.id=q.previous_policy_id
        LEFT JOIN support_policies c ON c.id=q.converted_policy_id
        """;
    private static final RowMapper<Quote> ROW=(r,n)->new Quote(r.getLong("id"),r.getLong("institution_id"),r.getString("institution_name"),r.getString("folio"),
        r.getString("description"),r.getDate("expires_on").toLocalDate(),r.getDate("valid_from").toLocalDate(),r.getDate("valid_until").toLocalDate(),
        r.getBigDecimal("agreed_amount"),r.getString("currency"),r.getLong("email_minutes"),r.getLong("remote_minutes"),r.getString("status"),
        r.getObject("previous_policy_id",Long.class),r.getString("previous_folio"),r.getObject("converted_policy_id",Long.class),r.getString("converted_folio"),
        r.getString("creator"),instant(r.getTimestamp("created_at")),instant(r.getTimestamp("updated_at")),instant(r.getTimestamp("sent_at")),r.getString("sent_evidence"),
        r.getString("decision_contact"),r.getString("decision_evidence"),r.getDate("decided_on")==null?null:r.getDate("decided_on").toLocalDate(),
        r.getString("decision_actor"),instant(r.getTimestamp("decision_recorded_at")),r.getDate("expires_on").toLocalDate().isBefore(today()));
    public List<Quote> list(Long institutionId){actor();if(institutionId!=null&&institutionId<=0)throw ApiException.bad("Institución inválida.");return institutionId==null
        ?db.query(SELECT+" ORDER BY q.id DESC",ROW):db.query(SELECT+" WHERE q.institution_id=? ORDER BY q.id DESC",ROW,institutionId);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Detail detail(long id){actor();return detailData(id);}
    private Detail detailData(long id){return new Detail(quote(id),db.query("SELECT h.*,u.username AS creator FROM quote_history h JOIN app_users u ON u.id=h.created_by WHERE h.quote_id=? ORDER BY h.id DESC",
        (r,n)->new History(r.getLong("id"),r.getLong("quote_id"),r.getString("action"),r.getString("description"),r.getString("creator"),instant(r.getTimestamp("created_at"))),id),packages.snapshot(packageVersion(id)),db.queryForObject("SELECT package_override_reason FROM support_quotes WHERE id=?",String.class,id));}
    @Transactional
    public Detail create(Input input){Models.User actor=actor();validate(input);lockInstitution(input.institutionId(),true);checkPrevious(input.previousPolicyId(),input.institutionId());unique(input.folio(),null);
        Long id=db.queryForObject("""
            INSERT INTO support_quotes(institution_id,folio,description,expires_on,valid_from,valid_until,agreed_amount,email_minutes,remote_minutes,previous_policy_id,created_by)
            VALUES(?,?,?,?,?,?,?,?,?,?,?) RETURNING id
            """,Long.class,input.institutionId(),clean(input.folio()),clean(input.description()),date(input.expiresOn()),date(input.validFrom()),date(input.validUntil()),
            input.agreedAmount(),input.emailMinutes(),input.remoteMinutes(),input.previousPolicyId(),actor.id());
        applyPackage(id,input,null);event(id,"CREATE_QUOTE",actor,"Borrador creado. "+snapshot(input)+packageDescription(id));return detailData(id);}
    @Transactional
    public Detail update(long id,Input input){Models.User actor=actor();validate(input);Quote q=lock(id,true);
        if(q.institutionId()!=input.institutionId()||!Objects.equals(q.previousPolicyId(),input.previousPolicyId()))throw ApiException.conflict("La institución y póliza anterior no pueden cambiarse. Cree otra cotización.");
        require(q,"DRAFT");unique(input.folio(),id);
        db.update("UPDATE support_quotes SET folio=?,description=?,expires_on=?,valid_from=?,valid_until=?,agreed_amount=?,email_minutes=?,remote_minutes=?,updated_at=NOW() WHERE id=?",
            clean(input.folio()),clean(input.description()),date(input.expiresOn()),date(input.validFrom()),date(input.validUntil()),input.agreedAmount(),input.emailMinutes(),input.remoteMinutes(),id);
        applyPackage(id,input,packageVersion(id));event(id,"UPDATE_QUOTE",actor,"Borrador actualizado. Antes: "+snapshot(asInput(q))+"\nDespués: "+snapshot(input)+packageDescription(id));return detailData(id);}
    @Transactional
    public Detail send(long id,Evidence input){Models.User actor=actor();required(input==null?null:input.evidence(),"Constancia de emisión",10000);Quote q=lock(id,true);require(q,"DRAFT");
        if(q.expiresOn().isBefore(today()))throw ApiException.conflict("La oferta ya venció. Actualice la fecha del borrador antes de emitirla.");
        db.update("UPDATE support_quotes SET status='SENT',sent_at=NOW(),sent_evidence=?,updated_at=NOW() WHERE id=?",clean(input.evidence()),id);
        event(id,"SEND_QUOTE",actor,"Emisión registrada y términos congelados. Constancia: "+clean(input.evidence())+". No implica envío automático de correo.");return detailData(id);}
    @Transactional
    public Detail decide(long id,Decision input){Models.User actor=actor();if(input==null||input.decision()==null||!List.of("ACCEPTED","REJECTED").contains(input.decision()))throw ApiException.bad("Indique aceptación o rechazo.");
        required(input.contactName(),"Contacto que respondió",180);required(input.evidence(),"Constancia de respuesta",10000);validDate(input.decidedOn());Quote q=lock(id,false);require(q,"SENT");
        if(input.decidedOn().isBefore(q.sentAt().atZone(ZONE).toLocalDate())||input.decidedOn().isAfter(today()))throw ApiException.bad("La fecha de respuesta debe estar entre la emisión y hoy.");
        if(input.decision().equals("ACCEPTED")&&input.decidedOn().isAfter(q.expiresOn()))throw ApiException.conflict("La aceptación está fuera de la vigencia de la oferta. Emita una nueva cotización.");
        db.update("UPDATE support_quotes SET status=?,decision_contact=?,decision_evidence=?,decided_on=?,decision_recorded_by=?,decision_recorded_at=NOW(),updated_at=NOW() WHERE id=?",
            input.decision(),clean(input.contactName()),clean(input.evidence()),date(input.decidedOn()),actor.id(),id);
        event(id,input.decision().equals("ACCEPTED")?"ACCEPT_QUOTE":"REJECT_QUOTE",actor,(input.decision().equals("ACCEPTED")?"Aceptación":"Rechazo")
            +" registrado por el personal en nombre del cliente. Contacto: "+clean(input.contactName())+"; fecha: "+input.decidedOn()+". Constancia: "+clean(input.evidence())+". Este registro no es una firma digital.");return detailData(id);}
    @Transactional
    public Detail convert(long id,Conversion input){Models.User actor=actor();required(input==null?null:input.policyFolio(),"Folio de póliza",100);Quote q=lock(id,false);
        if(q.status().equals("CONVERTED")){if(!db.queryForObject("SELECT conversion_folio FROM support_quotes WHERE id=?",String.class,id).equals(clean(input.policyFolio())))throw ApiException.conflict("La cotización ya generó una póliza con otro folio.");return detailData(id);}
        require(q,"ACCEPTED");lockInstitution(q.institutionId(),true);
        if(q.previousPolicyId()!=null&&db.queryForObject("SELECT COUNT(*) FROM policy_renewals WHERE previous_policy_id=?",Long.class,q.previousPolicyId())>0)
            throw ApiException.conflict("La póliza anterior ya tiene una renovación. Consulte la nueva póliza antes de crear otra renovación.");
        PolicyService.Detail created=policies.create(new PolicyService.PolicyInput(q.institutionId(),clean(input.policyFolio()),q.description(),q.validFrom(),q.validUntil(),q.agreedAmount(),q.emailMinutes(),q.remoteMinutes(),q.previousPolicyId()==null?null:policies.policy(q.previousPolicyId()).licenseId()),actor);
        long policyId=created.policy().id();db.update("UPDATE support_quotes SET status='CONVERTED',converted_policy_id=?,conversion_folio=?,updated_at=NOW() WHERE id=?",policyId,clean(input.policyFolio()),id);
        if(q.previousPolicyId()!=null)db.update("INSERT INTO policy_renewals(institution_id,previous_policy_id,new_policy_id,quote_id,created_by) VALUES(?,?,?,?,?)",q.institutionId(),q.previousPolicyId(),policyId,id,actor.id());
        event(id,"CONVERT_QUOTE",actor,"Póliza "+created.policy().folio()+" creada desde cotización aceptada"+(q.previousPolicyId()==null?"":"; renueva la póliza "+q.previousPolicyFolio())+". No se registraron pagos ni se acreditaron minutos.");
        return detailData(id);}
    private Long packageVersion(long id){return db.queryForObject("SELECT package_version_id FROM support_quotes WHERE id=?",Long.class,id);}
    private void applyPackage(long id,Input input,Long retained){Long requested=input.packageVersionId()==null?retained:input.packageVersionId();if(requested==null){if(input.packageOverrideReason()!=null&&!input.packageOverrideReason().isBlank())throw ApiException.bad("El motivo de ajuste requiere un paquete.");return;}
        SupportPackageService.Version v=packages.forQuote(requested,retained);boolean changed=v.amount().compareTo(input.agreedAmount())!=0||v.emailMinutes()!=input.emailMinutes()||v.remoteMinutes()!=input.remoteMinutes()||!v.description().equals(clean(input.description()));
        String reason=clean(input.packageOverrideReason());if(changed)required(reason,"Motivo de ajuste a los términos del paquete",2000);else if(reason.length()>2000)throw ApiException.bad("El motivo admite hasta 2000 caracteres.");
        db.update("UPDATE support_quotes SET package_version_id=?,package_override_reason=? WHERE id=?",requested,changed?reason:null,id);
    }
    private String packageDescription(long id){SupportPackageService.Version v=packages.snapshot(packageVersion(id));if(v==null)return "";String reason=db.queryForObject("SELECT package_override_reason FROM support_quotes WHERE id=?",String.class,id);return " Paquete: "+v.code()+" revisión "+v.revision()+"; tarifa original $"+v.amount()+" MXN, correo "+v.emailMinutes()+" min, remoto "+v.remoteMinutes()+" min."+(reason==null?" Sin ajustes.":" Términos ajustados: "+reason);}
    private Quote quote(long id){List<Quote> rows=db.query(SELECT+" WHERE q.id=?",ROW,id);if(rows.isEmpty())throw ApiException.missing();return rows.get(0);}
    private Quote lock(long id,boolean active){Quote q=quote(id);lockInstitution(q.institutionId(),active);checkPrevious(q.previousPolicyId(),q.institutionId());db.queryForObject("SELECT id FROM support_quotes WHERE id=? FOR UPDATE",Long.class,id);return quote(id);}
    private void lockInstitution(long id,boolean active){List<Boolean> rows=db.query("SELECT active FROM institutions WHERE id=? FOR UPDATE",(r,n)->r.getBoolean(1),id);if(rows.isEmpty())throw ApiException.missing();if(active&&!rows.get(0))throw ApiException.conflict("La institución está dada de baja.");}
    private void checkPrevious(Long id,long institution){if(id==null)return;policies.lockForAccounting(id,institution);}
    private void unique(String folio,Long except){Long count=except==null?db.queryForObject("SELECT COUNT(*) FROM support_quotes WHERE LOWER(BTRIM(folio))=LOWER(?)",Long.class,clean(folio))
        :db.queryForObject("SELECT COUNT(*) FROM support_quotes WHERE LOWER(BTRIM(folio))=LOWER(?) AND id<>?",Long.class,clean(folio),except);if(count>0)throw ApiException.conflict("El folio de cotización ya existe.");}
    private void event(long id,String action,Models.User actor,String description){db.update("INSERT INTO quote_history(quote_id,action,description,created_by) VALUES(?,?,?,?)",id,action,description,actor.id());
        db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES(?,?,'QUOTE',?,?)",actor.username(),action,id,description);}
    private Models.User actor(){Models.User u=helpdesk.me();if(u.role()!=Models.Role.ADMIN&&u.role()!=Models.Role.BILLING)throw ApiException.forbidden();return u;}
    private static void require(Quote q,String status){if(!q.status().equals(status))throw ApiException.conflict("Esta operación no corresponde al estado actual de la cotización.");}
    private static void validate(Input i){if(i==null||i.institutionId()==null||i.institutionId()<=0)throw ApiException.bad("Seleccione la institución.");required(i.folio(),"Folio",100);required(i.description(),"Descripción",10000);
        validDate(i.expiresOn());validDate(i.validFrom());validDate(i.validUntil());if(i.validUntil().isBefore(i.validFrom()))throw ApiException.bad("La vigencia final no puede ser anterior al inicio.");
        if(i.agreedAmount()==null||i.agreedAmount().signum()<=0||i.agreedAmount().compareTo(new BigDecimal("999999999.99"))>0||i.agreedAmount().scale()>2)throw ApiException.bad("El importe debe ser positivo, con hasta dos decimales y máximo 999999999.99 MXN.");
        for(Long m:new Long[]{i.emailMinutes(),i.remoteMinutes()})if(m==null||m<0||m>HelpdeskService.MAX_MINUTES)throw ApiException.bad("Indique minutos enteros entre 0 y 1000000000.");
        if(i.emailMinutes()==0&&i.remoteMinutes()==0)throw ApiException.bad("Cotice tiempo en al menos un canal.");if(i.previousPolicyId()!=null&&i.previousPolicyId()<=0)throw ApiException.bad("Póliza anterior inválida.");}
    private static void validDate(LocalDate d){if(d==null||d.getYear()<1||d.getYear()>9999)throw ApiException.bad("Indique una fecha válida entre los años 0001 y 9999.");}
    private static Input asInput(Quote q){return new Input(q.institutionId(),q.folio(),q.description(),q.expiresOn(),q.validFrom(),q.validUntil(),q.agreedAmount(),q.emailMinutes(),q.remoteMinutes(),q.previousPolicyId());}
    private static String snapshot(Input i){return "Folio: "+clean(i.folio())+"; oferta hasta: "+i.expiresOn()+"; cobertura: "+i.validFrom()+" a "+i.validUntil()+"; importe: $"+i.agreedAmount().toPlainString()+" MXN; correo: "+i.emailMinutes()+" min; remoto: "+i.remoteMinutes()+" min; póliza anterior: "+i.previousPolicyId()+". Descripción: "+clean(i.description());}
    private static void required(String value,String name,int max){if(value==null||value.isBlank()||value.length()>max||value.indexOf('\0')>=0)throw ApiException.bad(name+" es obligatorio y admite hasta "+max+" caracteres.");}
    private static String clean(String s){return s==null?"":s.strip();}private static java.sql.Date date(LocalDate d){return java.sql.Date.valueOf(d);}private static LocalDate today(){return LocalDate.now(ZONE);}private static Instant instant(Timestamp t){return t==null?null:t.toInstant();}
}
