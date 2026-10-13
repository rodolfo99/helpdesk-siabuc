package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentReceiptIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    @Test
    void issuesExactlyOnceAndPreservesInstitutionAndAuthorSnapshotWithoutFinancialOrMailEffects()throws Exception{
        long institution=institution(0,0);Client billing=billing("Responsable inicial");
        long payment=payment(billing,institution,null,"1250.50",65,30,"Cuota de atención institucional");
        Map<String,Object> beforePayment=db.queryForMap("SELECT * FROM payments WHERE id=?",payment);
        Map<String,Object> beforeInstitution=db.queryForMap("SELECT * FROM institutions WHERE id=?",institution);
        long notices=mailCount(payment);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM payment_receipts WHERE payment_id=?",Long.class,payment)).isZero();
        MvcResult issued=billing.get(staff(payment));assertStatus(issued,200);
        String folio=issued.getResponse().getHeader("X-Receipt-Folio");assertThat(folio).isEqualTo(String.format("SIABUC-REC-%010d",payment));
        assertThat(issued.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(issued.getResponse().getHeader("Content-Disposition")).contains("attachment;",folio+".pdf");
        assertThat(issued.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(issued.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(issued.getResponse().getHeader("Content-Security-Policy")).contains("sandbox");
        String text=text(issued);assertThat(text).contains("1250.50 MXN","Correo: 1 h 5 min (65 minutos)","Escritorio remoto: 0 h 30 min (30 minutos)","Responsable inicial","NO ES COMPROBANTE FISCAL", "CFDI NI FACTURA");
        assertThat(db.queryForMap("SELECT * FROM payments WHERE id=?",payment)).isEqualTo(beforePayment);
        assertThat(db.queryForMap("SELECT * FROM institutions WHERE id=?",institution)).isEqualTo(beforeInstitution);
        Map<String,Object> receipt=db.queryForMap("SELECT * FROM payment_receipts WHERE payment_id=?",payment);
        db.update("UPDATE institutions SET name='Institución RENOMBRADA',contact_name='Contacto cambiado',email='nuevo@example.test' WHERE id=?",institution);
        db.update("UPDATE app_users SET display_name='Responsable RENOMBRADO' WHERE id=(SELECT created_by FROM payments WHERE id=?)",payment);
        MvcResult repeated=admin.get(staff(payment));assertStatus(repeated,200);
        assertThat(repeated.getResponse().getHeader("X-Receipt-Folio")).isEqualTo(folio);
        assertThat(text(repeated)).contains(beforeInstitution.get("name").toString(),"Responsable inicial").doesNotContain("Institución RENOMBRADA","Responsable RENOMBRADO","Contacto cambiado","nuevo@example.test");
        assertThat(db.queryForMap("SELECT * FROM payment_receipts WHERE payment_id=?",payment)).isEqualTo(receipt);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='ISSUE_PAYMENT_RECEIPT' AND entity_id=?",Long.class,payment)).isEqualTo(1);
        assertThat(mailCount(payment)).isEqualTo(notices);
        assertBalance(institution,0,65,0,30);
    }

    @Test
    void subsequentDownloadMarksEveryPageVoidedButNeverRewritesTheIssuedSnapshot()throws Exception{
        long institution=institution(0,0);
        String notes="Descripción con acentos: depósito, póliza y atención.\n".repeat(110)+"Última línea conservada 😀 https://example.test";
        long payment=payment(admin,institution,null,"300.00",60,0,notes);
        MvcResult original=admin.get(staff(payment));assertStatus(original,200);
        Map<String,Object> snapshot=db.queryForMap("SELECT * FROM payment_receipts WHERE payment_id=?",payment);
        success(admin.post("/payments/"+payment+"/void",Map.of("reason","Referencia duplicada; se anuló el registro")));
        long noticesAfterVoid=mailCount(payment);
        MvcResult cancelled=admin.get(staff(payment));assertStatus(cancelled,200);
        assertThat(cancelled.getResponse().getHeader("X-Receipt-Folio")).isEqualTo(original.getResponse().getHeader("X-Receipt-Folio"));
        assertThat(text(cancelled)).contains("ANULACIÓN DEL REGISTRO","Referencia duplicada", "Estado del pago al emitir por primera vez: VIGENTE", "Última línea conservada [U+1F600]");
        try(PDDocument pdf=Loader.loadPDF(cancelled.getResponse().getContentAsByteArray())){
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            assertThat(pdf.getDocumentCatalog().getOpenAction()).isNull();
            for(int page=1;page<=pdf.getNumberOfPages();page++){
                PDFTextStripper stripper=new PDFTextStripper();stripper.setStartPage(page);stripper.setEndPage(page);
                assertThat(stripper.getText(pdf)).contains("ANULADO","Página "+page);
                assertThat(pdf.getPage(page-1).getAnnotations()).isEmpty();
            }
        }
        assertThat(db.queryForMap("SELECT * FROM payment_receipts WHERE payment_id=?",payment)).isEqualTo(snapshot);
        assertThat(mailCount(payment)).isEqualTo(noticesAfterVoid);assertBalance(institution,0,0,0,0);
    }

    @Test
    void clientReceiptRequiresAnEnabledPortalAndOwnInstitutionOnEveryDownload()throws Exception{
        long institution=institution(0,0),otherInstitution=institution(0,0);
        long payment=payment(admin,institution,null,"100.00",60,0,"Compra propia"),foreign=payment(admin,otherInstitution,null,"200.00",60,0,"Compra ajena");
        Client client=institutionUser(institution),other=institutionUser(otherInstitution);
        assertStatus(client.get(client(payment)),403);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM payment_receipts WHERE payment_id=?",Long.class,payment)).isZero();
        enable(institution,true);enable(otherInstitution,true);
        assertStatus(client.get(client(foreign)),404);assertStatus(other.get(client(payment)),404);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM payment_receipts WHERE payment_id IN (?,?)",Long.class,payment,foreign)).isZero();
        MvcResult allowed=client.get(client(payment));assertStatus(allowed,200);
        assertThat(text(allowed)).contains("100.00 MXN","Versión para la institución").doesNotContain("Compra propia","Compra ajena","RESPONSABLE DEL REGISTRO","DESCRIPCIÓN DEL PAGO");
        assertStatus(client.get(staff(payment)),403);
        assertStatus(new Client().get(client(payment)),401);assertStatus(new Client().get(staff(payment)),401);
        assertStatus(admin.get(client(payment)),403);
        enable(institution,false);assertStatus(client.get(client(payment)),403);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM payment_receipts WHERE payment_id=?",Long.class,payment)).isEqualTo(1);
        assertBalance(institution,0,60,0,0);
    }

    @Test
    void clientPdfOmitsPrivateFieldsWithoutChangingStaffSnapshotOrFolioRegardlessOfFirstIssuer()throws Exception{
        for(boolean staffFirst:new boolean[]{false,true}){
            long institution=institution(0,0);Client client=institutionUser(institution);enable(institution,true);
            String privateNotes=unique("NOTA_INTERNA"),privateAuthor=unique("RESPONSABLE_PRIVADO"),privateReason=unique("MOTIVO_INTERNO"),privateVoider=unique("ANULADOR_PRIVADO");
            Client billing=billing(privateAuthor);String authorUsername=success(billing.get("/auth/me")).path("username").asText();
            String voiderUsername=unique("receiptvoider");success(admin.post("/users",Map.of("username",voiderUsername,"displayName",privateVoider,"password",PASSWORD,"role","ADMIN","active",true)));
            Client voider=login(voiderUsername,PASSWORD);
            long payment=payment(billing,institution,null,"125.25",15,5,privateNotes);
            if(staffFirst)assertStatus(billing.get(staff(payment)),200);
            success(voider.post("/payments/"+payment+"/void",Map.of("reason",privateReason)));
            long notices=mailCount(payment);
            MvcResult publicPdf=client.get(client(payment));assertStatus(publicPdf,200);
            String publicText=text(publicPdf);
            assertThat(publicText).contains("ANULADO","125.25 MXN","Correo: 0 h 15 min","Escritorio remoto: 0 h 5 min","NO ES COMPROBANTE FISCAL")
                .doesNotContain(privateNotes,privateAuthor,authorUsername,privateReason,privateVoider,voiderUsername,"RESPONSABLE DEL REGISTRO","DESCRIPCIÓN DEL PAGO","Anulado por:");
            Map<String,Object> stored=db.queryForMap("SELECT * FROM payment_receipts WHERE payment_id=?",payment);
            assertThat(db.queryForObject("SELECT snapshot::text FROM payment_receipts WHERE payment_id=?",String.class,payment)).contains(privateNotes,privateAuthor,authorUsername);
            if(!staffFirst)assertThat(db.queryForObject("SELECT snapshot::text FROM payment_receipts WHERE payment_id=?",String.class,payment)).contains(privateReason,voiderUsername);
            MvcResult fullPdf=billing.get(staff(payment));assertStatus(fullPdf,200);
            assertThat(text(fullPdf)).contains(privateNotes,privateAuthor,authorUsername,privateReason,privateVoider,voiderUsername,"ANULADO");
            assertThat(fullPdf.getResponse().getHeader("X-Receipt-Folio")).isEqualTo(publicPdf.getResponse().getHeader("X-Receipt-Folio"));
            assertThat(db.queryForMap("SELECT * FROM payment_receipts WHERE payment_id=?",payment)).isEqualTo(stored);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='ISSUE_PAYMENT_RECEIPT' AND entity_id=?",Long.class,payment)).isEqualTo(1);
            assertThat(mailCount(payment)).isEqualTo(notices);assertBalance(institution,0,0,0,0);
        }
    }

    @Test
    void staffCanIssueHistoricalVoidedReceiptsForInactiveInstitutionsWhileClientCannot()throws Exception{
        long institution=institution(0,0),payment=payment(admin,institution,null,"70.00",10,0,"Pago histórico");
        Client billing=billing("Cobranza histórica"),client=institutionUser(institution);enable(institution,true);
        success(admin.post("/payments/"+payment+"/void",Map.of("reason","Registro corregido antes de emitir recibo")));
        assertStatus(admin.delete("/institutions/"+institution),204);
        Map<String,Object> before=db.queryForMap("SELECT * FROM institutions WHERE id=?",institution);
        MvcResult issued=billing.get(staff(payment));assertStatus(issued,200);
        assertThat(text(issued)).contains("Estado del pago al emitir por primera vez: ANULADO","Registro corregido antes de emitir recibo");
        assertThat(client.get(client(payment)).getResponse().getStatus()).isIn(401,403);
        assertThat(db.queryForMap("SELECT * FROM institutions WHERE id=?",institution)).isEqualTo(before);
        assertStatus(billing.get(staff(Long.MAX_VALUE)),404);
    }

    @Test
    void zeroAmountCreditAndMoneyOnlyInstallmentProduceDifferentAdministrativeConcepts()throws Exception{
        long institution=institution(0,0);
        long policy=success(admin.post("/policies",Map.of("institutionId",institution,"folio",unique("REC-POL"),"description","Contrato de horas","validFrom","2026-01-01","validUntil","2026-12-31","agreedAmount",new BigDecimal("500.00"),"emailMinutes",60,"remoteMinutes",30))).at("/policy/id").asLong();
        long money=payment(admin,institution,policy,"500.00",0,0,"Liquidación sin liberar horas");
        long credit=payment(admin,institution,policy,"0.00",60,30,"Liberación de horas pagadas");
        JsonNode policyInfo=success(admin.get("/policies/"+policy));String folio=policyInfo.at("/policy/folio").asText();
        MvcResult cash=admin.get(staff(money)),hours=admin.get(staff(credit));assertStatus(cash,200);assertStatus(hours,200);
        assertThat(text(cash)).contains("500.00 MXN","abono monetario sin nueva acreditación",folio);
        assertThat(text(hours)).contains("0.00 MXN (sin nuevo cobro)","póliza previamente liquidada",folio,"Correo: 1 h 0 min");
        assertThat(success(admin.get("/policies/"+policy)).path("policy")).isEqualTo(policyInfo.path("policy"));
        assertBalance(institution,0,60,0,30);
    }

    @Test
    void preservesHistoricalCurrencyAndCardMethodWithoutInventingPesosOrExchangeRates()throws Exception{
        long institution=institution(0,0),actor=success(admin.get("/auth/me")).path("id").asLong();
        long payment=db.queryForObject("INSERT INTO payments(request_id,institution_id,reference,amount,currency,paid_on,method,email_minutes,remote_minutes,notes,created_by) VALUES(?,?,?,?,?,?,'CARD',10,0,'Pago legado',?) RETURNING id",Long.class,UUID.randomUUID(),institution,unique("LEGADO"),new BigDecimal("20.25"),"USD",Date.valueOf("2025-12-10"),actor);
        db.update("UPDATE institutions SET email_allocated_minutes=email_allocated_minutes+10 WHERE id=?",institution);
        MvcResult receipt=admin.get(staff(payment));assertStatus(receipt,200);
        assertThat(text(receipt)).contains("20.25 USD","Tarjeta (registro histórico)","2025-12-10").doesNotContain("20.25 MXN");
        assertBalance(institution,0,10,0,0);
    }

    @Test
    void simultaneousFirstDownloadsKeepOneFolioSnapshotAndAudit()throws Exception{
        long institution=institution(0,0),payment=payment(admin,institution,null,"85.00",5,0,"Emisión concurrente");
        Client first=billing("Primero"),second=billing("Segundo");CountDownLatch start=new CountDownLatch(1);
        CompletableFuture<MvcResult> a=CompletableFuture.supplyAsync(()->getAfter(start,first,staff(payment)));
        CompletableFuture<MvcResult> b=CompletableFuture.supplyAsync(()->getAfter(start,second,staff(payment)));start.countDown();
        MvcResult one=a.get(30,java.util.concurrent.TimeUnit.SECONDS),two=b.get(30,java.util.concurrent.TimeUnit.SECONDS);assertStatus(one,200);assertStatus(two,200);
        assertThat(one.getResponse().getHeader("X-Receipt-Folio")).isEqualTo(two.getResponse().getHeader("X-Receipt-Folio"));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM payment_receipts WHERE payment_id=?",Long.class,payment)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='ISSUE_PAYMENT_RECEIPT' AND entity_id=?",Long.class,payment)).isEqualTo(1);
        assertBalance(institution,0,5,0,0);
    }

    private static String staff(long payment){return "/payments/"+payment+"/receipt.pdf";}
    private static String client(long payment){return "/client-finance/payments/"+payment+"/receipt.pdf";}
    private static MvcResult getAfter(CountDownLatch latch,Client client,String path){try{latch.await();return client.get(path);}catch(Exception e){throw new RuntimeException(e);}}
    private String text(MvcResult result)throws Exception{try(PDDocument document=Loader.loadPDF(result.getResponse().getContentAsByteArray())){return new PDFTextStripper().getText(document).replaceAll("\\s+"," ");}}
    private long mailCount(long payment){return db.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE payment_id=?",Long.class,payment);}
    private void enable(long institution,boolean enabled)throws Exception{long actor=success(admin.get("/auth/me")).path("id").asLong();db.update("INSERT INTO client_finance_access(institution_id,enabled,updated_by) VALUES(?,?,?) ON CONFLICT (institution_id) DO UPDATE SET enabled=EXCLUDED.enabled,updated_by=EXCLUDED.updated_by",institution,enabled,actor);}
    private Client billing(String displayName)throws Exception{String username=unique("receiptbilling");success(admin.post("/users",Map.of("username",username,"displayName",displayName,"password",PASSWORD,"role","BILLING","active",true)));return login(username,PASSWORD);}
    private long payment(Client author,long institution,Long policy,String amount,int email,int remote,String notes)throws Exception{
        Map<String,Object> input=new LinkedHashMap<>();input.put("requestId",UUID.randomUUID().toString());input.put("institutionId",institution);input.put("policyId",policy);input.put("reference",unique("REC"));input.put("amount",new BigDecimal(amount));input.put("currency","MXN");input.put("paidOn","2026-10-13");input.put("method","DEPOSIT");input.put("emailMinutes",email);input.put("remoteMinutes",remote);input.put("notes",notes);return success(author.post("/payments",input)).path("id").asLong();
    }
}
