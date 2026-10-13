package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;

class PolicyIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    static Map<String,Object> policyPayload(long institution,String amount,long email,long remote) {
        var value=new LinkedHashMap<String,Object>();value.put("institutionId",institution);value.put("folio","POL-"+UUID.randomUUID());
        value.put("description","Póliza de atención SIABUC contratada por la biblioteca");value.put("validFrom","2026-01-01");value.put("validUntil","2026-12-31");
        value.put("agreedAmount",new BigDecimal(amount));value.put("emailMinutes",email);value.put("remoteMinutes",remote);return value;
    }
    private long policy(long institution,String amount,long email,long remote) throws Exception {
        return success(admin.post("/policies",policyPayload(institution,amount,email,remote))).path("policy").path("id").asLong();
    }
    private Map<String,Object> installment(long institution,long policy,String amount,long email,long remote,String method) {
        var input=new LinkedHashMap<>(PaymentIntegrationIT.paymentPayload(institution,email,remote));input.put("policyId",policy);
        input.put("amount",new BigDecimal(amount));input.put("method",method);return input;
    }
    private Client billing() throws Exception {
        String name=unique("billingpolicy");var value=new LinkedHashMap<String,Object>();value.put("username",name);value.put("displayName","Cobranza de pólizas");
        value.put("password",PASSWORD);value.put("role","BILLING");value.put("active",true);value.put("email","polizas@example.test");
        success(admin.post("/users",value));return login(name,PASSWORD);
    }
    private JsonNode get(long policy) throws Exception { return success(admin.get("/policies/"+policy)); }
    private long count(String sql,long id) { return db.queryForObject(sql,Long.class,id); }

    @Test void billingCanManagePoliciesWithoutCreditingHoursAndClientsCannotReadThem() throws Exception {
        long institution=institution(10,20);Client billing=billing(),client=institutionUser(institution);
        var input=policyPayload(institution,"1234.50",600,300);
        JsonNode created=success(billing.post("/policies",input));long id=created.path("policy").path("id").asLong();
        assertThat(created.path("policy").path("currency").asText()).isEqualTo("MXN");
        assertThat(created.path("policy").path("paidAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(created.path("history")).hasSize(1);assertThat(created.path("payments")).isEmpty();assertBalance(institution,0,10,0,20);
        input.put("description","Contrato corregido antes de recibir pagos");input.put("agreedAmount",new BigDecimal("1500.00"));
        JsonNode updated=success(billing.put("/policies/"+id,input));assertThat(updated.path("history")).hasSize(2);
        assertThat(updated.path("history").get(0).path("description").asText()).contains("Antes:","Después:","Contrato corregido");
        assertThat(success(billing.get("/policies?institutionId="+institution))).hasSize(1);
        for(String path:List.of("/policies","/policies/"+id,"/policies?institutionId="+institution))assertStatus(client.get(path),403);
        assertStatus(client.post("/policies",input),403);assertStatus(client.put("/policies/"+id,input),403);
        assertStatus(client.post("/policies/"+id+"/cancel",Map.of("reason","Sin permiso")),403);
        assertStatus(billing.raw("POST","/policies",input,false),403);
        JsonNode cancelled=success(billing.post("/policies/"+id+"/cancel",Map.of("reason","Cliente decide no contratar")));
        assertThat(cancelled.path("policy").path("status").asText()).isEqualTo("CANCELLED");assertThat(cancelled.path("history")).hasSize(3);
        assertBalance(institution,0,10,0,20);
    }

    @Test void installmentsCreditExplicitHoursAndMoneyOnlyPaymentsCompleteTheContract() throws Exception {
        long institution=institution(10,20),policy=policy(institution,"1000.00",100,60);Client billing=billing();
        JsonNode first=success(billing.post("/payments",installment(institution,policy,"200.00",20,10,"DEPOSIT")));
        assertThat(first.path("policyId").asLong()).isEqualTo(policy);assertThat(first.path("policyFolio").asText()).isNotBlank();
        assertBalance(institution,0,30,0,30);
        success(billing.post("/payments",installment(institution,policy,"800.00",0,0,"CREDIT_CARD")));
        assertBalance(institution,0,30,0,30);
        success(billing.post("/payments",installment(institution,policy,"0.00",80,50,"OTHER")));
        assertBalance(institution,0,110,0,80);
        JsonNode detail=get(policy),p=detail.path("policy");
        assertThat(p.path("paidAmount").decimalValue()).isEqualByComparingTo("1000.00");assertThat(p.path("remainingAmount").decimalValue()).isZero();
        assertThat(p.path("creditedEmailMinutes").asLong()).isEqualTo(100);assertThat(p.path("creditedRemoteMinutes").asLong()).isEqualTo(60);
        assertThat(p.path("remainingEmailMinutes").asLong()).isZero();assertThat(p.path("remainingRemoteMinutes").asLong()).isZero();
        assertThat(detail.path("payments")).hasSize(3);assertThat(detail.path("history")).hasSize(4);
        assertThat(detail.path("history").get(0).path("paymentId").isNull()).isFalse();
        assertThat(detail.path("history").get(0).path("description").asText()).contains("importe: $0.00 MXN","correo: 80 min","remoto: 50 min");
        String body=db.queryForObject("SELECT body FROM notification_outbox WHERE payment_id=? ORDER BY id LIMIT 1",String.class,first.path("id").asLong());
        assertThat(body).contains("Póliza: "+first.path("policyFolio").asText(),"Método: Depósito","Minutos de correo: 20");
    }

    @Test void limitsAndUnpaidZeroAmountAreRejectedWithoutPartialPaymentsCreditsOrHistory() throws Exception {
        long institution=institution(0,0),policy=policy(institution,"100.00",50,30);
        assertStatus(admin.post("/payments",installment(institution,policy,"0",1,0,"TRANSFER")),409);
        for(var input:List.of(installment(institution,policy,"100.01",1,0,"TRANSFER"),installment(institution,policy,"50",51,0,"TRANSFER"),installment(institution,policy,"50",0,31,"TRANSFER")))
            assertStatus(admin.post("/payments",input),409);
        long other=institution(0,0);assertStatus(admin.post("/payments",installment(other,policy,"20",10,0,"CASH")),409);
        assertBalance(institution,0,0,0,0);assertBalance(other,0,0,0,0);assertThat(get(policy).path("history")).hasSize(1);
        assertThat(get(policy).path("payments")).isEmpty();
        success(admin.post("/payments",installment(institution,policy,"100",20,0,"TRANSFER")));
        assertStatus(admin.post("/payments",installment(institution,policy,"0.01",0,0,"CASH")),409);
        assertStatus(admin.post("/payments",installment(institution,policy,"0",31,0,"CASH")),409);
        assertBalance(institution,0,20,0,0);assertThat(get(policy).path("payments")).hasSize(1);
    }

    @Test void paymentFailureLateInTransactionRollsBackCreditAuditHistoryAndNotification() throws Exception {
        long institution=institution(10,20),policy=policy(institution,"100",30,40);
        var input=installment(institution,policy,"100",30,40,"DEBIT_CARD");
        long outbox=db.queryForObject("SELECT COUNT(*) FROM notification_outbox",Long.class);
        faultNotifications.forcePaymentFailureFor(institution);
        try {
            MvcResult failed=admin.post("/payments",input);assertStatus(failed,409);
            assertThat(json(failed).path("message").asText()).isEqualTo("Fallo controlado después de guardar el aviso de pago.");
            assertBalance(institution,0,10,0,20);
            assertThat(count("SELECT COUNT(*) FROM payments WHERE institution_id=?",institution)).isZero();
            assertThat(get(policy).path("history")).hasSize(1);assertThat(get(policy).path("policy").path("paidAmount").decimalValue()).isZero();
            assertThat(db.queryForObject("SELECT COUNT(*) FROM notification_outbox",Long.class)).isEqualTo(outbox);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='CREATE_PAYMENT' AND details LIKE ?",Long.class,"Institución="+institution+"; %")).isZero();
        } finally {faultNotifications.resetPaymentFailure();}
        success(admin.post("/payments",input));assertBalance(institution,0,40,0,60);assertThat(get(policy).path("history")).hasSize(2);
    }

    @Test void paymentIdempotenceIncludesPolicyAndNeverDuplicatesHistoryOrCredits() throws Exception {
        long institution=institution(0,0),policy=policy(institution,"100",50,30),other=policy(institution,"100",50,30);
        var input=installment(institution,policy,"20.00",10,10,"CHECK");JsonNode first=success(admin.post("/payments",input));
        input.put("amount",new BigDecimal("20.0"));JsonNode retried=success(admin.post("/payments",input));assertThat(retried.path("id")).isEqualTo(first.path("id"));
        assertBalance(institution,0,10,0,10);assertThat(get(policy).path("history")).hasSize(2);
        input.put("policyId",other);assertStatus(admin.post("/payments",input),409);
        input.put("policyId",null);assertStatus(admin.post("/payments",input),409);
        assertThat(get(other).path("payments")).isEmpty();assertThat(get(policy).path("payments")).hasSize(1);
        var duplicate=installment(institution,policy,"10",1,1,"CHECK");duplicate.put("reference",first.path("reference").asText().toUpperCase());
        assertStatus(admin.post("/payments",duplicate),409);assertBalance(institution,0,10,0,10);
    }

    @Test void administratorVoidsLinkedPaymentRestoresContractAndEditingRemainsFrozen() throws Exception {
        long institution=institution(0,0),policy=policy(institution,"100",30,40);Client billing=billing();
        var input=installment(institution,policy,"100",30,40,"CREDIT_CARD");JsonNode payment=success(billing.post("/payments",input));long paymentId=payment.path("id").asLong();
        assertStatus(billing.post("/payments/"+paymentId+"/void",Map.of("reason","No autorizado")),403);
        assertStatus(billing.post("/policies/"+policy+"/cancel",Map.of("reason","Aún tiene pagos")),409);
        var edit=policyPayload(institution,"200",60,80);assertStatus(billing.put("/policies/"+policy,edit),409);
        success(admin.post("/payments/"+paymentId+"/void",Map.of("reason","Corregir el movimiento")));assertBalance(institution,0,0,0,0);
        JsonNode detail=get(policy),p=detail.path("policy");assertThat(p.path("remainingAmount").decimalValue()).isEqualByComparingTo("100");
        assertThat(p.path("remainingEmailMinutes").asLong()).isEqualTo(30);assertThat(p.path("remainingRemoteMinutes").asLong()).isEqualTo(40);
        assertThat(p.path("paymentCount").asLong()).isEqualTo(1);assertThat(p.path("activePaymentCount").asLong()).isZero();
        assertThat(detail.path("history")).hasSize(3);assertThat(detail.path("history").get(0).path("action").asText()).isEqualTo("VOID_PAYMENT");
        assertStatus(billing.put("/policies/"+policy,edit),409);
        assertThat(success(billing.post("/payments",input)).path("voided").asBoolean()).isTrue();assertBalance(institution,0,0,0,0);
        success(billing.post("/policies/"+policy+"/cancel",Map.of("reason","Contrato cancelado con evidencia conservada")));
        assertStatus(billing.post("/payments",installment(institution,policy,"100",30,40,"CASH")),409);
        assertThat(get(policy).path("history")).hasSize(4);
    }

    @Test void consumedLinkedHoursPreventVoidAndLeaveAllLedgersUntouched() throws Exception {
        long institution=institution(0,0),policy=policy(institution,"100",10,20);
        JsonNode payment=success(admin.post("/payments",installment(institution,policy,"100",10,20,"TRANSFER")));
        long caseId=supportCase(institution,"REMOTE");addEntry(admin,caseId,"Uso del soporte contratado","REMOTE",5,false,200);
        assertStatus(admin.post("/payments/"+payment.path("id").asLong()+"/void",Map.of("reason","Saldo ya utilizado")),409);
        assertBalance(institution,0,10,5,15);JsonNode detail=get(policy);assertThat(detail.path("history")).hasSize(2);
        assertThat(detail.path("payments").get(0).path("voided").asBoolean()).isFalse();assertThat(detail.path("policy").path("paidAmount").decimalValue()).isEqualByComparingTo("100");
    }

    @Test void moneyThatBacksZeroAmountCreditsCannotBeVoidedUntilThoseCreditsAreSafelyReversed() throws Exception {
        long institution=institution(0,0),policy=policy(institution,"100",20,0);
        JsonNode money=success(admin.post("/payments",installment(institution,policy,"100",0,0,"DEPOSIT")));
        JsonNode credit=success(admin.post("/payments",installment(institution,policy,"0",20,0,"OTHER")));
        String moneyPath="/payments/"+money.path("id").asLong()+"/void",creditPath="/payments/"+credit.path("id").asLong()+"/void";
        MvcResult denied=admin.post(moneyPath,Map.of("reason","Intento de retirar el respaldo monetario"));assertStatus(denied,409);
        assertThat(json(denied).path("message").asText()).contains("Anule primero esas acreditaciones");
        assertBalance(institution,0,20,0,0);assertThat(get(policy).path("history")).hasSize(3);
        long caseId=supportCase(institution,"EMAIL");JsonNode consumed=addEntry(admin,caseId,"Uso de las horas previamente pagadas","EMAIL",10,false,200);
        long entryId=consumed.path("entries").get(consumed.path("entries").size()-1).path("id").asLong();
        assertStatus(admin.post(creditPath,Map.of("reason","Las horas ya se utilizaron")),409);
        assertStatus(admin.post(moneyPath,Map.of("reason","No se puede eludir el saldo utilizado")),409);
        assertBalance(institution,10,10,0,0);assertThat(get(policy).path("policy").path("paidAmount").decimalValue()).isEqualByComparingTo("100");
        success(admin.post("/cases/"+caseId+"/entries/"+entryId+"/void",Map.of("reason","Corregir atención de prueba")));
        success(admin.post(creditPath,Map.of("reason","Primero retirar la acreditación respaldada")));
        success(admin.post(moneyPath,Map.of("reason","Después retirar su respaldo monetario")));
        assertBalance(institution,0,0,0,0);assertThat(get(policy).path("policy").path("paidAmount").decimalValue()).isZero();
        assertThat(get(policy).path("history")).hasSize(5);
    }

    @Test void concurrentInstallmentsCannotExceedPolicyMoneyOrHours() throws Exception {
        long institution=institution(0,0),policy=policy(institution,"100",50,30);Client a=billing(),b=billing();
        var first=installment(institution,policy,"60",30,20,"CASH");var second=installment(institution,policy,"60",30,20,"DEPOSIT");
        a.refreshCsrf();b.refreshCsrf();var ready=new CountDownLatch(2);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var one=pool.submit(()->{ready.countDown();assertThat(start.await(20,TimeUnit.SECONDS)).isTrue();return a.raw("POST","/payments",first,true).getResponse().getStatus();});
            var two=pool.submit(()->{ready.countDown();assertThat(start.await(20,TimeUnit.SECONDS)).isTrue();return b.raw("POST","/payments",second,true).getResponse().getStatus();});
            assertThat(ready.await(20,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(one.get(40,TimeUnit.SECONDS),two.get(40,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        } finally {start.countDown();pool.shutdownNow();}
        assertBalance(institution,0,30,0,20);JsonNode detail=get(policy);assertThat(detail.path("payments")).hasSize(1);assertThat(detail.path("history")).hasSize(2);
        assertThat(detail.path("policy").path("paidAmount").decimalValue()).isEqualByComparingTo("60");
    }

    @Test void concurrentReplayOfSamePaymentCreditsOnce() throws Exception {
        long institution=institution(0,0),policy=policy(institution,"100",50,30);Client a=billing(),b=billing();
        var input=installment(institution,policy,"100",50,30,"TRANSFER");a.refreshCsrf();b.refreshCsrf();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var one=pool.submit(()->{assertThat(start.await(20,TimeUnit.SECONDS)).isTrue();return success(a.raw("POST","/payments",input,true));});
            var two=pool.submit(()->{assertThat(start.await(20,TimeUnit.SECONDS)).isTrue();return success(b.raw("POST","/payments",input,true));});
            start.countDown();assertThat(one.get(40,TimeUnit.SECONDS).path("id")).isEqualTo(two.get(40,TimeUnit.SECONDS).path("id"));
        } finally {start.countDown();pool.shutdownNow();}
        assertBalance(institution,0,50,0,30);assertThat(get(policy).path("history")).hasSize(2);assertThat(get(policy).path("payments")).hasSize(1);
    }

    @Test void dateValidityIsInformationalButCancelledPoliciesAndInvalidContractsAreRejected() throws Exception {
        long institution=institution(0,0);var input=policyPayload(institution,"100",10,10);input.put("validFrom","2000-01-01");input.put("validUntil","2000-12-31");
        JsonNode created=success(admin.post("/policies",input));long policy=created.path("policy").path("id").asLong();
        assertThat(created.path("policy").path("validity").asText()).isEqualTo("EXPIRED");
        success(admin.post("/payments",installment(institution,policy,"100",10,10,"CASH")));assertBalance(institution,0,10,0,10);
        var duplicate=new LinkedHashMap<>(input);duplicate.put("folio","  "+input.get("folio").toString().toLowerCase()+"  ");assertStatus(admin.post("/policies",duplicate),409);
        for(var invalid:Map.<String,Object>of("agreedAmount",new BigDecimal("1.001"),"description","","emailMinutes",-1,"validUntil","1999-12-31","remoteMinutes",1.5).entrySet()) {
            var bad=new LinkedHashMap<>(input);bad.put("folio","BAD-"+UUID.randomUUID());bad.put(invalid.getKey(),invalid.getValue());assertStatus(admin.post("/policies",bad),400);
        }
        var future=policyPayload(institution,"50",10,0);future.put("validFrom","2090-01-01");future.put("validUntil","2090-12-31");
        assertThat(success(admin.post("/policies",future)).path("policy").path("validity").asText()).isEqualTo("UPCOMING");
    }

    @Test void newPaymentsUseMxnWhileLegacyForeignCurrenciesAndCardReplayRemainUnchanged() throws Exception {
        long institution=institution(0,0);var legacy=PaymentIntegrationIT.paymentPayload(institution,10,20);legacy.put("currency","USD");legacy.put("method","CARD");
        long author=success(admin.get("/auth/me")).path("id").asLong();
        Long id=db.queryForObject("INSERT INTO payments(request_id,institution_id,reference,amount,currency,paid_on,method,email_minutes,remote_minutes,notes,created_by) VALUES(?::uuid,?,?,?,?,?::date,?,?,?,?,?) RETURNING id",Long.class,
            legacy.get("requestId"),institution,legacy.get("reference"),legacy.get("amount"),"USD",legacy.get("paidOn"),"CARD",10,20,legacy.get("notes"),author);
        db.update("UPDATE institutions SET email_allocated_minutes=10,remote_allocated_minutes=20 WHERE id=?",institution);
        JsonNode replay=success(admin.post("/payments",legacy));assertThat(replay.path("id").asLong()).isEqualTo(id);assertThat(replay.path("currency").asText()).isEqualTo("USD");
        assertThat(replay.path("method").asText()).isEqualTo("CARD");assertThat(replay.path("policyId").isNull()).isTrue();assertBalance(institution,0,10,0,20);
        legacy.put("requestId",UUID.randomUUID().toString());legacy.put("reference","Nuevo-"+UUID.randomUUID());assertStatus(admin.post("/payments",legacy),400);
        var unlinked=PaymentIntegrationIT.paymentPayload(institution,1,1);
        for(String method:List.of("TRANSFER","DEPOSIT","CASH","CREDIT_CARD","DEBIT_CARD","CHECK","OTHER","CARD")) {
            unlinked.put("requestId",UUID.randomUUID().toString());unlinked.put("reference","Método-"+UUID.randomUUID());unlinked.put("method",method);
            assertThat(success(admin.post("/payments",unlinked)).path("method").asText()).isEqualTo(method);
        }
        assertBalance(institution,0,18,0,28);
    }
}
