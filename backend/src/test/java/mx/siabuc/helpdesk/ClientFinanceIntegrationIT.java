package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

class ClientFinanceIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    private static final byte[] PDF="%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final String PRIVATE="NOTA_INTERNA_FINANCIERA_NO_PUBLICAR_6";

    @Test void enabledPortalOnlyExposesOwnSanitizedPoliciesPaymentsAndHistoricalProofs() throws Exception {
        long inst=institution(0,0),other=institution(0,0);Client client=institutionUser(inst),outsider=institutionUser(other);
        var policyInput=new LinkedHashMap<>(PolicyIntegrationIT.policyPayload(inst,"100.00",10,20));policyInput.put("description",PRIVATE);
        long policy=success(admin.post("/policies",policyInput)).path("policy").path("id").asLong();
        var paymentInput=new LinkedHashMap<>(PaymentIntegrationIT.paymentPayload(inst,10,20));paymentInput.put("policyId",policy);paymentInput.put("amount",new BigDecimal("50.00"));paymentInput.put("notes",PRIVATE);
        long payment=success(admin.post("/payments",paymentInput)).path("id").asLong();
        long proof=success(upload(admin,"/payments/"+payment+"/attachments")).path("id").asLong();
        long otherPayment=success(admin.post("/payments",PaymentIntegrationIT.paymentPayload(other,1,1))).path("id").asLong();
        long otherProof=success(upload(admin,"/payments/"+otherPayment+"/attachments")).path("id").asLong();
        assertStatus(client.get("/client-finance/payments/"+payment+"/attachments"),403);
        assertStatus(client.get("/client-finance/payment-attachments/"+proof+"/download"),403);
        enable(inst);enable(other);
        JsonNode view=success(client.get("/client-finance"));assertThat(view.toString()).doesNotContain(PRIVATE,"description","notes","cancelReason","voidReason","requestId","createdBy","voidedBy");
        assertThat(ids(view.path("policies"))).containsExactly(policy);assertThat(ids(view.path("payments"))).containsExactly(payment);
        assertThat(view.path("policies").get(0).path("paidAmount").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(view.path("policies").get(0).path("remainingAmount").decimalValue()).isEqualByComparingTo("50.00");
        JsonNode files=success(client.get("/client-finance/payments/"+payment+"/attachments"));assertThat(ids(files)).containsExactly(proof);assertThat(files.toString()).doesNotContain("uploadedBy","uploadedName","fileBytes");
        var result=client.get("/client-finance/payment-attachments/"+proof+"/download");assertStatus(result,200);assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(PDF);
        assertStatus(client.get("/client-finance/payments/"+otherPayment+"/attachments"),404);assertStatus(client.get("/client-finance/payment-attachments/"+otherProof+"/download"),404);
        assertStatus(outsider.get("/client-finance/payment-attachments/"+proof+"/download"),404);
        assertStatus(client.get("/payments/"+payment+"/attachments"),403);assertStatus(client.get("/payment-attachments/"+proof+"/download"),403);
        assertStatus(upload(client,"/payments/"+payment+"/attachments"),403);
        success(admin.post("/payments/"+payment+"/void",Map.of("reason",PRIVATE)));
        view=success(client.get("/client-finance"));assertThat(view.toString()).doesNotContain(PRIVATE,"voidReason");assertThat(view.path("payments").get(0).path("voided").asBoolean()).isTrue();
        assertStatus(client.get("/client-finance/payment-attachments/"+proof+"/download"),200);
        success(admin.post("/policies/"+policy+"/cancel",Map.of("reason",PRIVATE)));
        JsonNode cancelled=success(client.get("/client-finance")).path("policies").get(0);assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");assertThat(cancelled.path("remainingAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(cancelled.toString()).doesNotContain(PRIVATE);
    }

    @Test void privateApprovalReasonIsAbsentFromAllClientRequestResponsesButRejectionReasonIsPublic() throws Exception {
        long inst=institution(0,0);enable(inst);Client client=institutionUser(inst);
        Map<String,Object> body=request(inst);JsonNode created=success(client.post("/client-finance/payment-requests",body));long id=created.path("request").path("id").asLong();
        success(upload(client,"/client-finance/payment-requests/"+id+"/attachments"));
        success(admin.post("/payment-requests/"+id+"/approve",Map.of("reason",PRIVATE)));
        assertThat(success(admin.get("/payment-requests/"+id)).path("request").path("decisionReason").asText()).isEqualTo(PRIVATE);
        assertThat(success(client.get("/client-finance/payment-requests")).toString()).doesNotContain(PRIVATE);
        JsonNode detail=success(client.get("/client-finance/payment-requests/"+id));assertThat(detail.toString()).doesNotContain(PRIVATE);assertThat(detail.path("request").path("decisionReason").isNull()).isTrue();
        assertThat(success(client.post("/client-finance/payment-requests",body)).toString()).doesNotContain(PRIVATE);
        long rejected=success(client.post("/client-finance/payment-requests",request(inst))).path("request").path("id").asLong();
        success(admin.post("/payment-requests/"+rejected+"/reject",Map.of("reason","Adjunta una referencia legible")));
        assertThat(success(client.get("/client-finance/payment-requests/"+rejected)).path("request").path("decisionReason").asText()).isEqualTo("Adjunta una referencia legible");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM payments WHERE institution_id=?",Long.class,inst)).isEqualTo(1);
    }

    @Test void inactiveInstitutionsAndRevokedClientsCannotReadOldFinancialInformation() throws Exception {
        long inst=institution(0,0);enable(inst);Client client=institutionUser(inst);
        long id=success(client.post("/client-finance/payment-requests",request(inst))).path("request").path("id").asLong();
        long proof=success(upload(client,"/client-finance/payment-requests/"+id+"/attachments")).path("id").asLong();
        success(admin.put("/client-finance-access/"+inst,Map.of("enabled",false)));
        assertStatus(client.get("/client-finance/payment-requests/"+id),403);assertStatus(upload(client,"/client-finance/payment-requests/"+id+"/attachments"),403);assertStatus(client.get("/client-finance/payment-requests/attachments/"+proof+"/download"),403);
        enable(inst);assertStatus(admin.delete("/institutions/"+inst),204);
        assertStatus(client.get("/client-finance"),401);assertStatus(admin.put("/client-finance-access/"+inst,Map.of("enabled",true)),409);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM payment_request_attachments WHERE request_id=?",Long.class,id)).isEqualTo(1);
    }
    private void enable(long id)throws Exception{success(admin.put("/client-finance-access/"+id,Map.of("enabled",true)));}
    private Map<String,Object> request(long inst){Map<String,Object> r=new LinkedHashMap<>();r.put("requestId",UUID.randomUUID().toString());r.put("institutionId",inst);r.put("reference",UUID.randomUUID().toString());r.put("amount",new BigDecimal("50.00"));r.put("paidOn","2026-10-13");r.put("method","TRANSFER");r.put("emailMinutes",5);r.put("remoteMinutes",10);r.put("description","Solicitud de pago visible al cliente");return r;}
    private MvcResult upload(Client client,String path)throws Exception{client.refreshCsrf();return client.perform(multipart("/api"+path).file(new MockMultipartFile("file","evidencia.pdf","application/pdf",PDF)).param("requestId",UUID.randomUUID().toString()).session(client.session).cookie(client.cookies.values().toArray(jakarta.servlet.http.Cookie[]::new)).header("X-XSRF-TOKEN",client.csrf));}
}
