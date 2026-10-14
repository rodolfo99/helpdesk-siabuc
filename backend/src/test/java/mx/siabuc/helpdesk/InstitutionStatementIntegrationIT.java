package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstitutionStatementIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    private static final String PRIVATE="PRIVADO_ESTADO_CUENTA_NO_PUBLICAR";
    private String today(){return LocalDate.now(ZoneId.of("America/Mexico_City")).toString();}
    private Map<String,Object> input(long institution){return Map.of("institutionId",institution,"cutoff",today(),"requestId",UUID.randomUUID().toString());}
    private JsonNode issue(long institution)throws Exception{return success(admin.post("/statements",input(institution)));}
    private long policy(long institution,String amount)throws Exception{var p=new LinkedHashMap<>(PolicyIntegrationIT.policyPayload(institution,amount,50,30));p.put("description",PRIVATE);return success(admin.post("/policies",p)).path("policy").path("id").asLong();}
    private long pay(long institution,Long policy,String amount,long email,long remote)throws Exception{var p=new LinkedHashMap<>(PaymentIntegrationIT.paymentPayload(institution,email,remote));p.put("amount",new BigDecimal(amount));p.put("notes",PRIVATE);p.put("paidOn","2001-01-01");if(policy!=null)p.put("policyId",policy);return success(admin.post("/payments",p)).path("id").asLong();}
    private JsonNode total(JsonNode statement,String currency){for(JsonNode t:statement.path("document").path("totals"))if(t.path("currency").asText().equals(currency))return t;throw new AssertionError("Moneda no encontrada");}
    private void enable(long institution)throws Exception{success(admin.put("/client-finance-access/"+institution,Map.of("enabled",true)));}

    @Test void issuedStatementSeparatesMoneyMinutesAndBackdatedPaymentUsesRegistrationTime()throws Exception{
        long institution=institution(10,20),policy=policy(institution,"100.00");long payment=pay(institution,policy,"40.00",15,5);pay(institution,null,"25.00",5,5);
        long caseId=supportCase(institution,"EMAIL");addEntry(admin,caseId,PRIVATE,"EMAIL",3,true,200);
        long outbox=db.queryForObject("SELECT COUNT(*) FROM notification_outbox",Long.class);JsonNode issued=issue(institution),d=issued.path("document"),mxn=total(issued,"MXN");
        assertThat(mxn.path("activeContracts").decimalValue()).isEqualByComparingTo("100");assertThat(mxn.path("policyPayments").decimalValue()).isEqualByComparingTo("40");assertThat(mxn.path("directPayments").decimalValue()).isEqualByComparingTo("25");assertThat(mxn.path("allPayments").decimalValue()).isEqualByComparingTo("65");assertThat(mxn.path("pendingContracts").decimalValue()).isEqualByComparingTo("60");
        assertThat(d.path("balance").path("emailAllocated").asLong()).isEqualTo(30);assertThat(d.path("balance").path("emailUsed").asLong()).isEqualTo(3);assertThat(d.path("balance").path("emailRemaining").asLong()).isEqualTo(27);assertThat(d.path("balance").path("remoteRemaining").asLong()).isEqualTo(30);
        assertThat(d.path("minuteMovements")).hasSize(4);assertThat(d.path("minuteMovements").get(0).path("type").asText()).isEqualTo("INITIAL_BALANCE");assertThat(d.path("minuteMovements").get(3).path("emailUsedDelta").asLong()).isEqualTo(3);
        assertThat(d.toString()).doesNotContain(PRIVATE,"notes","description","createdBy","voidedBy","password");assertThat(d.path("payments").get(0).path("paidOn").asText()).isEqualTo("2001-01-01");assertThat(d.path("payments").get(0).path("recordedAt").asText()).doesNotStartWith("2001");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notification_outbox",Long.class)).isEqualTo(outbox);assertBalance(institution,3,27,0,30);
        assertThat(ids(d.path("payments"))).contains(payment);assertThat(d.path("policies").get(0).path("creditedEmailMinutes").asLong()).isEqualTo(15);
    }

    @Test void policyEditsAndPaymentVoidsNeverRewritePreviousEmission()throws Exception{
        long institution=institution(0,0),paidPolicy=policy(institution,"100"),unpaid=policy(institution,"70");long payment=pay(institution,paidPolicy,"40",10,5);
        JsonNode first=issue(institution);long id=first.path("id").asLong();
        var edit=PolicyIntegrationIT.policyPayload(institution,"120",80,60);success(admin.put("/policies/"+unpaid,edit));success(admin.post("/payments/"+payment+"/void",Map.of("reason",PRIVATE)));
        assertThat(success(admin.get("/statements/"+id))).isEqualTo(first);JsonNode second=issue(institution);
        assertThat(total(first,"MXN").path("pendingContracts").decimalValue()).isEqualByComparingTo("130");assertThat(total(second,"MXN").path("pendingContracts").decimalValue()).isEqualByComparingTo("220");assertThat(total(second,"MXN").path("voidedPayments").decimalValue()).isEqualByComparingTo("40");assertThat(second.path("document").path("balance").path("emailRemaining").asLong()).isZero();
        assertThat(second.path("document").path("payments").get(0).path("voided").asBoolean()).isTrue();assertThat(first.path("document").path("payments").get(0).path("voided").asBoolean()).isFalse();
    }

    @Test void portalPermissionsAreRecheckedForEveryReadGenerateAndPdf()throws Exception{
        long institution=institution(0,0),other=institution(0,0);Client client=institutionUser(institution),outsider=institutionUser(other);JsonNode own=issue(institution),foreign=issue(other);long id=own.path("id").asLong();
        assertStatus(client.get("/client-finance/statements/options"),403);assertStatus(client.get("/client-finance/statements/"+id+".pdf"),403);assertStatus(client.post("/client-finance/statements",input(institution)),403);
        enable(institution);enable(other);assertThat(success(client.get("/client-finance/statements/options")).path("institutions")).hasSize(1);assertThat(success(client.get("/client-finance/statements/"+id))).isEqualTo(own);
        assertStatus(client.get("/client-finance/statements/"+foreign.path("id").asLong()),404);assertStatus(outsider.get("/client-finance/statements/"+id+".pdf"),404);assertStatus(client.post("/client-finance/statements",input(other)),403);
        assertStatus(client.get("/statements/options"),403);assertStatus(client.raw("POST","/client-finance/statements",input(institution),false),403);
        success(client.post("/client-finance/statements",Map.of("cutoff",today(),"requestId",UUID.randomUUID().toString())));
        success(admin.put("/client-finance-access/"+institution,Map.of("enabled",false)));assertStatus(client.get("/client-finance/statements/"+id),403);assertStatus(client.get("/client-finance/statements/"+id+".pdf"),403);
    }

    @Test void duplicateRequestIsIdempotentAndCutoffBeforeOpeningOrFutureIsRejected()throws Exception{
        long institution=institution(0,0);var input=input(institution);JsonNode first=success(admin.post("/statements",input));assertThat(success(admin.post("/statements",input))).isEqualTo(first);assertThat(success(admin.get("/statements?institutionId="+institution))).hasSize(1);
        var conflict=new LinkedHashMap<>(input);conflict.put("institutionId",institution(0,0));assertStatus(admin.post("/statements",conflict),409);
        String earliest=success(admin.get("/statements/options")).path("earliestCutoff").asText();for(String cutoff:new String[]{LocalDate.parse(earliest).minusDays(1).toString(),LocalDate.parse(today()).plusDays(1).toString(),"2026-99-01","invalid"})assertStatus(admin.post("/statements",Map.of("institutionId",institution,"requestId",UUID.randomUUID().toString(),"cutoff",cutoff)),400);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM institution_statements WHERE institution_id=?",Long.class,institution)).isEqualTo(1);
    }

    @Test void storedSnapshotsAndHistoryCannotBeRewrittenAndFailedPaymentsLeaveNoVersions()throws Exception{
        long institution=institution(0,0);long id=issue(institution).path("id").asLong();
        assertThatThrownBy(()->db.update("UPDATE institution_statements SET snapshot='{}'::jsonb WHERE id=?",id)).hasMessageContaining("inmutables");assertThatThrownBy(()->db.update("DELETE FROM statement_versions WHERE institution_id=?",institution)).hasMessageContaining("inmutables");
        long count=db.queryForObject("SELECT COUNT(*) FROM statement_versions WHERE institution_id=?",Long.class,institution);faultNotifications.forcePaymentFailureFor(institution);try{assertStatus(admin.post("/payments",PaymentIntegrationIT.paymentPayload(institution,10,5)),409);}finally{faultNotifications.resetPaymentFailure();}
        assertThat(db.queryForObject("SELECT COUNT(*) FROM statement_versions WHERE institution_id=?",Long.class,institution)).isEqualTo(count);assertBalance(institution,0,0,0,0);
    }

    @Test void pdfContainsStoredPublicProjectionAndDownloadingDoesNotChangeBalancesOrEmissionCount()throws Exception{
        long institution=institution(0,0);policy(institution,"100");pay(institution,null,"50",10,5);enable(institution);Client client=institutionUser(institution);JsonNode statement=issue(institution);long id=statement.path("id").asLong();
        var result=client.get("/client-finance/statements/"+id+".pdf");assertStatus(result,200);assertThat(result.getResponse().getContentType()).isEqualTo("application/pdf");assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
        java.nio.file.Files.write(java.nio.file.Path.of("target","statement-v17-test.pdf"),result.getResponse().getContentAsByteArray());
        try(var pdf=Loader.loadPDF(result.getResponse().getContentAsByteArray())){
            String text=new PDFTextStripper().getText(pdf);
            assertThat(text).contains(statement.path("folio").asText(),"ESTADO DE CUENTA","100.00","50.00","no fiscal","PÓLIZAS Y APLICACIÓN DE PAGOS").doesNotContain(PRIVATE,"testadmin");
            int fonts=0;
            for(var page:pdf.getPages())for(var name:page.getResources().getFontNames()){
                var font=page.getResources().getFont(name);
                assertThat(font.isEmbedded()).as("Fuente %s debe acompañar al PDF para visualizarse sin fuentes locales",font.getName()).isTrue();
                fonts++;
            }
            assertThat(fonts).isGreaterThanOrEqualTo(2);
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM institution_statements WHERE institution_id=?",Long.class,institution)).isEqualTo(1);assertBalance(institution,0,10,0,5);
    }

    @Test void concurrentDuplicateEmissionReturnsOneDocumentOrExplicitRetryConflict()throws Exception{
        long institution=institution(0,0);Client first=login("testadmin","PruebaSegura123!"),second=login("testadmin","PruebaSegura123!");var body=input(institution);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
        try{var a=executor.submit(()->{start.await();return first.post("/statements",body);});var b=executor.submit(()->{start.await();return second.post("/statements",body);});start.countDown();var ra=a.get(20,java.util.concurrent.TimeUnit.SECONDS);var rb=b.get(20,java.util.concurrent.TimeUnit.SECONDS);assertThat(ra.getResponse().getStatus()).isIn(200,409);assertThat(rb.getResponse().getStatus()).isIn(200,409);assertThat(java.util.List.of(ra.getResponse().getStatus(),rb.getResponse().getStatus())).contains(200);
            JsonNode retry=success(admin.post("/statements",body));assertThat(db.queryForObject("SELECT COUNT(*) FROM institution_statements WHERE institution_id=?",Long.class,institution)).isEqualTo(1);assertThat(success(admin.get("/statements/"+retry.path("id").asLong()))).isEqualTo(retry);
        }finally{executor.shutdownNow();}
    }

    @Test void billingCanIssueButTechnicianCannotReadFinancialStatements()throws Exception{
        long institution=institution(0,0);for(String role:new String[]{"BILLING","TECH"}){String username=unique("statementrole");var user=new LinkedHashMap<String,Object>();user.put("username",username);user.put("displayName","Prueba permisos");user.put("password",PASSWORD);user.put("role",role);user.put("active",true);user.put("email","roles@example.test");success(admin.post("/users",user));Client actor=login(username,PASSWORD);assertStatus(actor.get("/statements/options"),role.equals("BILLING")?200:403);assertStatus(actor.post("/statements",input(institution)),role.equals("BILLING")?200:403);}
    }
}
