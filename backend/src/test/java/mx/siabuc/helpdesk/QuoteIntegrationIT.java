package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class QuoteIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    static LocalDate today(){return LocalDate.now(ZoneId.of("America/Mexico_City"));}
    static Map<String,Object> quotePayload(long institution,Long previous) {
        var value=new LinkedHashMap<String,Object>();value.put("institutionId",institution);value.put("folio",unique("COT-"));
        value.put("description","Atención SIABUC: instalación y préstamos, términos originales");
        value.put("expiresOn",today().plusDays(15).toString());value.put("validFrom",today().toString());value.put("validUntil",today().plusYears(1).toString());
        value.put("agreedAmount",new BigDecimal("1234.56"));value.put("emailMinutes",120L);value.put("remoteMinutes",60L);value.put("previousPolicyId",previous);return value;
    }
    static Map<String,Object> decision(String state,LocalDate date){return Map.of("decision",state,"contactName","Responsable de Biblioteca","evidence","Respuesta por correo, referencia CLIENTE-123","decidedOn",date.toString());}
    private long quote(long institution,Long previous) throws Exception {return success(admin.post("/quotes",quotePayload(institution,previous))).path("quote").path("id").asLong();}
    private void accept(long quote) throws Exception {success(admin.post("/quotes/"+quote+"/send",Map.of("evidence","Entrega personal de la oferta")));success(admin.post("/quotes/"+quote+"/decision",decision("ACCEPTED",today())));}
    private long convert(long quote,String folio) throws Exception {return success(admin.post("/quotes/"+quote+"/convert",Map.of("policyFolio",folio))).path("quote").path("convertedPolicyId").asLong();}
    private Client billing() throws Exception {String name=unique("quoteBilling");success(admin.post("/users",Map.of("username",name,"displayName","Cobranza","password",PASSWORD,"role","BILLING","active",true,"email","quotes@example.test")));return login(name,PASSWORD);}
    private long policy(long institution) throws Exception {return success(admin.post("/policies",PolicyIntegrationIT.policyPayload(institution,"100",60,30))).path("policy").path("id").asLong();}
    private long count(String sql,long id){return db.queryForObject(sql,Long.class,id);}

    @Test void billingManagesDraftAndTermsFreezeWithDescriptiveHistoryAtIssue() throws Exception {
        long institution=institution(25,40);Client billing=billing();var input=quotePayload(institution,null);
        JsonNode created=success(billing.post("/quotes",input));long id=created.path("quote").path("id").asLong();
        assertThat(created.path("quote").path("status").asText()).isEqualTo("DRAFT");assertThat(created.path("history")).hasSize(1);
        input.put("description","Descripción negociada, incluye diagnóstico");input.put("agreedAmount",new BigDecimal("1400.25"));
        JsonNode edited=success(billing.put("/quotes/"+id,input));assertThat(edited.path("history").get(0).path("description").asText()).contains("términos originales","Descripción negociada","1400.25");
        assertStatus(billing.post("/quotes/"+id+"/send",Map.of("evidence"," ")),400);
        JsonNode sent=success(billing.post("/quotes/"+id+"/send",Map.of("evidence","Entregada a Biblioteca por correo; ref OFERTA-10")));
        assertThat(sent.path("quote").path("status").asText()).isEqualTo("SENT");assertThat(sent.path("quote").path("sentAt").asText()).isNotBlank();
        assertStatus(billing.put("/quotes/"+id,input),409);assertStatus(billing.post("/quotes/"+id+"/send",Map.of("evidence","Reemitir")),409);
        assertThat(success(admin.get("/quotes?institutionId="+institution))).hasSize(1);assertBalance(institution,0,25,0,40);
        assertThat(count("SELECT COUNT(*) FROM payments WHERE institution_id=?",institution)).isZero();
        assertThat(count("SELECT COUNT(*) FROM support_policies WHERE institution_id=?",institution)).isZero();
    }

    @Test void clientCannotReadOrMutateFinancialQuotesAndRenewals() throws Exception {
        long institution=institution(0,0),id=quote(institution,null);Client user=institutionUser(institution);
        for(String path:List.of("/quotes","/quotes?institutionId="+institution,"/quotes/"+id,"/renewals","/renewals/settings"))assertStatus(user.get(path),403);
        assertStatus(user.post("/quotes",quotePayload(institution,null)),403);assertStatus(user.put("/quotes/"+id,quotePayload(institution,null)),403);
        assertStatus(user.post("/quotes/"+id+"/send",Map.of("evidence","Sin permiso")),403);
        assertStatus(user.post("/quotes/"+id+"/decision",decision("ACCEPTED",today())),403);
        assertStatus(user.post("/quotes/"+id+"/convert",Map.of("policyFolio",unique("BAD-"))),403);
        assertStatus(user.put("/renewals/settings",Map.of("enabled",false,"daysBeforeExpiry",2)),403);
        assertStatus(user.post("/renewals/scan",Map.of()),403);assertStatus(user.post("/renewals/notices/1/read",Map.of()),403);
        assertThat(success(admin.get("/quotes/"+id)).path("history")).hasSize(1);assertBalance(institution,0,0,0,0);
    }

    @Test void institutionAndAntecedentAreImmutableAndMoneyMinutesAndDatesAreValidated() throws Exception {
        long institution=institution(0,0),other=institution(0,0),previous=policy(other);var input=quotePayload(institution,previous);
        assertStatus(admin.post("/quotes",input),409);input.put("previousPolicyId",null);
        for(String amount:List.of("0","-1","10.001","1000000000")){input.put("agreedAmount",new BigDecimal(amount));assertStatus(admin.post("/quotes",input),400);}
        input.put("agreedAmount",new BigDecimal("100.10"));input.put("emailMinutes",0);input.put("remoteMinutes",0);assertStatus(admin.post("/quotes",input),400);
        input.put("emailMinutes",1.5);assertStatus(admin.post("/quotes",input),400);input.put("emailMinutes",120);
        input.put("validUntil",today().minusDays(1).toString());assertStatus(admin.post("/quotes",input),400);input.put("validUntil",today().plusYears(1).toString());
        long id=success(admin.post("/quotes",input)).path("quote").path("id").asLong();
        input.put("institutionId",other);assertStatus(admin.put("/quotes/"+id,input),409);input.put("institutionId",institution);input.put("previousPolicyId",policy(institution));assertStatus(admin.put("/quotes/"+id,input),409);
        input.put("previousPolicyId",null);input.put("folio",input.get("folio").toString().toLowerCase());assertStatus(admin.post("/quotes",input),409);
        assertThat(success(admin.get("/quotes/"+id)).path("history")).hasSize(1);
    }

    @Test void acceptanceAndRejectionRequireEvidenceAndValidActualResponseDate() throws Exception {
        long institution=institution(0,0),id=quote(institution,null);
        assertStatus(admin.post("/quotes/"+id+"/decision",decision("ACCEPTED",today())),409);
        assertStatus(admin.post("/quotes/"+id+"/convert",Map.of("policyFolio",unique("NO-"))),409);
        success(admin.post("/quotes/"+id+"/send",Map.of("evidence","Entrega documentada")));
        var missing=new LinkedHashMap<>(decision("ACCEPTED",today()));missing.put("contactName","");assertStatus(admin.post("/quotes/"+id+"/decision",missing),400);
        missing.put("contactName","Contacto");missing.put("evidence","");assertStatus(admin.post("/quotes/"+id+"/decision",missing),400);
        assertStatus(admin.post("/quotes/"+id+"/decision",decision("ACCEPTED",today().plusDays(1))),400);
        assertStatus(admin.post("/quotes/"+id+"/decision",decision("ACCEPTED",today().minusDays(1))),400);
        db.update("UPDATE support_quotes SET sent_at=?,expires_on=? WHERE id=?",Timestamp.from(today().minusDays(3).atTime(12,0).atZone(ZoneId.of("America/Mexico_City")).toInstant()),java.sql.Date.valueOf(today().minusDays(1)),id);
        assertStatus(admin.post("/quotes/"+id+"/decision",decision("ACCEPTED",today())),409);
        JsonNode accepted=success(admin.post("/quotes/"+id+"/decision",decision("ACCEPTED",today().minusDays(1))));
        assertThat(accepted.path("quote").path("decisionRecordedBy").asText()).isEqualTo("testadmin");assertThat(accepted.path("history").get(0).path("description").asText()).contains("no es una firma digital","CLIENTE-123");
        assertStatus(admin.post("/quotes/"+id+"/decision",decision("REJECTED",today())),409);
        long rejected=quote(institution,null);success(admin.post("/quotes/"+rejected+"/send",Map.of("evidence","Entrega documentada")));
        JsonNode detail=success(admin.post("/quotes/"+rejected+"/decision",decision("REJECTED",today())));assertThat(detail.path("quote").path("status").asText()).isEqualTo("REJECTED");
        assertStatus(admin.post("/quotes/"+rejected+"/convert",Map.of("policyFolio",unique("NO-"))),409);
        var expired=quotePayload(institution,null);expired.put("expiresOn",today().minusDays(1).toString());long draft=success(admin.post("/quotes",expired)).path("quote").path("id").asLong();
        assertStatus(admin.post("/quotes/"+draft+"/send",Map.of("evidence","Entrega fuera de plazo")),409);
    }

    @Test void conversionCopiesExactTermsWithoutCreditsAndRetryUsesOriginalConversionFolio() throws Exception {
        long institution=institution(45,20),id=quote(institution,null);accept(id);String folio=unique("POL-COT-");long policy=convert(id,folio);
        JsonNode detail=success(admin.get("/policies/"+policy)),p=detail.path("policy");assertThat(p.path("agreedAmount").decimalValue()).isEqualByComparingTo("1234.56");
        assertThat(p.path("emailMinutes").asLong()).isEqualTo(120);assertThat(p.path("remoteMinutes").asLong()).isEqualTo(60);assertThat(p.path("paidAmount").decimalValue()).isZero();
        assertThat(detail.path("payments")).isEmpty();assertBalance(institution,0,45,0,20);
        var edit=PolicyIntegrationIT.policyPayload(institution,"1234.56",120,60);edit.put("folio",unique("RENAMED-"));success(admin.put("/policies/"+policy,edit));
        assertThat(convert(id,folio)).isEqualTo(policy);assertStatus(admin.post("/quotes/"+id+"/convert",Map.of("policyFolio",edit.get("folio"))),409);
        assertThat(count("SELECT COUNT(*) FROM support_policies WHERE institution_id=?",institution)).isEqualTo(1);assertThat(count("SELECT COUNT(*) FROM payments WHERE institution_id=?",institution)).isZero();
        JsonNode quote=success(admin.get("/quotes/"+id));assertThat(quote.path("quote").path("convertedPolicyFolio").asText()).isEqualTo(edit.get("folio"));
        assertThat(quote.path("history")).hasSize(4);assertThat(quote.path("history").get(0).path("description").asText()).contains(folio,"No se registraron pagos");
    }

    @Test void concurrentConversionOfSameQuoteCreatesOnePolicyAndOneRenewal() throws Exception {
        long institution=institution(0,0),previous=policy(institution),id=quote(institution,previous);accept(id);String folio=unique("PARALLEL-");Client a=billing(),b=billing();a.refreshCsrf();b.refreshCsrf();
        var start=new CountDownLatch(1);var ready=new CountDownLatch(2);var pool=Executors.newFixedThreadPool(2);
        try {
            var one=pool.submit(()->{ready.countDown();assertThat(start.await(20,TimeUnit.SECONDS)).isTrue();return a.raw("POST","/quotes/"+id+"/convert",Map.of("policyFolio",folio),true);});
            var two=pool.submit(()->{ready.countDown();assertThat(start.await(20,TimeUnit.SECONDS)).isTrue();return b.raw("POST","/quotes/"+id+"/convert",Map.of("policyFolio",folio),true);});
            assertThat(ready.await(20,TimeUnit.SECONDS)).isTrue();start.countDown();JsonNode first=success(one.get(40,TimeUnit.SECONDS)),second=success(two.get(40,TimeUnit.SECONDS));
            assertThat(first.path("quote").path("convertedPolicyId")).isEqualTo(second.path("quote").path("convertedPolicyId"));
        } finally {start.countDown();pool.shutdownNow();}
        assertThat(count("SELECT COUNT(*) FROM support_policies WHERE institution_id=?",institution)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM policy_renewals WHERE previous_policy_id=?",previous)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM quote_history WHERE quote_id=? AND action='CONVERT_QUOTE'",id)).isEqualTo(1);assertBalance(institution,0,0,0,0);
    }

    @Test void aRenewedAntecedentCannotCreateAnotherPolicyAndCancelledReplacementKeepsItsChain() throws Exception {
        long institution=institution(0,0),previous=policy(institution),id=quote(institution,previous),competing=quote(institution,previous);accept(id);accept(competing);
        long next=convert(id,unique("NEXT-"));assertStatus(admin.post("/quotes/"+competing+"/convert",Map.of("policyFolio",unique("DUPLICATE-"))),409);
        assertThat(count("SELECT COUNT(*) FROM support_policies WHERE institution_id=?",institution)).isEqualTo(2);
        assertThat(success(admin.get("/quotes/"+competing)).path("quote").path("status").asText()).isEqualTo("ACCEPTED");
        success(admin.post("/policies/"+next+"/cancel",Map.of("reason","La institución requiere otra cobertura")));
        long replacement=quote(institution,next);accept(replacement);long last=convert(replacement,unique("REPLACEMENT-"));
        assertThat(success(admin.get("/policies/"+next)).path("policy").path("status").asText()).isEqualTo("CANCELLED");
        assertThat(db.queryForObject("SELECT new_policy_id FROM policy_renewals WHERE previous_policy_id=?",Long.class,previous)).isEqualTo(next);
        assertThat(db.queryForObject("SELECT new_policy_id FROM policy_renewals WHERE previous_policy_id=?",Long.class,next)).isEqualTo(last);
        JsonNode links=success(admin.get("/renewals")).path("links");boolean cancelledVisible=false;
        for(JsonNode link:links)if(link.path("newPolicyId").asLong()==next)cancelledVisible=link.path("newPolicyStatus").asText().equals("CANCELLED");
        assertThat(cancelledVisible).isTrue();assertBalance(institution,0,0,0,0);
    }
}
