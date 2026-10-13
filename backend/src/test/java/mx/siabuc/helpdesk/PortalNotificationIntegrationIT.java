package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class PortalNotificationIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    @Autowired PortalEventService portalEvents;

    @Test
    void customerCreatesUnassignedCaseAndNotifiesAdministratorsAndPeersButNotSelfOrOtherInstitutions() throws Exception {
        long institution=institution(200,200), otherInstitution=institution(200,200);
        Client customer=institutionUser(institution), peer=institutionUser(institution), outsider=institutionUser(otherInstitution);
        long actorId=success(customer.get("/auth/me")).path("id").asLong();
        long caseId=success(customer.post("/cases",casePayload(institution,"EMAIL"))).path("id").asLong();
        assertThat(caseItems(customer,caseId)).isEmpty();
        assertThat(caseItems(peer,caseId)).hasSize(1);
        assertThat(caseItems(outsider,caseId)).isEmpty();
        assertThat(caseItems(admin,caseId)).hasSize(1);
        assertThat(pending(admin,caseId)).isTrue();
        assertThat(pending(customer,caseId)).isFalse();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM portal_notifications WHERE case_id=? AND recipient_id=?",Long.class,caseId,actorId)).isZero();

        assertStatus(admin.post("/cases/"+caseId+"/claim",Map.of()),200);
        assertThat(pending(admin,caseId)).as("Tomar un caso no cuenta como responder al cliente").isTrue();
        assertThat(caseItems(customer,caseId).stream().map(n->n.path("eventType").asText())).contains("CLAIM_CASE");
    }

    @Test
    void publicRepliesSwitchPendingSideAndKeepWorkingDuringEmailPauseWhileInternalNotesStayPrivate() throws Exception {
        long institution=institution(200,200);
        Client customer=institutionUser(institution);
        long caseId=success(customer.post("/cases",casePayload(institution,"EMAIL"))).path("id").asLong();
        assertStatus(admin.post("/cases/"+caseId+"/claim",Map.of()),200);
        assertStatus(admin.post("/notifications/config",Map.of("paused",true)),200);
        try {
            addEntry(admin,caseId,"Respuesta pública con correo pausado",null,0,false,200);
            assertThat(caseItems(customer,caseId).stream().map(n->n.path("description").asText())).anyMatch(text->text.contains("Respuesta pública con correo pausado"));
            assertThat(pending(customer,caseId)).isTrue();
            assertThat(pending(admin,caseId)).isFalse();
            addEntry(customer,caseId,"Respuesta de la institución",null,0,false,200);
            assertThat(pending(admin,caseId)).isTrue();
            assertThat(pending(customer,caseId)).isFalse();
            addEntry(admin,caseId,"Diagnóstico interno secreto",null,0,true,200);
            assertThat(pending(admin,caseId)).isTrue();
            assertThat(caseItems(customer,caseId).stream().map(n->n.path("description").asText())).noneMatch(text->text.contains("Diagnóstico interno secreto"));
            assertStatus(admin.post("/cases/"+caseId+"/status",Map.of("status","CLOSED","reason","Caso resuelto")),200);
            assertThat(pending(admin,caseId)).isFalse();assertThat(pending(customer,caseId)).isFalse();
            assertStatus(admin.post("/cases/"+caseId+"/status",Map.of("status","OPEN","reason","Continuar revisión")),200);
            assertThat(pending(admin,caseId)).isTrue();
        } finally {assertStatus(admin.post("/notifications/config",Map.of("paused",false)),200);}
    }

    @Test
    void notificationReadStateIsOwnedPersistentAndCannotBeChangedAcrossUsers() throws Exception {
        long institution=institution(200,200);
        Client customer=institutionUser(institution), peer=institutionUser(institution);
        long caseId=supportCase(institution,"EMAIL");
        JsonNode first=caseItems(customer,caseId).get(0);
        long id=first.path("id").asLong();
        assertThat(first.path("readAt").isNull()).isTrue();
        assertStatus(peer.post("/inbox/"+id+"/read",Map.of()),404);
        assertStatus(admin.post("/inbox/"+id+"/read",Map.of()),404);
        assertStatus(customer.post("/inbox/"+id+"/read",Map.of()),204);
        String readAt=caseItems(customer,caseId).get(0).path("readAt").asText();
        assertStatus(customer.post("/inbox/"+id+"/read",Map.of()),204);
        assertThat(caseItems(customer,caseId).get(0).path("readAt").asText()).isEqualTo(readAt);
        assertThat(caseItems(peer,caseId).get(0).path("readAt").isNull()).isTrue();
        assertStatus(peer.post("/inbox/read-all",Map.of()),204);
        assertThat(success(peer.get("/inbox")).path("unreadCount").asLong()).isZero();
        assertStatus(peer.post("/inbox/read-all",Map.of("recipientId",id)),400);
    }

    @Test
    void oldNotificationsAreHiddenAfterInstitutionTransferAndAdministratorDemotion() throws Exception {
        long institution=institution(200,200), destination=institution(200,200);
        String customerName=unique("transfer");
        long customerId=createUser(customerName,institution).path("id").asLong();
        Client customer=login(customerName,PASSWORD);
        long caseId=supportCase(institution,"EMAIL");
        long notice=caseItems(customer,caseId).get(0).path("id").asLong();
        Map<String,Object> moved=userPayload(customerName,destination,true);moved.remove("password");
        assertStatus(admin.put("/users/"+customerId,moved),409);
        // Simulate a legacy/imported account change; the normal API correctly rejects scope changes.
        db.update("UPDATE app_users SET institution_id=? WHERE id=?",destination,customerId);
        assertThat(caseItems(customer,caseId)).isEmpty();
        assertStatus(customer.post("/inbox/"+notice+"/read",Map.of()),404);

        String assignedName=unique("assigned");
        long assignedId=success(admin.post("/users",Map.of("username",assignedName,"displayName","Responsable temporal","password",PASSWORD,
            "role","ADMIN","active",true,"email","asignado@example.test"))).path("id").asLong();
        Client assigned=login(assignedName,PASSWORD);
        assertStatus(admin.put("/cases/"+caseId,Map.of("title","Caso privado","description","Datos públicos","channel","EMAIL","assignedAdminId",assignedId)),200);
        addEntry(admin,caseId,"Información interna restringida",null,0,true,200);
        assertThat(caseItems(assigned,caseId).stream().map(n->n.path("description").asText())).anyMatch(text->text.contains("Información interna restringida"));
        long privateId=db.queryForObject("SELECT id FROM portal_notifications WHERE recipient_id=? AND case_id=? AND internal",Long.class,assignedId,caseId);
        Map<String,Object> demoted=userPayload(assignedName,institution,true);demoted.remove("password");
        assertStatus(admin.put("/users/"+assignedId,demoted),409);
        db.update("UPDATE app_users SET role='USER',institution_id=? WHERE id=?",institution,assignedId);
        assertThat(caseItems(assigned,caseId).stream().map(n->n.path("description").asText())).noneMatch(text->text.contains("Información interna restringida"));
        assertStatus(assigned.post("/inbox/"+privateId+"/read",Map.of()),404);
    }

    @Test
    void lowBalanceAlertsTriggerOncePerChannelAndRearmAfterTopUp() throws Exception {
        long institution=institution(200,200);
        Client customer=institutionUser(institution);
        long caseId=supportCase(institution,"EMAIL");
        assertStatus(admin.put("/balance-alert-settings/"+institution,Map.of("emailThresholdMinutes",20,"remoteThresholdMinutes",0,"enabled",true)),200);
        addEntry(admin,caseId,"Consumo hasta el umbral", "EMAIL",180,false,200);
        assertThat(lowBalanceCount(customer,institution)).isEqualTo(1);
        addEntry(admin,caseId,"Otro minuto", "EMAIL",1,false,200);
        portalEvents.checkBalance(institution);portalEvents.checkBalance(institution);
        assertThat(lowBalanceCount(customer,institution)).isEqualTo(1);
        assertStatus(admin.put("/balance-alert-settings/"+institution,Map.of("emailThresholdMinutes",20,"remoteThresholdMinutes",0,"enabled",true)),200);
        assertThat(lowBalanceCount(customer,institution)).isEqualTo(1);
        success(admin.post("/payments",PaymentIntegrationIT.paymentPayload(institution,50,0)));
        addEntry(admin,caseId,"Nuevo periodo de saldo bajo","EMAIL",50,false,200);
        assertThat(lowBalanceCount(customer,institution)).isEqualTo(2);
    }

    @Test
    void thresholdsAreAdminOnlyValidatedAndNotifyBillingWithoutDisclosingCases() throws Exception {
        long institution=institution(200,200),otherInstitution=institution(200,200);
        Client customer=institutionUser(institution),outsider=institutionUser(otherInstitution);
        String billingName=unique("billing");
        success(admin.post("/users",Map.of("username",billingName,"displayName","Cobranza","password",PASSWORD,"role","BILLING","active",true,"email","cobranza@example.test")));
        Client billing=login(billingName,PASSWORD);
        long caseId=supportCase(institution,"EMAIL");
        Map<String,Object> thresholds=Map.of("emailThresholdMinutes",200,"remoteThresholdMinutes",0,"enabled",true);
        assertStatus(customer.get("/balance-alert-settings"),403);assertStatus(billing.get("/balance-alert-settings"),403);
        assertStatus(customer.put("/balance-alert-settings/"+institution,thresholds),403);
        assertStatus(billing.put("/balance-alert-settings/"+institution,thresholds),403);
        assertStatus(admin.put("/balance-alert-settings/"+institution,Map.of("emailThresholdMinutes",-1,"remoteThresholdMinutes",0,"enabled",true)),400);
        assertStatus(admin.put("/balance-alert-settings/"+institution,Map.of("emailThresholdMinutes",1.5,"remoteThresholdMinutes",0,"enabled",true)),400);
        assertStatus(admin.put("/balance-alert-settings/"+institution,thresholds),200);
        assertThat(lowBalanceCount(customer,institution)).isEqualTo(1);
        assertThat(lowBalanceCount(billing,institution)).isEqualTo(1);
        assertThat(lowBalanceCount(outsider,institution)).isZero();
        assertThat(caseItems(billing,caseId)).isEmpty();
        assertThat(success(billing.get("/inbox")).path("pendingReplies").isEmpty()).isTrue();
    }

    @Test
    void disablingAlertsStopsNewLowBalanceNoticesAndZeroThresholdWarnsAtExhaustion() throws Exception {
        long institution=institution(100,100);
        Client customer=institutionUser(institution);long caseId=supportCase(institution,"EMAIL");
        assertStatus(admin.put("/balance-alert-settings/"+institution,Map.of("emailThresholdMinutes",60,"remoteThresholdMinutes",0,"enabled",false)),200);
        addEntry(admin,caseId,"Consumo con avisos desactivados","EMAIL",90,false,200);
        assertThat(lowBalanceCount(customer,institution)).isZero();
        assertStatus(admin.put("/balance-alert-settings/"+institution,Map.of("emailThresholdMinutes",0,"remoteThresholdMinutes",0,"enabled",true)),200);
        assertThat(lowBalanceCount(customer,institution)).isZero();
        addEntry(admin,caseId,"Agotar saldo","EMAIL",10,false,200);
        assertThat(lowBalanceCount(customer,institution)).isEqualTo(1);
    }

    private List<JsonNode> caseItems(Client client,long caseId) throws Exception {
        return java.util.stream.StreamSupport.stream(success(client.get("/inbox")).path("items").spliterator(),false)
            .filter(n->!n.path("caseId").isNull() && n.path("caseId").asLong()==caseId).toList();
    }
    private boolean pending(Client client,long caseId) throws Exception {
        return java.util.stream.StreamSupport.stream(success(client.get("/inbox")).path("pendingReplies").spliterator(),false)
            .anyMatch(n->n.path("caseId").asLong()==caseId);
    }
    private long lowBalanceCount(Client client,long institution) throws Exception {
        return java.util.stream.StreamSupport.stream(success(client.get("/inbox")).path("items").spliterator(),false)
            .filter(n->n.path("eventType").asText().equals("LOW_BALANCE") && n.path("institutionId").asLong()==institution).count();
    }
}
