package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

class PaymentAttachmentIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    private static final byte[] PDF="%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jW0YAAAAASUVORK5CYII=");

    @Test void billingAndAdministratorsCanAttachAndDownloadButClientsCannotReadFinancialFiles() throws Exception {
        long institution=institution(40,50),payment=payment(institution);Client billing=billing(),client=institutionUser(institution);
        JsonNode metadata=success(upload(billing,payment,"../../comprobante.pdf","text/html",PDF,UUID.randomUUID(),true));long id=metadata.path("id").asLong();
        assertThat(metadata.path("paymentId").asLong()).isEqualTo(payment);
        assertThat(metadata.path("filename").asText()).isEqualTo("comprobante.pdf");
        assertThat(metadata.path("contentType").asText()).isEqualTo("application/pdf");
        assertThat(metadata.path("uploadedName").asText()).isEqualTo("Cobranza comprobantes");
        assertThat(metadata.path("sha256").asText()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PDF)));
        assertThat(metadata.has("fileBytes")).isFalse();
        assertThat(ids(success(billing.get("/payments/"+payment+"/attachments")))).containsExactly(id);
        assertThat(ids(success(admin.get("/payments/"+payment+"/attachments")))).containsExactly(id);
        MvcResult download=billing.get("/payment-attachments/"+id+"/download");assertStatus(download,200);
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(PDF);
        assertThat(download.getResponse().getHeader("Content-Disposition")).startsWith("attachment;").contains("comprobante.pdf");
        assertThat(download.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(download.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertStatus(client.get("/payments/"+payment+"/attachments"),403);
        assertStatus(client.get("/payment-attachments/"+id+"/download"),403);
        assertStatus(upload(client,payment,"cliente.pdf","application/pdf",PDF,UUID.randomUUID(),true),403);
        assertStatus(new Client().get("/payment-attachments/"+id+"/download"),401);
        assertStatus(upload(billing,payment,"sin-csrf.pdf","application/pdf",PDF,UUID.randomUUID(),false),403);
        assertBalance(institution,0,50,0,70);
    }

    @Test void idempotentProofOnPolicyPaymentAddsHistoryOnceWithoutChangingMoneyMinutesOrSendingNotices() throws Exception {
        long institution=institution(30,40);
        long policy=success(admin.post("/policies",PolicyIntegrationIT.policyPayload(institution,"100.00",60,30))).path("policy").path("id").asLong();
        var input=new java.util.LinkedHashMap<>(PaymentIntegrationIT.paymentPayload(institution,10,20));input.put("policyId",policy);input.put("amount",new java.math.BigDecimal("50.00"));
        long payment=success(admin.post("/payments",input)).path("id").asLong();
        Map<String,Object> before=db.queryForMap("SELECT * FROM payments WHERE id=?",payment);
        long notices=db.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE payment_id=?",Long.class,payment);
        UUID request=UUID.randomUUID();JsonNode first=success(upload(admin,payment,"transferencia.png","application/pdf",PNG,request,true));
        assertThat(success(upload(admin,payment,"transferencia.png","application/pdf",PNG,request,true))).isEqualTo(first);
        assertStatus(upload(admin,payment,"otro.png","image/png",PNG,request,true),409);
        assertThat(db.queryForMap("SELECT * FROM payments WHERE id=?",payment)).isEqualTo(before);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE payment_id=?",Long.class,payment)).isEqualTo(notices);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_type='PAYMENT' AND entity_id=? AND action='ATTACH_PAYMENT_PROOF'",Long.class,payment)).isEqualTo(1);
        JsonNode detail=success(admin.get("/policies/"+policy));
        assertThat(detail.path("history")).hasSize(3);
        assertThat(detail.path("history").get(0).path("action").asText()).isEqualTo("ATTACH_PAYMENT_PROOF");
        assertThat(detail.path("history").get(0).path("description").asText()).contains("transferencia.png","Sin modificación de importe ni minutos");
        assertThat(detail.path("policy").path("paidAmount").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(detail.path("policy").path("creditedEmailMinutes").asLong()).isEqualTo(10);
        assertBalance(institution,0,40,0,60);
    }

    @Test void historicalVoidedPaymentOfInactiveInstitutionCanReceiveEvidenceWithoutRecredit() throws Exception {
        long institution=institution(40,50),payment=payment(institution);Client billing=billing();
        assertStatus(admin.post("/payments/"+payment+"/void",Map.of("reason","Referencia anulada con evidencia pendiente")),200);
        assertStatus(admin.delete("/institutions/"+institution),204);
        Map<String,Object> before=db.queryForMap("SELECT * FROM institutions WHERE id=?",institution);
        JsonNode proof=success(upload(billing,payment,"cancelacion.pdf","application/pdf",PDF,UUID.randomUUID(),true));
        assertStatus(billing.get("/payment-attachments/"+proof.path("id").asLong()+"/download"),200);
        assertThat(db.queryForMap("SELECT * FROM institutions WHERE id=?",institution)).isEqualTo(before);
        assertThat(db.queryForObject("SELECT voided FROM payments WHERE id=?",Boolean.class,payment)).isTrue();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM payments WHERE institution_id=?",Long.class,institution)).isEqualTo(1);
    }

    @Test void rejectsDiagnosticsDisguisedFilesEmptyOversizeAndMissingPayment() throws Exception {
        long payment=payment(institution(40,50));
        for(String name:List.of("informe.txt","datos.json","archivo.zip","programa.exe","pagina.html"))
            assertStatus(upload(admin,payment,name,"application/pdf",PDF,UUID.randomUUID(),true),400);
        assertStatus(upload(admin,payment,"falso.pdf","application/pdf","<html>falso</html>".getBytes(StandardCharsets.UTF_8),UUID.randomUUID(),true),400);
        assertStatus(upload(admin,payment,"falso.png","image/png",PDF,UUID.randomUUID(),true),400);
        assertStatus(upload(admin,payment,"vacio.pdf","application/pdf",new byte[0],UUID.randomUUID(),true),400);
        assertStatus(upload(admin,payment,"grande.pdf","application/pdf",new byte[PaymentAttachmentService.MAX_FILE_BYTES+1],UUID.randomUUID(),true),413);
        assertStatus(upload(admin,Long.MAX_VALUE,"sin-pago.pdf","application/pdf",PDF,UUID.randomUUID(),true),404);
        assertThat(success(admin.get("/payments/"+payment+"/attachments"))).isEmpty();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='ATTACH_PAYMENT_PROOF' AND entity_id=?",Long.class,payment)).isZero();
    }

    @Test void paymentFileCountLimitAcceptsTenAndRejectsEleven() throws Exception {
        long payment=payment(institution(40,50)),actor=success(admin.get("/auth/me")).path("id").asLong();
        db.update("""
            INSERT INTO payment_attachments(payment_id,uploaded_by,request_id,filename,content_type,size_bytes,sha256,file_bytes)
            SELECT ?,?,gen_random_uuid(),'previo-'||n||'.png','image/png',8,repeat('0',64),decode('89504e470d0a1a0a','hex')
            FROM generate_series(1,9) AS n
            """,payment,actor);
        success(upload(admin,payment,"decimo.png","image/png",PNG,UUID.randomUUID(),true));
        assertStatus(upload(admin,payment,"onceavo.png","image/png",PNG,UUID.randomUUID(),true),409);
        assertThat(success(admin.get("/payments/"+payment+"/attachments"))).hasSize(10);
    }

    @Test void paymentTotalSizeLimitRejectsAdditionalBytesEvenBelowFileCountLimit() throws Exception {
        long payment=payment(institution(40,50)),actor=success(admin.get("/auth/me")).path("id").asLong();
        try {
            db.update("""
                INSERT INTO payment_attachments(payment_id,uploaded_by,request_id,filename,content_type,size_bytes,sha256,file_bytes)
                SELECT ?,?,gen_random_uuid(),'previo-'||n||'.png','image/png',10485760,repeat('0',64),decode('89504e470d0a1a0a','hex')||convert_to(repeat('x',10485752),'UTF8')
                FROM generate_series(1,5) AS n
                """,payment,actor);
            assertStatus(upload(admin,payment,"limite.png","image/png",PNG,UUID.randomUUID(),true),409);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM payment_attachments WHERE payment_id=?",Long.class,payment)).isEqualTo(5);
            assertThat(db.queryForObject("SELECT SUM(size_bytes) FROM payment_attachments WHERE payment_id=?",Long.class,payment)).isEqualTo(PaymentAttachmentService.MAX_PAYMENT_BYTES);
        } finally {db.update("DELETE FROM payment_attachments WHERE payment_id=?",payment);}
    }

    private long payment(long institution)throws Exception{return success(admin.post("/payments",PaymentIntegrationIT.paymentPayload(institution,10,20))).path("id").asLong();}
    private Client billing()throws Exception{
        String name=unique("proofbilling");success(admin.post("/users",Map.of("username",name,"displayName","Cobranza comprobantes","password",PASSWORD,"role","BILLING","active",true)));
        return login(name,PASSWORD);
    }
    private MvcResult upload(Client client,long payment,String filename,String contentType,byte[] bytes,UUID requestId,boolean csrf)throws Exception{
        client.refreshCsrf();MockHttpServletRequestBuilder builder=multipart("/api/payments/"+payment+"/attachments")
            .file(new MockMultipartFile("file",filename,contentType,bytes)).param("requestId",requestId.toString())
            .session(client.session).cookie(client.cookies.values().toArray(jakarta.servlet.http.Cookie[]::new));
        if(csrf)builder.header("X-XSRF-TOKEN",client.csrf);return client.perform(builder);
    }
}
