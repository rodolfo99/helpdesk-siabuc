package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class CaseProfileIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    @Autowired CaseProfileService profiles;

    private Map<String,Object> profile(String category,String priority) {
        return Map.of("category",category,"priority",priority,"siabucVersion","SIABUC 9","operatingSystem","Ubuntu 26.04",
            "errorMessage","Conexión interrumpida","reproductionSteps","1. Abrir catálogo\n2. Consultar el registro");
    }
    @Test void profileIsAtomicAtCreationAndInstitutionScoped() throws Exception {
        long institution=institution(60,60),other=institution(60,60);
        Client client=institutionUser(institution),outsider=institutionUser(other);
        var input=new LinkedHashMap<>(casePayload(institution,"EMAIL"));input.put("profile",profile("CATALOGING","HIGH"));
        long id=success(client.post("/cases",input)).path("id").asLong();
        JsonNode detail=success(client.get("/cases/"+id+"/profile"));
        assertThat(detail.path("category").asText()).isEqualTo("CATALOGING");
        assertThat(detail.path("priority").asText()).isEqualTo("HIGH");
        assertThat(detail.path("errorMessage").asText()).isEqualTo("Conexión interrumpida");
        assertThat(detail.path("sla").path("firstResponseMinutes").asInt()).isEqualTo(120);
        assertThat(detail.path("sla").path("legacy").asBoolean()).isFalse();
        assertStatus(outsider.get("/cases/"+id+"/profile"),404);
        assertStatus(outsider.put("/cases/"+id+"/profile",profile("LOANS","URGENT")),404);
        assertThat(success(outsider.get("/case-profiles")).toString()).doesNotContain("\"caseId\":"+id+",");
        long own=supportCase(other,"REMOTE");
        assertThat(success(outsider.get("/case-profiles")).size()).isEqualTo(1);
        assertThat(success(outsider.get("/case-profiles")).get(0).path("caseId").asLong()).isEqualTo(own);
        success(client.put("/cases/"+id+"/profile",profile("LOANS","URGENT")));
        assertThat(success(admin.get("/cases/"+id+"/profile")).path("priority").asText()).isEqualTo("URGENT");
        assertBalance(institution,0,60,0,60);
    }
    @Test void defaultLegacyCompatibleCreationAndInvalidAtomicInputRollsBack() throws Exception {
        long institution=institution(60,60),id=supportCase(institution,"EMAIL");
        JsonNode detail=success(admin.get("/cases/"+id+"/profile"));
        assertThat(detail.path("category").asText()).isEqualTo("OTHER");
        assertThat(detail.path("priority").asText()).isEqualTo("NORMAL");
        assertThat(detail.path("siabucVersion").asText()).isEmpty();
        long count=db.queryForObject("SELECT COUNT(*) FROM support_cases WHERE institution_id=?",Long.class,institution);
        var badProfile=new LinkedHashMap<>(profile("INSTALLATION","NORMAL"));badProfile.put("errorMessage","x".repeat(10001));
        var input=new LinkedHashMap<>(casePayload(institution,"EMAIL"));input.put("profile",badProfile);
        assertStatus(admin.post("/cases",input),400);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM support_cases WHERE institution_id=?",Long.class,institution)).isEqualTo(count);
        assertStatus(admin.put("/cases/"+id+"/profile",badProfile),400);
        assertStatus(admin.put("/cases/"+id+"/profile",profile("INVALID","NORMAL")),400);
        success(admin.post("/cases/"+id+"/status",Map.of("status","CLOSED","reason","Terminado")));
        assertStatus(admin.put("/cases/"+id+"/profile",profile("LOANS","LOW")),409);
    }
    @Test void settingsAreAdminOnlyValidatedAndFrozenForExistingCases() throws Exception {
        JsonNode original=success(admin.get("/support-settings"));
        try {
            long institution=institution(60,60),first=supportCase(institution,"EMAIL");
            Client client=institutionUser(institution);
            String name=unique("cobranza");var billing=new LinkedHashMap<String,Object>();billing.put("username",name);billing.put("displayName","Cobranza");billing.put("password",PASSWORD);billing.put("role","BILLING");billing.put("active",true);
            success(admin.post("/users",billing));Client bill=login(name,PASSWORD);
            assertStatus(client.get("/support-settings"),403);assertStatus(client.put("/support-settings",original),403);
            assertStatus(bill.get("/support-settings"),403);assertStatus(bill.get("/case-profiles"),403);assertStatus(bill.get("/cases/"+first+"/profile"),403);
            var changed=original.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)changed.path("goals").path("NORMAL")).put("firstResponseMinutes",90);
            ((com.fasterxml.jackson.databind.node.ObjectNode)changed).put("timezone","UTC");
            success(admin.put("/support-settings",changed));
            long second=supportCase(institution,"EMAIL");
            assertThat(success(admin.get("/cases/"+first+"/profile")).path("sla").path("firstResponseMinutes").asInt()).isEqualTo(240);
            assertThat(success(admin.get("/cases/"+second+"/profile")).path("sla").path("firstResponseMinutes").asInt()).isEqualTo(90);
            assertThat(success(admin.get("/cases/"+first+"/profile")).path("sla").path("timezone").asText()).isEqualTo("America/Mexico_City");
            var invalid=original.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)invalid).put("timezone","Nowhere/Invalid");assertStatus(admin.put("/support-settings",invalid),400);
            invalid=original.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)invalid).put("endTime","08:00");assertStatus(admin.put("/support-settings",invalid),400);
            invalid=original.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)invalid.path("goals").path("NORMAL")).put("resolutionMinutes",1);assertStatus(admin.put("/support-settings",invalid),400);
            invalid=original.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)invalid).putArray("workDays");assertStatus(admin.put("/support-settings",invalid),400);
        } finally {success(admin.put("/support-settings",original));}
    }
    @Test void onlyPublicAdminReplyCompletesFirstResponseAndReopenStartsResolutionWindow() throws Exception {
        long institution=institution(60,60);Client client=institutionUser(institution);
        long id=success(client.post("/cases",casePayload(institution,"EMAIL"))).path("id").asLong();
        addEntry(client,id,"Información del cliente",null,0,false,200);
        success(admin.post("/cases/"+id+"/claim",Map.of()));
        addEntry(admin,id,"Nota privada del equipo",null,0,true,200);
        assertThat(success(admin.get("/cases/"+id+"/profile")).path("sla").path("firstRespondedAt").isNull()).isTrue();
        addEntry(admin,id,"Respuesta pública del administrativo",null,0,false,200);
        JsonNode first=success(admin.get("/cases/"+id+"/profile")).path("sla");
        assertThat(first.path("firstRespondedAt").asText()).isNotBlank();
        assertThat(first.path("firstResponseState").asText()).isEqualTo("MET");
        addEntry(admin,id,"Otra respuesta pública",null,0,false,200);
        assertThat(success(admin.get("/cases/"+id+"/profile")).path("sla").path("firstRespondedAt")).isEqualTo(first.path("firstRespondedAt"));
        success(admin.post("/cases/"+id+"/status",Map.of("status","CLOSED","reason","Problema solucionado")));
        JsonNode closed=success(admin.get("/cases/"+id+"/profile")).path("sla");
        assertThat(closed.path("resolvedAt").isNull()).isFalse();assertThat(closed.path("resolutionState").asText()).isEqualTo("MET");
        success(admin.post("/cases/"+id+"/status",Map.of("status","OPEN","reason","Validación adicional")));
        JsonNode reopened=success(admin.get("/cases/"+id+"/profile")).path("sla");
        assertThat(reopened.path("resolvedAt").isNull()).isTrue();assertThat(reopened.path("resolutionState").asText()).isEqualTo("PENDING");
        assertThat(reopened.path("firstRespondedAt")).isEqualTo(first.path("firstRespondedAt"));
        assertBalance(institution,0,60,0,60);
    }
    @Test void expiredResponseAndResolutionAreReportedWithoutSpendingBalance() throws Exception {
        long institution=institution(15,20),id=supportCase(institution,"EMAIL");
        db.update("UPDATE case_profiles SET opened_at='2020-01-02T15:00:00Z',resolution_started_at='2020-01-02T15:00:00Z' WHERE case_id=?",id);
        JsonNode overdue=success(admin.get("/cases/"+id+"/profile")).path("sla");
        assertThat(overdue.path("firstResponseState").asText()).isEqualTo("OVERDUE");assertThat(overdue.path("resolutionState").asText()).isEqualTo("OVERDUE");
        addEntry(admin,id,"Respuesta tardía",null,0,false,200);
        success(admin.post("/cases/"+id+"/status",Map.of("status","CLOSED","reason","Resuelto")));
        JsonNode closed=success(admin.get("/cases/"+id+"/profile")).path("sla");
        assertThat(closed.path("firstResponseState").asText()).isEqualTo("BREACHED");assertThat(closed.path("resolutionState").asText()).isEqualTo("BREACHED");
        assertBalance(institution,0,15,0,20);
    }
}
