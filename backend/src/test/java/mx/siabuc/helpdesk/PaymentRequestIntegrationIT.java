package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

class PaymentRequestIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    private static final byte[] PDF="%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jW0YAAAAASUVORK5CYII=");

    @Test void portalIsOptInPerInstitutionAdminOnlyAndRevocationAppliesToExistingSessions() throws Exception {
        long inst=institution(15,25);Client client=institutionUser(inst),billing=billing();
        assertThat(success(client.get("/client-finance/access")).path("enabled").asBoolean()).isFalse();
        assertStatus(client.get("/client-finance"),403);
        assertStatus(client.post("/client-finance/payment-requests",payload(inst,null,"50.00",10,20)),403);
        assertStatus(client.get("/client-finance-access"),403);assertStatus(billing.get("/client-finance-access"),403);
        assertStatus(billing.put("/client-finance-access/"+inst,Map.of("enabled",true)),403);
        enable(inst,true);
        assertThat(success(client.get("/client-finance/access")).path("enabled").asBoolean()).isTrue();
        assertThat(success(client.get("/client-finance")).path("institutionId").asLong()).isEqualTo(inst);
        assertStatus(admin.get("/client-finance"),403);assertStatus(billing.get("/client-finance"),403);
        enable(inst,false);assertStatus(client.get("/client-finance"),403);
        assertStatus(client.get("/client-finance/payment-requests"),403);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='CLIENT_FINANCE_ACCESS' AND entity_id=?",Long.class,inst)).isEqualTo(2);
        assertBalance(inst,0,15,0,25);
    }

    @Test void customerCanSubmitAndDownloadPdfAndImageBeforeApprovalWithoutCreditOrDisclosureToOthers() throws Exception {
        long inst=institution(15,25),other=institution(0,0);enable(inst,true);enable(other,true);
        Client client=institutionUser(inst),outsider=institutionUser(other),billing=billing();
        Map<String,Object> body=payload(inst,null,"150.00",30,40);
        JsonNode created=success(client.post("/client-finance/payment-requests",body));long id=created.path("request").path("id").asLong();
        assertThat(created.path("request").path("status").asText()).isEqualTo("PENDING");
        assertThat(success(client.post("/client-finance/payment-requests",body))).isEqualTo(created);
        body.put("amount",new BigDecimal("150.01"));assertStatus(client.post("/client-finance/payment-requests",body),409);
        UUID key=UUID.randomUUID();JsonNode pdf=success(upload(client,id,true,"../../transferencia.pdf",PDF,key));
        assertThat(success(upload(client,id,true,"../../transferencia.pdf",PDF,key))).isEqualTo(pdf);
        JsonNode png=success(upload(client,id,true,"deposito.png",PNG,UUID.randomUUID()));
        assertThat(pdf.path("filename").asText()).isEqualTo("transferencia.pdf");assertThat(png.path("contentType").asText()).isEqualTo("image/png");
        var download=client.get("/client-finance/payment-requests/attachments/"+pdf.path("id").asLong()+"/download");assertStatus(download,200);
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(PDF);
        assertThat(download.getResponse().getHeader("Content-Disposition")).startsWith("attachment;");
        assertThat(download.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(download.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(client.get("/client-finance/payment-requests/attachments/"+png.path("id").asLong()+"/download").getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(success(billing.get("/payment-requests/"+id)).path("attachments")).hasSize(2);
        assertStatus(outsider.get("/client-finance/payment-requests/"+id),404);
        assertStatus(outsider.get("/client-finance/payment-requests/attachments/"+pdf.path("id").asLong()+"/download"),404);
        assertStatus(upload(outsider,id,true,"ajeno.pdf",PDF,UUID.randomUUID()),404);
        assertStatus(client.post("/client-finance/payment-requests",payload(other,null,"10.00",1,0)),403);
        assertStatus(client.get("/payment-requests/"+id),403);
        assertStatus(client.post("/payment-requests/"+id+"/approve",Map.of("reason","Intento no autorizado")),403);
        assertNoPayment(inst);assertBalance(inst,0,15,0,25);
        enable(inst,false);assertStatus(client.get("/client-finance/payment-requests/attachments/"+pdf.path("id").asLong()+"/download"),403);
    }

    @Test void approvalCreatesExactlyOnePaymentAndCreditsWithEvidenceAndFrozenRequest() throws Exception {
        long inst=institution(15,25),policy=policy(inst,"200.00",60,80);enable(inst,true);
        Client client=institutionUser(inst),billing=billing();long id=create(client,true,payload(inst,policy,"100.00",30,40));
        JsonNode file=success(upload(client,id,true,"prueba.pdf",PDF,UUID.randomUUID()));
        assertNoPayment(inst);assertBalance(inst,0,15,0,25);
        JsonNode approved=success(billing.post("/payment-requests/"+id+"/approve",Map.of("reason","Depósito verificado")));
        long payment=approved.path("request").path("paymentId").asLong();assertThat(payment).isPositive();
        assertThat(approved.path("request").path("status").asText()).isEqualTo("APPROVED");
        assertThat(success(admin.post("/payment-requests/"+id+"/approve",Map.of("reason","Reintento")))).isEqualTo(approved);
        assertBalance(inst,0,45,0,65);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM payments WHERE institution_id=?",Long.class,inst)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT amount FROM payments WHERE id=?",BigDecimal.class,payment)).isEqualByComparingTo("100.00");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM payment_attachments WHERE payment_id=?",Long.class,payment)).isEqualTo(1);
        long copy=db.queryForObject("SELECT id FROM payment_attachments WHERE payment_id=?",Long.class,payment);
        assertThat(client.get("/client-finance/payment-attachments/"+copy+"/download").getResponse().getContentAsByteArray()).isEqualTo(PDF);
        assertThat(client.get("/client-finance/payment-requests/attachments/"+file.path("id").asLong()+"/download").getResponse().getContentAsByteArray()).isEqualTo(PDF);
        assertStatus(upload(client,id,true,"posterior.png",PNG,UUID.randomUUID()),409);
        assertStatus(billing.post("/payment-requests/"+id+"/reject",Map.of("reason","Cambiar decisión")),409);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM policy_history WHERE policy_id=? AND action='CREATE_PAYMENT'",Long.class,policy)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM policy_history WHERE policy_id=? AND action='ATTACH_PAYMENT_PROOF'",Long.class,policy)).isEqualTo(1);
    }

    @Test void rejectionNeedsReasonMayHaveNoProofAndNeverCreditsOrDeletesEvidence() throws Exception {
        long inst=institution(7,9);Client billing=billing();long empty=create(billing,false,payload(inst,null,"100.00",20,30));
        assertStatus(billing.post("/payment-requests/"+empty+"/approve",Map.of("reason","")),409);
        assertStatus(billing.post("/payment-requests/"+empty+"/reject",Map.of("reason"," ")),400);
        JsonNode rejected=success(billing.post("/payment-requests/"+empty+"/reject",Map.of("reason","Falta comprobante")));
        assertThat(success(billing.post("/payment-requests/"+empty+"/reject",Map.of("reason","Falta comprobante")))).isEqualTo(rejected);
        assertStatus(billing.post("/payment-requests/"+empty+"/reject",Map.of("reason","Reemplazar motivo")),409);
        assertStatus(billing.post("/payment-requests/"+empty+"/approve",Map.of("reason","")),409);
        long id=create(billing,false,payload(inst,null,"100.00",20,30));JsonNode proof=success(upload(billing,id,false,"incorrecto.png",PNG,UUID.randomUUID()));
        success(billing.post("/payment-requests/"+id+"/reject",Map.of("reason","Referencia no encontrada")));
        assertThat(billing.get("/payment-requests/attachments/"+proof.path("id").asLong()+"/download").getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertStatus(upload(billing,id,false,"nuevo.pdf",PDF,UUID.randomUUID()),409);
        assertNoPayment(inst);assertBalance(inst,0,7,0,9);
    }

    @Test void failedApprovalRollsBackPaymentCreditNotificationsAndDecisionAndCanBeRetried() throws Exception {
        long inst=institution(8,9),policy=policy(inst,"200.00",60,60);long id=create(admin,false,payload(inst,policy,"100.00",20,30));
        success(upload(admin,id,false,"prueba.pdf",PDF,UUID.randomUUID()));
        faultNotifications.forcePaymentFailureFor(inst);
        try{assertStatus(admin.post("/payment-requests/"+id+"/approve",Map.of("reason","Validada")),409);}finally{faultNotifications.resetPaymentFailure();}
        assertNoPayment(inst);assertBalance(inst,0,8,0,9);
        assertThat(success(admin.get("/payment-requests/"+id)).path("request").path("status").asText()).isEqualTo("PENDING");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM policy_history WHERE policy_id=?",Long.class,policy)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='APPROVE_PAYMENT_REQUEST' AND entity_id=?",Long.class,id)).isZero();
        success(admin.post("/payment-requests/"+id+"/approve",Map.of("reason","Validada")));assertBalance(inst,0,28,0,39);
    }

    @Test void policyLimitsAndCrossInstitutionAreRecheckedWhenApprovalIsRequested() throws Exception {
        long inst=institution(0,0),other=institution(0,0),policy=policy(inst,"100.00",10,20);
        assertStatus(admin.post("/payment-requests",payload(other,policy,"10.00",1,0)),404);
        long zero=create(admin,false,payload(inst,policy,"0.00",5,0));success(upload(admin,zero,false,"cero.pdf",PDF,UUID.randomUUID()));
        assertStatus(admin.post("/payment-requests/"+zero+"/approve",Map.of("reason","")),409);assertNoPayment(inst);
        long over=create(admin,false,payload(inst,policy,"101.00",10,20));success(upload(admin,over,false,"exceso.pdf",PDF,UUID.randomUUID()));
        assertStatus(admin.post("/payment-requests/"+over+"/approve",Map.of("reason","")),409);
        assertThat(success(admin.get("/payment-requests/"+over)).path("request").path("status").asText()).isEqualTo("PENDING");assertNoPayment(inst);assertBalance(inst,0,0,0,0);
    }

    @Test void proofTypesCountAndIdempotencyAreEnforcedBeforeApproval() throws Exception {
        long inst=institution(0,0),id=create(admin,false,payload(inst,null,"10.00",1,0));
        for(String name:List.of("datos.txt","ejecutable.exe","comprobante.html","registro.json"))assertStatus(upload(admin,id,false,name,PDF,UUID.randomUUID()),400);
        assertStatus(upload(admin,id,false,"falso.pdf","<html>x</html>".getBytes(StandardCharsets.UTF_8),UUID.randomUUID()),400);
        assertStatus(upload(admin,id,false,"vacio.pdf",new byte[0],UUID.randomUUID()),400);
        assertStatus(upload(admin,id,false,"grande.pdf",new byte[10485761],UUID.randomUUID()),413);
        UUID key=UUID.randomUUID();success(upload(admin,id,false,"primero.pdf",PDF,key));
        assertStatus(upload(admin,id,false,"otro.pdf",PDF,key),409);
        long actor=success(admin.get("/auth/me")).path("id").asLong();
        db.update("INSERT INTO payment_request_attachments(request_id,uploaded_by,submission_id,filename,content_type,size_bytes,sha256,file_bytes) SELECT ?,?,gen_random_uuid(),'previo-'||n||'.png','image/png',8,repeat('0',64),decode('89504e470d0a1a0a','hex') FROM generate_series(1,9) n",id,actor);
        assertStatus(upload(admin,id,false,"onceavo.pdf",PDF,UUID.randomUUID()),409);
        assertThat(success(admin.get("/payment-requests/"+id)).path("attachments")).hasSize(10);assertNoPayment(inst);
    }

    @Test void concurrentApprovalsReturnSameMovementAndSingleCredit() throws Exception {
        long inst=institution(2,3),id=create(admin,false,payload(inst,null,"70.00",20,30));success(upload(admin,id,false,"pago.pdf",PDF,UUID.randomUUID()));
        Client first=billing(),second=billing();first.refreshCsrf();second.refreshCsrf();var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var a=executor.submit(()->{start.await();return first.raw("POST","/payment-requests/"+id+"/approve",Map.of("reason","Validado"),true);});var b=executor.submit(()->{start.await();return second.raw("POST","/payment-requests/"+id+"/approve",Map.of("reason","Validado"),true);});start.countDown();JsonNode r1=success(a.get(30,TimeUnit.SECONDS)),r2=success(b.get(30,TimeUnit.SECONDS));assertThat(r1.path("request").path("paymentId")).isEqualTo(r2.path("request").path("paymentId"));}finally{executor.shutdownNow();}
        assertThat(db.queryForObject("SELECT COUNT(*) FROM payments WHERE institution_id=?",Long.class,inst)).isEqualTo(1);assertBalance(inst,0,22,0,33);
    }

    private Map<String,Object> payload(long institution,Long policy,String amount,int email,int remote){Map<String,Object> body=new LinkedHashMap<>();body.put("requestId",UUID.randomUUID().toString());body.put("institutionId",institution);body.put("policyId",policy);body.put("reference",unique("SOL-"));body.put("amount",new BigDecimal(amount));body.put("paidOn","2026-10-13");body.put("method","TRANSFER");body.put("emailMinutes",email);body.put("remoteMinutes",remote);body.put("description","Pago declarado por la institución, pendiente de revisión.");return body;}
    private long create(Client client,boolean own,Map<String,Object> payload)throws Exception{return success(client.post((own?"/client-finance":"")+"/payment-requests",payload)).path("request").path("id").asLong();}
    private long policy(long inst,String amount,int email,int remote)throws Exception{return success(admin.post("/policies",PolicyIntegrationIT.policyPayload(inst,amount,email,remote))).path("policy").path("id").asLong();}
    private void enable(long inst,boolean enabled)throws Exception{success(admin.put("/client-finance-access/"+inst,Map.of("enabled",enabled)));}
    private void assertNoPayment(long inst){assertThat(db.queryForObject("SELECT COUNT(*) FROM payments WHERE institution_id=?",Long.class,inst)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM notification_outbox n JOIN payments p ON p.id=n.payment_id WHERE p.institution_id=?",Long.class,inst)).isZero();}
    private Client billing()throws Exception{String username=unique("requestbilling");success(admin.post("/users",Map.of("username",username,"displayName","Cobranza solicitudes","password",PASSWORD,"role","BILLING","active",true)));return login(username,PASSWORD);}
    private MvcResult upload(Client client,long id,boolean own,String filename,byte[] bytes,UUID key)throws Exception{client.refreshCsrf();return client.perform(multipart("/api"+(own?"/client-finance":"")+"/payment-requests/"+id+"/attachments").file(new MockMultipartFile("file",filename,"text/html",bytes)).param("requestId",key.toString()).session(client.session).cookie(client.cookies.values().toArray(jakarta.servlet.http.Cookie[]::new)).header("X-XSRF-TOKEN",client.csrf));}
}
