package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

class BankReconciliationIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    private static final String HEADER="fecha,referencia,importe,moneda\n";
    @Test void previewsWithoutWritesAndCanonicalReimportsRemainIdempotent()throws Exception{
        Client billing=staff("BILLING");String account=unique("Cuenta"),ref=unique("BANCO");String csv=HEADER+"2026-10-13,"+ref+",1250.50,MXN\n";
        long before=db.queryForObject("SELECT COUNT(*) FROM bank_imports",Long.class);
        JsonNode preview=success(upload(billing,"/preview",account,csv,null,true));assertThat(preview.path("newCount").asInt()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM bank_imports",Long.class)).isEqualTo(before);
        JsonNode imported=success(upload(billing,"/imports",account,csv,preview.path("hash").asText(),true));long id=imported.at("/summary/id").asLong();
        String canonical="\uFEFFfecha,referencia,importe,moneda\r\n2026-10-13,"+ref.toLowerCase()+",1250.5,MXN\r\n";
        JsonNode repeatedPreview=success(upload(admin,"/preview",account.toLowerCase(),canonical,null,true));assertThat(repeatedPreview.path("existingImportId").asLong()).isEqualTo(id);assertThat(repeatedPreview.path("newCount").asInt()).isZero();
        assertThat(success(upload(admin,"/imports",account,canonical,repeatedPreview.path("hash").asText(),true)).at("/summary/id").asLong()).isEqualTo(id);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM bank_imports WHERE account_key=?",Long.class,account.toLowerCase())).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='IMPORT_BANK_CSV' AND entity_id=?",Long.class,id)).isEqualTo(1);
    }
    @Test void overlappingImportsShareMovementsAndRejectAllInvalidRowsAtomically()throws Exception{
        String account=unique("Cuenta"),ref=unique("ABONO");JsonNode first=imported(admin,account,HEADER+"2026-10-13,"+ref+",100,MXN");long movement=first.at("/movements/0/id").asLong();
        JsonNode second=imported(admin,account,HEADER+"2026-10-13,"+ref+",100.00,MXN\n2026-10-14,SEGUNDO,20,MXN");
        assertThat(second.at("/summary/newCount").asInt()).isEqualTo(1);assertThat(second.at("/movements/0/id").asLong()).isEqualTo(movement);
        long before=db.queryForObject("SELECT COUNT(*) FROM bank_imports",Long.class);
        assertStatus(upload(admin,"/imports",account,HEADER+"2026-10-15,TERCERO,10,MXN\n2026-02-30,FALLA,10,MXN","invalid",true),400);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM bank_imports",Long.class)).isEqualTo(before);
        assertStatus(upload(admin,"/imports",account,HEADER+"2026-10-15,TERCERO,10,MXN","stale-preview",true),409);
    }
    @Test void explicitMatchAndUnmatchLeavePaymentsBalancesAndMailUnchanged()throws Exception{
        Client billing=staff("BILLING");long institution=institution(20,30);String ref=unique("REFERENCE");long payment=payment(institution,"125.50",ref);
        long movement=imported(admin,unique("Cuenta"),HEADER+"2026-10-13,"+ref+",125.50,MXN").at("/movements/0/id").asLong();
        Map<String,Object> beforePayment=db.queryForMap("SELECT * FROM payments WHERE id=?",payment),beforeInstitution=db.queryForMap("SELECT * FROM institutions WHERE id=?",institution);
        long mail=db.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE payment_id=?",Long.class,payment);
        JsonNode candidates=success(billing.get(path(movement)+"/candidates?q="+payment));assertThat(candidates.path("total").asLong()).isEqualTo(1);assertThat(candidates.at("/items/0/referenceMatch").asBoolean()).isTrue();
        JsonNode matched=success(billing.post(path(movement)+"/match",decision(payment,"Comprobante y referencia verificados")));
        assertThat(matched.at("/movement/paymentId").asLong()).isEqualTo(payment);assertThat(matched.path("history")).hasSize(1);
        success(admin.post(path(movement)+"/match",decision(payment,"Reintento del mismo vínculo")));
        assertThat(success(admin.get(path(movement))).path("history")).hasSize(1);
        assertStatus(admin.post(path(movement)+"/unmatch",decision(payment+100000,"Selección obsoleta del pago")),409);
        JsonNode undone=success(billing.post(path(movement)+"/unmatch",decision(payment,"Corrección de la referencia elegida")));
        assertThat(undone.at("/movement/paymentId").isNull()).isTrue();assertThat(undone.path("history")).hasSize(2);assertThat(undone.at("/history/0/action").asText()).isEqualTo("UNMATCH");
        assertThat(db.queryForMap("SELECT * FROM payments WHERE id=?",payment)).isEqualTo(beforePayment);assertThat(db.queryForMap("SELECT * FROM institutions WHERE id=?",institution)).isEqualTo(beforeInstitution);assertThat(db.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE payment_id=?",Long.class,payment)).isEqualTo(mail);
    }
    @Test void rejectsWrongAmountCurrencyVoidsDuplicatesAndShortReason()throws Exception{
        long institution=institution(0,0);long payment=payment(institution,"75.25",unique("PAY")),wrong=payment(institution,"99.00",unique("WRONG"));
        String account=unique("Cuenta");JsonNode imported=imported(admin,account,HEADER+"2026-10-13,UNO,75.25,MXN\n2026-10-13,DOS,75.25,MXN\n2026-10-13,USD,75.25,USD");long first=imported.at("/movements/0/id").asLong(),second=imported.at("/movements/1/id").asLong(),usd=imported.at("/movements/2/id").asLong();
        assertStatus(admin.post(path(first)+"/match",decision(payment,"no")),400);
        assertStatus(admin.post(path(first)+"/match",decision(wrong,"Importe no coincide")),409);
        assertStatus(admin.post(path(usd)+"/match",decision(payment,"Moneda no coincide")),409);
        success(admin.post(path(first)+"/match",decision(payment,"Pago verificado y seleccionado")));
        assertStatus(admin.post(path(second)+"/match",decision(payment,"Pago duplicado en otro movimiento")),409);
        assertThat(success(admin.get(path(second)+"/candidates?q="+payment)).path("items")).isEmpty();
        success(admin.post("/payments/"+payment+"/void",Map.of("reason","Se anuló el pago después de conciliar")));
        assertThat(success(admin.get(path(first))).at("/movement/paymentVoided").asBoolean()).isTrue();
        success(admin.post(path(first)+"/unmatch",decision(payment,"Desvincular el pago ahora anulado")));
        assertStatus(admin.post(path(second)+"/match",decision(payment,"El pago anulado no debe conciliarse")),409);
    }
    @Test void protectsAllRoutesFromClientsTechniciansAndUnauthenticatedRequestsAndRequiresCsrf()throws Exception{
        long institution=institution(0,0),payment=payment(institution,"10.00",unique("SEC"));String account=unique("Cuenta"),csv=HEADER+"2026-10-13,SECURITY,10,MXN";
        JsonNode imported=imported(admin,account,csv);long batch=imported.at("/summary/id").asLong(),movement=imported.at("/movements/0/id").asLong();
        for(Client denied:new Client[]{institutionUser(institution),staff("TECH")}){
            for(String path:new String[]{"/reconciliation/imports","/reconciliation/imports/"+batch,path(movement),path(movement)+"/candidates"})assertStatus(denied.get(path),403);
            assertStatus(upload(denied,"/preview",account,csv,null,true),403);assertStatus(upload(denied,"/imports",account,csv,"invalid",true),403);
            assertStatus(denied.post(path(movement)+"/match",decision(payment,"Intento de rol sin permiso")),403);assertStatus(denied.post(path(movement)+"/unmatch",decision(payment,"Intento de rol sin permiso")),403);
        }
        assertStatus(new Client().get("/reconciliation/imports"),401);assertStatus(upload(admin,"/preview",account,csv,null,false),403);
        assertStatus(admin.raw("POST",path(movement)+"/match",decision(payment,"Sin token CSRF"),false),403);
    }
    @Test void concurrentMatchingAllowsExactlyOneMovementPerPayment()throws Exception{
        long institution=institution(0,0),payment=payment(institution,"35.00",unique("CONCURRENT"));JsonNode imported=imported(admin,unique("Cuenta"),HEADER+"2026-10-13,A,35,MXN\n2026-10-13,B,35,MXN");
        long first=imported.at("/movements/0/id").asLong(),second=imported.at("/movements/1/id").asLong();Client one=staff("BILLING"),two=staff("BILLING");CountDownLatch start=new CountDownLatch(1);
        CompletableFuture<MvcResult> a=CompletableFuture.supplyAsync(()->after(start,one,first,payment)),b=CompletableFuture.supplyAsync(()->after(start,two,second,payment));start.countDown();
        assertThat(new int[]{a.get(30,java.util.concurrent.TimeUnit.SECONDS).getResponse().getStatus(),b.get(30,java.util.concurrent.TimeUnit.SECONDS).getResponse().getStatus()}).containsExactlyInAnyOrder(200,409);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM bank_movements WHERE matched_payment_id=?",Long.class,payment)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM bank_reconciliation_events WHERE payment_id=?",Long.class,payment)).isEqualTo(1);
    }
    @Test void currencyIsPreservedAndCandidateSearchTreatsWildcardsLiterally()throws Exception{
        long institution=institution(0,0),payment=payment(institution,"12.00","ABC%"+unique("literal"));db.update("UPDATE payments SET currency='USD' WHERE id=?",payment);
        long movement=imported(admin,unique("Cuenta"),HEADER+"2026-10-13,EXTRANJERO,12.00,USD").at("/movements/0/id").asLong();
        JsonNode result=success(admin.perform(admin.builder("GET",path(movement)+"/candidates",false).param("q","ABC%")));assertThat(ids(result.path("items"))).contains(payment);
        success(admin.post(path(movement)+"/match",decision(payment,"Pago legado en la misma moneda USD")));assertThat(success(admin.get(path(movement))).at("/movement/currency").asText()).isEqualTo("USD");
    }
    private static Map<String,Object> decision(long payment,String reason){return Map.of("paymentId",payment,"reason",reason);}
    private static String path(long movement){return "/reconciliation/movements/"+movement;}
    private MvcResult after(CountDownLatch start,Client client,long movement,long payment){try{start.await();return client.post(path(movement)+"/match",decision(payment,"Conciliación concurrente explícita"));}catch(Exception e){throw new RuntimeException(e);}}
    private Client staff(String role)throws Exception{String name=unique("reconcile");success(admin.post("/users",Map.of("username",name,"displayName","Conciliación "+role,"password",PASSWORD,"role",role,"active",true)));return login(name,PASSWORD);}
    private long payment(long institution,String amount,String reference)throws Exception{Map<String,Object> input=new LinkedHashMap<>(PaymentIntegrationIT.paymentPayload(institution,10,0));input.put("amount",new BigDecimal(amount));input.put("reference",reference);return success(admin.post("/payments",input)).path("id").asLong();}
    private JsonNode imported(Client client,String account,String csv)throws Exception{JsonNode preview=success(upload(client,"/preview",account,csv,null,true));return success(upload(client,"/imports",account,csv,preview.path("hash").asText(),true));}
    private MvcResult upload(Client client,String suffix,String account,String csv,String hash,boolean csrf)throws Exception{
        client.refreshCsrf();MockHttpServletRequestBuilder builder=multipart("/api/reconciliation"+suffix).file(new MockMultipartFile("file","movimientos.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8))).param("account",account).session(client.session).cookie(client.cookies.values().toArray(jakarta.servlet.http.Cookie[]::new));
        if(hash!=null)builder.param("expectedHash",hash);if(csrf)builder.header("X-XSRF-TOKEN",client.csrf);return client.perform(builder);
    }
}
