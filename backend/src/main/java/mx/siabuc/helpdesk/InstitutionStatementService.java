package mx.siabuc.helpdesk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Reproducible issued snapshots. No private notes or account identities enter the projection. */
@Service
public class InstitutionStatementService {
    private static final BigDecimal ZERO=new BigDecimal("0.00");
    private final JdbcTemplate db;private final HelpdeskService helpdesk;private final ClientFinanceAccessService access;private final ObjectMapper mapper;private final ZoneId zone;
    public InstitutionStatementService(JdbcTemplate db,HelpdeskService helpdesk,ClientFinanceAccessService access,ObjectMapper mapper,@Value("${app.reporting-time-zone:America/Mexico_City}") String timeZone){this.db=db;this.helpdesk=helpdesk;this.access=access;this.mapper=mapper;zone=ZoneId.of(timeZone);}
    public record Input(UUID requestId,Long institutionId,String cutoff){}
    public record Option(long id,String name){}
    public record Options(String timeZone,Instant supportedFrom,LocalDate earliestCutoff,LocalDate today,List<Option> institutions){}
    public record Summary(long id,String folio,long institutionId,LocalDate cutoff,Instant createdAt){}
    public record Issued(long id,String folio,Document document){}
    public record Policy(long id,String folio,String status,LocalDate validFrom,LocalDate validUntil,String currency,BigDecimal contracted,BigDecimal paid,BigDecimal pending,long emailMinutes,long remoteMinutes,long creditedEmailMinutes,long creditedRemoteMinutes){}
    public record Payment(long id,Long policyId,String reference,String currency,BigDecimal amount,LocalDate paidOn,String method,boolean voided,long emailMinutes,long remoteMinutes,Instant recordedAt,boolean legacyOpening){}
    public record CurrencyTotal(String currency,BigDecimal activeContracts,BigDecimal policyPayments,BigDecimal directPayments,BigDecimal allPayments,BigDecimal pendingContracts,BigDecimal voidedPayments){}
    public record Balance(long emailAllocated,long emailUsed,long emailRemaining,long remoteAllocated,long remoteUsed,long remoteRemaining){}
    public record MinuteMovement(long revisionId,Instant recordedAt,String type,long emailAllocatedDelta,long emailUsedDelta,long remoteAllocatedDelta,long remoteUsedDelta,Balance balance){}
    public record Document(long institutionId,String institutionName,LocalDate cutoff,Instant cutoffAt,Instant observedAt,String timeZone,Instant supportedFrom,boolean legacyOpening,List<CurrencyTotal> totals,List<Policy> policies,List<Payment> payments,Balance balance,List<MinuteMovement> minuteMovements,String basis,String limitation){}
    private record Version(long id,String kind,long entityId,Instant at,boolean opening,boolean deleted,JsonNode data){}
    private Models.User actor(boolean client){if(client)return access.requireClient();Models.User actor=helpdesk.me();if(actor.role()!=Models.Role.ADMIN&&actor.role()!=Models.Role.BILLING)throw ApiException.forbidden();return actor;}
    private long scope(Models.User actor,Long institution){if(actor.role()==Models.Role.USER){if(institution!=null&&!institution.equals(actor.institutionId()))throw ApiException.forbidden();return actor.institutionId();}if(institution==null||institution<=0)throw ApiException.bad("Seleccione una institución.");return institution;}
    private Instant origin(){return db.queryForObject("SELECT opened_at FROM statement_history_origin WHERE id=1",(r,n)->r.getTimestamp(1).toInstant());}
    public Options options(boolean client){Models.User actor=actor(client);Instant opened=origin();List<Option> options=actor.role()==Models.Role.USER?db.query("SELECT id,name FROM institutions WHERE id=?",(r,n)->new Option(r.getLong(1),r.getString(2)),actor.institutionId()):db.query("SELECT id,name FROM institutions ORDER BY name,id",(r,n)->new Option(r.getLong(1),r.getString(2)));return new Options(zone.getId(),opened,opened.atZone(zone).toLocalDate(),LocalDate.now(zone),options);}
    public List<Summary> list(Long institutionId,boolean client){long institution=scope(actor(client),institutionId);return db.query("SELECT id,institution_id,cutoff,created_at FROM institution_statements WHERE institution_id=? ORDER BY id DESC LIMIT 100",(r,n)->new Summary(r.getLong("id"),folio(r.getLong("id")),r.getLong("institution_id"),r.getDate("cutoff").toLocalDate(),r.getTimestamp("created_at").toInstant()),institution);}
    public Issued get(long id,boolean client){Models.User actor=actor(client);var rows=db.query("SELECT institution_id,snapshot FROM institution_statements WHERE id=?",(r,n)->Map.entry(r.getLong(1),r.getString(2)),id);if(rows.isEmpty()||(actor.role()==Models.Role.USER&&!rows.get(0).getKey().equals(actor.institutionId())))throw ApiException.missing();return new Issued(id,folio(id),decode(rows.get(0).getValue(),Document.class));}
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public Issued issue(Input input,boolean client){
        Models.User actor=actor(client);if(input==null||input.requestId()==null)throw ApiException.bad("Falta el identificador único de la solicitud.");long institution=scope(actor,input.institutionId());LocalDate cutoff=parseDate(input.cutoff());
        // Same-institution operations use a shared lock; statement creation never changes its balances.
        var names=db.queryForList("SELECT name FROM institutions WHERE id=? FOR SHARE",String.class,institution);if(names.isEmpty())throw ApiException.missing();
        var previous=db.query("SELECT id,institution_id,cutoff FROM institution_statements WHERE request_id=?",(r,n)->new Summary(r.getLong(1),folio(r.getLong(1)),r.getLong(2),r.getDate(3).toLocalDate(),null),input.requestId());
        if(!previous.isEmpty()){Summary old=previous.get(0);if(old.institutionId()!=institution||!old.cutoff().equals(cutoff))throw ApiException.conflict("El identificador ya corresponde a otro estado de cuenta.");return get(old.id(),client);}
        Instant observed=Instant.now(),opened=origin();LocalDate today=observed.atZone(zone).toLocalDate();if(cutoff.isBefore(opened.atZone(zone).toLocalDate())||cutoff.isAfter(today))throw ApiException.bad("El corte debe estar entre "+opened.atZone(zone).toLocalDate()+" y "+today+"; no existe historia temporal anterior a la apertura.");
        Instant cutoffAt=cutoff.equals(today)?observed:cutoff.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1000);
        List<Version> latest=db.query("SELECT DISTINCT ON (kind,entity_id) id,kind,entity_id,recorded_at,opening,deleted,data::text FROM statement_versions WHERE institution_id=? AND recorded_at<=? ORDER BY kind,entity_id,recorded_at DESC,id DESC LIMIT 5002",(r,n)->new Version(r.getLong("id"),r.getString("kind"),r.getLong("entity_id"),r.getTimestamp("recorded_at").toInstant(),r.getBoolean("opening"),r.getBoolean("deleted"),decode(r.getString("data"),JsonNode.class)),institution,Timestamp.from(cutoffAt));
        if(latest.size()>5001)throw ApiException.bad("El estado supera 5000 pólizas y pagos. No se han omitido registros.");
        Version institutionVersion=latest.stream().filter(v->v.kind().equals("institutions")&&!v.deleted()).findFirst().orElseThrow(()->ApiException.bad("La institución no estaba registrada en el corte seleccionado."));
        List<Payment> payments=new ArrayList<>();for(Version v:latest)if(v.kind().equals("payments")&&!v.deleted()){JsonNode p=v.data();payments.add(new Payment(v.entityId(),p.path("policyId").isNull()?null:p.path("policyId").asLong(),p.path("reference").asText(),p.path("currency").asText(),decimal(p,"amount"),LocalDate.parse(p.path("paidOn").asText()),p.path("method").asText(),p.path("voided").asBoolean(),p.path("emailMinutes").asLong(),p.path("remoteMinutes").asLong(),v.at(),v.opening()));}
        List<Policy> policies=new ArrayList<>();for(Version v:latest)if(v.kind().equals("support_policies")&&!v.deleted()){JsonNode p=v.data();List<Payment> allocated=payments.stream().filter(x->x.policyId()!=null&&x.policyId()==v.entityId()&&!x.voided()).toList();BigDecimal paid=allocated.stream().map(Payment::amount).reduce(ZERO,BigDecimal::add);BigDecimal contracted=decimal(p,"amount");policies.add(new Policy(v.entityId(),p.path("folio").asText(),p.path("status").asText(),LocalDate.parse(p.path("validFrom").asText()),LocalDate.parse(p.path("validUntil").asText()),p.path("currency").asText(),contracted,paid,p.path("status").asText().equals("ACTIVE")?contracted.subtract(paid):ZERO,p.path("emailMinutes").asLong(),p.path("remoteMinutes").asLong(),allocated.stream().mapToLong(Payment::emailMinutes).sum(),allocated.stream().mapToLong(Payment::remoteMinutes).sum()));}
        Map<String,CurrencyTotal> totals=new java.util.TreeMap<>();for(Policy p:policies){CurrencyTotal t=totals.computeIfAbsent(p.currency(),InstitutionStatementService::empty);totals.put(p.currency(),new CurrencyTotal(t.currency(),t.activeContracts().add(p.status().equals("ACTIVE")?p.contracted():ZERO),t.policyPayments(),t.directPayments(),t.allPayments(),t.pendingContracts().add(p.pending()),t.voidedPayments()));}
        for(Payment p:payments){CurrencyTotal t=totals.computeIfAbsent(p.currency(),InstitutionStatementService::empty);totals.put(p.currency(),new CurrencyTotal(t.currency(),t.activeContracts(),t.policyPayments().add(!p.voided()&&p.policyId()!=null?p.amount():ZERO),t.directPayments().add(!p.voided()&&p.policyId()==null?p.amount():ZERO),t.allPayments().add(!p.voided()?p.amount():ZERO),t.pendingContracts(),t.voidedPayments().add(p.voided()?p.amount():ZERO)));}
        List<Version> counters=db.query("SELECT id,recorded_at,opening,deleted,data::text FROM statement_versions WHERE institution_id=? AND kind='institutions' AND recorded_at<=? ORDER BY recorded_at,id LIMIT 20001",(r,n)->new Version(r.getLong("id"),"institutions",institution,r.getTimestamp("recorded_at").toInstant(),r.getBoolean("opening"),r.getBoolean("deleted"),decode(r.getString("data"),JsonNode.class)),institution,Timestamp.from(cutoffAt));
        if(counters.size()>20000)throw ApiException.bad("El estado supera 20000 movimientos de minutos; no se han omitido registros.");
        List<MinuteMovement> movements=new ArrayList<>();Balance before=new Balance(0,0,0,0,0,0);for(Version v:counters){if(v.deleted())continue;Balance b=balance(v.data());long ea=b.emailAllocated()-before.emailAllocated(),eu=b.emailUsed()-before.emailUsed(),ra=b.remoteAllocated()-before.remoteAllocated(),ru=b.remoteUsed()-before.remoteUsed();if(movements.isEmpty()||ea!=0||eu!=0||ra!=0||ru!=0)movements.add(new MinuteMovement(v.id(),v.at(),v.opening()?"LEGACY_OPENING":movements.isEmpty()?"INITIAL_BALANCE":"BALANCE_CHANGE",ea,eu,ra,ru,b));before=b;}
        Document document=new Document(institution,institutionVersion.data().path("name").asText(),cutoff,cutoffAt,observed,zone.getId(),opened,counters.stream().anyMatch(Version::opening),List.copyOf(totals.values()),List.copyOf(policies),List.copyOf(payments),balance(institutionVersion.data()),List.copyOf(movements),"Corte por fecha de registro en la plataforma. La fecha declarada del pago y la vigencia contractual se muestran como referencia; no trasladan movimientos a cortes anteriores.","Apertura desde la migración 1.7: los saldos y registros anteriores se conservan como estado conocido, sin reconstruir movimientos previos. Esta emisión fija los datos visibles en una transacción consistente; otra emisión puede incorporar registros confirmados después. No es factura ni constancia bancaria.");
        String snapshot=encode(document);if(snapshot.length()>4_000_000)throw ApiException.bad("El documento supera el tamaño permitido; no se han omitido registros.");
        List<Long> ids;
        try{ids=db.queryForList("INSERT INTO institution_statements(institution_id,request_id,cutoff,created_by,snapshot) VALUES(?,?,?,?,?::jsonb) ON CONFLICT(request_id) DO NOTHING RETURNING id",Long.class,institution,input.requestId(),Date.valueOf(cutoff),actor.id(),snapshot);}
        catch(CannotSerializeTransactionException e){throw ApiException.conflict("Otra solicitud está conservando la misma emisión. Reintente con el mismo identificador para recuperarla.");}
        if(ids.isEmpty())throw ApiException.conflict("Solicitud concurrente: reintente con el mismo identificador para recuperar la emisión.");
        long id=ids.get(0);return new Issued(id,folio(id),document);
    }
    private static CurrencyTotal empty(String currency){return new CurrencyTotal(currency,ZERO,ZERO,ZERO,ZERO,ZERO,ZERO);}
    private static BigDecimal decimal(JsonNode n,String key){return n.path(key).decimalValue().setScale(2);}
    private static Balance balance(JsonNode n){long ea=n.path("emailAllocated").asLong(),eu=n.path("emailUsed").asLong(),ra=n.path("remoteAllocated").asLong(),ru=n.path("remoteUsed").asLong();return new Balance(ea,eu,ea-eu,ra,ru,ra-ru);}
    private static String folio(long id){return "SIABUC-EC-"+String.format(java.util.Locale.ROOT,"%010d",id);}
    private static LocalDate parseDate(String value){try{if(value==null||!value.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException();return LocalDate.parse(value);}catch(RuntimeException e){throw ApiException.bad("Fecha de corte inválida. Use AAAA-MM-DD.");}}
    private String encode(Object value){try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("No se pudo guardar el estado de cuenta",e);}}
    private <T>T decode(String value,Class<T> type){try{return mapper.readValue(value,type);}catch(JsonProcessingException e){throw new IllegalStateException("No se pudo leer el estado de cuenta",e);}}
}
