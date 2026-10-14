package mx.siabuc.helpdesk;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankReconciliationService {
    private final JdbcTemplate db;private final HelpdeskService helpdesk;
    public BankReconciliationService(JdbcTemplate db,HelpdeskService helpdesk){this.db=db;this.helpdesk=helpdesk;}
    public record ImportSummary(long id,String account,String filename,int rowCount,int newCount,String createdBy,Instant createdAt){}
    public record PreviewRow(int rowNumber,LocalDate date,String reference,BigDecimal amount,String currency,Long existingMovementId){}
    public record Preview(String account,String hash,Long existingImportId,int rowCount,int newCount,List<PreviewRow> rows){}
    public record Movement(long id,String account,LocalDate date,String reference,BigDecimal amount,String currency,Long paymentId,String paymentReference,String institutionName,boolean paymentVoided,String matchedBy,Instant matchedAt){}
    public record ImportDetail(ImportSummary summary,List<Movement> movements){}
    public record Candidate(long id,long institutionId,String institutionName,String reference,BigDecimal amount,String currency,LocalDate paidOn,boolean referenceMatch,int dateDifferenceDays){}
    public record Candidates(int page,int pageSize,long total,List<Candidate> items){}
    public record Event(long id,String action,long paymentId,String reason,String actor,Instant createdAt){}
    public record Detail(Movement movement,List<Event> history){}
    private record Payment(long id,long institutionId,Long policyId,BigDecimal amount,String currency,boolean voided){}
    private static final String MOVEMENTS="""
        SELECT m.*,p.reference AS payment_reference,p.voided AS payment_voided,i.name AS institution_name,u.display_name AS matched_name
        FROM bank_movements m LEFT JOIN payments p ON p.id=m.matched_payment_id
        LEFT JOIN institutions i ON i.id=p.institution_id LEFT JOIN app_users u ON u.id=m.matched_by
        """;
    public List<ImportSummary> imports(){staff();return db.query("""
        SELECT b.*,u.display_name AS author FROM bank_imports b JOIN app_users u ON u.id=b.created_by
        ORDER BY b.id DESC LIMIT 200
        """,this::importRow);}
    public Preview preview(String account,byte[] bytes){staff();return preview(BankCsv.parse(account,bytes));}
    private Preview preview(BankCsv.Parsed parsed){
        List<Long> existing=db.queryForList("SELECT id FROM bank_imports WHERE content_hash=?",Long.class,parsed.hash());
        List<PreviewRow> rows=parsed.rows().stream().map(r->{List<Long> ids=db.queryForList("SELECT id FROM bank_movements WHERE movement_hash=?",Long.class,r.hash());return new PreviewRow(r.rowNumber(),r.date(),r.reference(),r.amount(),r.currency(),ids.isEmpty()?null:ids.get(0));}).toList();
        return new Preview(parsed.account(),parsed.hash(),existing.isEmpty()?null:existing.get(0),rows.size(),(int)rows.stream().filter(r->r.existingMovementId()==null).count(),rows);
    }
    @Transactional
    public ImportDetail importCsv(String account,String filename,byte[] bytes,String expectedHash){
        Models.User actor=staff();BankCsv.Parsed parsed=BankCsv.parse(account,bytes);
        if(!Objects.equals(parsed.hash(),expectedHash))throw ApiException.conflict("El archivo o la cuenta cambió. Genera otra vista previa antes de importar.");
        String cleanFilename=BankCsv.text(filename==null?"movimientos.csv":filename.replace('\\','/').substring(filename.replace('\\','/').lastIndexOf('/')+1),200,"Nombre de archivo");
        List<Long> inserted=db.queryForList("""
            INSERT INTO bank_imports(account_label,account_key,content_hash,filename,row_count,new_count,created_by)
            VALUES(?,?,?,?,?,0,?) ON CONFLICT(content_hash) DO NOTHING RETURNING id
            """,Long.class,parsed.account(),parsed.accountKey(),parsed.hash(),cleanFilename,parsed.rows().size(),actor.id());
        if(inserted.isEmpty())return importDetail(db.queryForObject("SELECT id FROM bank_imports WHERE content_hash=?",Long.class,parsed.hash()));
        long id=inserted.get(0);int added=0;
        // Deterministic conflict acquisition order also supports overlapping concurrent CSVs.
        for(BankCsv.Row row:parsed.rows().stream().sorted(Comparator.comparing(BankCsv.Row::hash)).toList()){
            List<Long> movement=db.queryForList("""
                INSERT INTO bank_movements(first_import_id,account_label,account_key,movement_hash,booked_on,reference,reference_key,amount,currency)
                VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(movement_hash) DO NOTHING RETURNING id
                """,Long.class,id,parsed.account(),parsed.accountKey(),row.hash(),java.sql.Date.valueOf(row.date()),row.reference(),row.referenceKey(),row.amount(),row.currency());
            long movementId;if(movement.isEmpty())movementId=db.queryForObject("SELECT id FROM bank_movements WHERE movement_hash=?",Long.class,row.hash());else{movementId=movement.get(0);added++;}
            db.update("INSERT INTO bank_import_rows(import_id,row_number,movement_id) VALUES(?,?,?)",id,row.rowNumber(),movementId);
        }
        db.update("UPDATE bank_imports SET new_count=? WHERE id=?",added,id);
        audit(actor,"IMPORT_BANK_CSV",id,"CSV validado: "+parsed.rows().size()+" registros; "+added+" nuevos. Cuenta: "+parsed.account()+". No crea pagos ni acredita minutos.");
        return importDetail(id);
    }
    public ImportDetail importDetail(long id){staff();List<ImportSummary> result=db.query("SELECT b.*,u.display_name AS author FROM bank_imports b JOIN app_users u ON u.id=b.created_by WHERE b.id=?",this::importRow,id);if(result.isEmpty())throw ApiException.missing();
        return new ImportDetail(result.get(0),db.query(MOVEMENTS+" JOIN bank_import_rows br ON br.movement_id=m.id WHERE br.import_id=? ORDER BY br.row_number",this::movementRow,id));}
    public Detail detail(long id){staff();Movement movement=movement(id);return new Detail(movement,db.query("""
        SELECT e.*,u.display_name AS author FROM bank_reconciliation_events e JOIN app_users u ON u.id=e.actor_id
        WHERE movement_id=? ORDER BY e.id DESC
        """,(r,n)->new Event(r.getLong("id"),r.getString("action"),r.getLong("payment_id"),r.getString("reason"),r.getString("author"),r.getTimestamp("created_at").toInstant()),id));}
    public Candidates candidates(long id,String query,int page){
        staff();Movement movement=movement(id);if(page<0||page>100000)throw ApiException.bad("Página inválida.");
        String q=query==null?"":query.trim();if(q.length()>100||q.codePoints().anyMatch(Character::isISOControl))throw ApiException.bad("La búsqueda admite hasta 100 caracteres, sin controles.");
        String pattern="%"+q.toLowerCase(java.util.Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        String where="""
            FROM payments p JOIN institutions i ON i.id=p.institution_id
            WHERE NOT p.voided AND p.amount=? AND p.currency=?
              AND NOT EXISTS(SELECT 1 FROM bank_movements bm WHERE bm.matched_payment_id=p.id)
              AND (LOWER(p.reference) LIKE ? ESCAPE E'\\\\' OR LOWER(i.name) LIKE ? ESCAPE E'\\\\' OR p.id::text=?)
            """;
        Object[] filters={movement.amount(),movement.currency(),pattern,pattern,q};
        long count=db.queryForObject("SELECT COUNT(*) "+where,Long.class,filters);
        List<Candidate> items=db.query("SELECT p.id,p.institution_id,i.name,p.reference,p.amount,p.currency,p.paid_on,LOWER(BTRIM(p.reference))=? AS reference_match,ABS(p.paid_on-?::date) AS date_difference "+where+" ORDER BY reference_match DESC,date_difference,p.id DESC LIMIT 20 OFFSET ?",
            (r,n)->new Candidate(r.getLong("id"),r.getLong("institution_id"),r.getString("name"),r.getString("reference"),r.getBigDecimal("amount"),r.getString("currency"),r.getDate("paid_on").toLocalDate(),r.getBoolean("reference_match"),r.getInt("date_difference")),
            BankCsv.key(movement.reference()),java.sql.Date.valueOf(movement.date()),movement.amount(),movement.currency(),pattern,pattern,q,page*20);
        return new Candidates(page,20,count,items);
    }
    @Transactional
    public Detail match(long id,long paymentId,String reason){
        Models.User actor=staff();String cleanReason=reason(reason);Payment initial=payment(paymentId);
        // Same institution -> policy -> payment order as cancellation; no payment or balance mutation.
        db.queryForObject("SELECT id FROM institutions WHERE id=? FOR UPDATE",Long.class,initial.institutionId());
        if(initial.policyId()!=null)db.queryForObject("SELECT id FROM support_policies WHERE id=? FOR UPDATE",Long.class,initial.policyId());
        db.queryForObject("SELECT id FROM payments WHERE id=? FOR UPDATE",Long.class,paymentId);
        Payment current=payment(paymentId);lockMovement(id);Movement movement=movement(id);
        if(current.voided())throw ApiException.conflict("El pago está anulado y no puede conciliarse.");
        if(current.amount().compareTo(movement.amount())!=0||!current.currency().equals(movement.currency()))throw ApiException.conflict("El importe y la moneda del pago deben coincidir exactamente con el movimiento.");
        if(movement.paymentId()!=null){if(movement.paymentId()==paymentId)return detail(id);throw ApiException.conflict("El movimiento ya está conciliado. Deshaz la conciliación antes de elegir otro pago.");}
        if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM bank_movements WHERE matched_payment_id=?)",Boolean.class,paymentId)))throw ApiException.conflict("El pago ya está conciliado con otro movimiento.");
        db.update("UPDATE bank_movements SET matched_payment_id=?,matched_by=?,matched_at=NOW() WHERE id=?",paymentId,actor.id(),id);
        event(actor,id,paymentId,"MATCH",cleanReason);return detail(id);
    }
    @Transactional
    public Detail unmatch(long id,long expectedPaymentId,String reason){
        Models.User actor=staff();String cleanReason=reason(reason);lockMovement(id);Movement movement=movement(id);
        if(movement.paymentId()==null)throw ApiException.conflict("El movimiento ya no tiene una conciliación. Actualiza el detalle.");
        if(movement.paymentId()!=expectedPaymentId)throw ApiException.conflict("La conciliación cambió. Actualiza el detalle antes de deshacerla.");
        db.update("UPDATE bank_movements SET matched_payment_id=NULL,matched_by=NULL,matched_at=NULL WHERE id=?",id);
        event(actor,id,expectedPaymentId,"UNMATCH",cleanReason);return detail(id);
    }
    private void event(Models.User actor,long id,long paymentId,String action,String reason){db.update("INSERT INTO bank_reconciliation_events(movement_id,payment_id,action,reason,actor_id) VALUES(?,?,?,?,?)",id,paymentId,action,reason,actor.id());audit(actor,action+"_BANK_MOVEMENT",id,"Pago "+paymentId+". "+reason+". Sin cambios al pago ni a los minutos.");}
    private void audit(Models.User actor,String action,long id,String details){db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,?,'BANK_RECONCILIATION',?,?)",actor.username(),action,id,details);}
    private void lockMovement(long id){List<Long> ids=db.queryForList("SELECT id FROM bank_movements WHERE id=? FOR UPDATE",Long.class,id);if(ids.isEmpty())throw ApiException.missing();}
    private Movement movement(long id){List<Movement> rows=db.query(MOVEMENTS+" WHERE m.id=?",this::movementRow,id);if(rows.isEmpty())throw ApiException.missing();return rows.get(0);}
    private Payment payment(long id){List<Payment> rows=db.query("SELECT id,institution_id,policy_id,amount,currency,voided FROM payments WHERE id=?",(r,n)->new Payment(r.getLong("id"),r.getLong("institution_id"),r.getObject("policy_id",Long.class),r.getBigDecimal("amount"),r.getString("currency"),r.getBoolean("voided")),id);if(rows.isEmpty())throw ApiException.missing();return rows.get(0);}
    private String reason(String reason){String text=BankCsv.text(reason,2000,"Motivo");if(text.length()<5)throw ApiException.bad("El motivo debe contener al menos cinco caracteres.");return text;}
    private Models.User staff(){Models.User actor=helpdesk.me();if(actor.role()!=Models.Role.ADMIN&&actor.role()!=Models.Role.BILLING)throw ApiException.forbidden();return actor;}
    private ImportSummary importRow(ResultSet r,int n)throws SQLException{return new ImportSummary(r.getLong("id"),r.getString("account_label"),r.getString("filename"),r.getInt("row_count"),r.getInt("new_count"),r.getString("author"),r.getTimestamp("created_at").toInstant());}
    private Movement movementRow(ResultSet r,int n)throws SQLException{return new Movement(r.getLong("id"),r.getString("account_label"),r.getDate("booked_on").toLocalDate(),r.getString("reference"),r.getBigDecimal("amount"),r.getString("currency"),r.getObject("matched_payment_id",Long.class),r.getString("payment_reference"),r.getString("institution_name"),r.getBoolean("payment_voided"),r.getString("matched_name"),r.getTimestamp("matched_at")==null?null:r.getTimestamp("matched_at").toInstant());}
}
