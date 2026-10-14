package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Real session/CSRF, SQL persistence and cross-role checks for the technical account. */
class TechnicianIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    private Map<String,Object> staffPayload(String name,String role) {
        return new LinkedHashMap<>(Map.of("username",name,"displayName","Personal " + role,"password",PASSWORD,
            "role",role,"active",true,"email",name+"@example.test"));
    }
    private Client technician() throws Exception {
        String name=unique("tecnico");success(admin.post("/users",staffPayload(name,"TECH")));return login(name,PASSWORD);
    }
    private long uid(Client client) throws Exception {return success(client.get("/auth/me")).path("id").asLong();}
    private JsonNode lastEntry(JsonNode detail) {return detail.path("entries").get(detail.path("entries").size()-1);}
    private Map<String,Object> edit(JsonNode c) {
        return new LinkedHashMap<>(Map.of("title",c.path("title").asText(),"description","Descripción actualizada por soporte",
            "channel",c.path("channel").asText()));
    }

    @Test void technicianHasNoInstitutionAndCannotReachAccountFinanceOrConfigurationRoutes() throws Exception {
        Client tech=technician();JsonNode me=success(tech.get("/auth/me"));
        assertThat(me.path("role").asText()).isEqualTo("TECH");assertThat(me.path("institutionId").isNull()).isTrue();
        long institution=institution(30,40);
        var invalid=staffPayload(unique("scopedtech"),"TECH");invalid.put("institutionId",institution);
        assertStatus(admin.post("/users",invalid),400);
        for(String path:List.of("/users","/audit","/payments","/policies","/quotes","/renewals","/payment-requests",
                "/client-finance","/client-finance-access","/notifications","/notifications/config","/backups/status",
                "/support-settings","/balance-alert-settings","/reports/support","/reports/billing","/satisfaction/reports",
                "/payments/1/receipt.pdf","/payments/1/attachments","/payment-attachments/1/download",
                "/support-packages","/installments","/reconciliation","/statements")) assertStatus(tech.get(path),403);
        for(String path:List.of("/users","/institutions","/payments","/quotes","/notifications/config","/backups/run"))
            assertStatus(tech.post(path,Map.of()),403);
        assertStatus(tech.delete("/institutions/"+institution),403);
        assertStatus(tech.put("/institutions/"+institution,institutionPayload("No permitido",0,0)),403);
        assertStatus(tech.put("/users/"+uid(tech),staffPayload(me.path("username").asText(),"ADMIN")),403);
        assertThat(findInstitution(institution).path("emailRemainingMinutes").asInt()).isEqualTo(30);
        JsonNode safe=success(tech.get("/institutions"));
        assertThat(ids(safe)).contains(institution);assertThat(safe).allSatisfy(i->assertThat(i.path("notes").asText()).isEmpty());
        JsonNode options=success(tech.get("/support-staff"));assertThat(ids(options)).contains(uid(tech),uid(admin));
        assertThat(options).allSatisfy(i->{assertThat(i.size()).isEqualTo(3);assertThat(i.has("username")).isFalse();assertThat(i.has("email")).isFalse();});
        assertStatus(institutionUser(institution).get("/support-staff"),403);
    }

    @Test void technicianClaimsAndAttendsAcrossInstitutionsWithoutReassignmentOrConsumptionVoids() throws Exception {
        Client tech=technician(),otherTech=technician();long institution=institution(10,20),otherInstitution=institution(10,20);
        Client customer=institutionUser(institution);
        JsonNode opened=success(customer.post("/cases",casePayload(institution,"EMAIL")));
        assertThat(opened.path("assignedAdminId").isNull()).isTrue(); // nullable legacy client assignment must not unbox
        long id=opened.path("id").asLong();
        long otherCase=supportCase(otherInstitution,"REMOTE");
        assertThat(ids(success(tech.get("/cases")))).contains(id,otherCase);
        assertStatus(tech.raw("POST","/cases/"+id+"/claim",Map.of(),false),403);
        JsonNode claimed=success(tech.post("/cases/"+id+"/claim",Map.of()));
        assertThat(claimed.path("assignedAdminId").asLong()).isEqualTo(uid(tech));
        assertThat(success(tech.post("/cases/"+id+"/claim",Map.of()))).isEqualTo(claimed);
        assertStatus(otherTech.post("/cases/"+id+"/claim",Map.of()),409);
        var modified=edit(claimed);modified.put("assignedAdminId",uid(admin));
        assertStatus(tech.put("/cases/"+id,modified),403);
        modified.put("assignedAdminId",null);assertStatus(tech.put("/cases/"+id,modified),403);
        modified.remove("assignedAdminId");assertThat(success(tech.put("/cases/"+id,modified)).path("assignedAdminId").asLong()).isEqualTo(uid(tech));
        addEntry(tech,id,"Diagnóstico privado TECH",null,0,true,200);
        assertThat(success(otherTech.get("/cases/"+id)).toString()).contains("Diagnóstico privado TECH");
        assertThat(success(customer.get("/cases/"+id)).toString()).doesNotContain("Diagnóstico privado TECH");
        JsonNode attended=addEntry(tech,id,"Solución aplicada por técnico","EMAIL",4,false,200);
        long entryId=lastEntry(attended).path("id").asLong();
        assertThat(success(tech.get("/cases/"+id+"/profile")).path("sla").path("firstRespondedAt").isNull()).isFalse();
        addEntry(tech,id,"No hay suficientes minutos","EMAIL",7,false,409);
        assertStatus(tech.post("/cases/"+id+"/entries/"+entryId+"/void",Map.of("reason","No autorizado")),403);
        assertBalance(institution,4,6,0,20);
        success(tech.post("/cases/"+id+"/status",Map.of("status","CLOSED","reason","Solución comprobada")));
        addEntry(tech,id,"No puede cobrar cerrado","EMAIL",1,false,409);
        success(tech.post("/cases/"+id+"/status",Map.of("status","OPEN","reason","Seguimiento adicional")));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='CLAIM_CASE' AND entity_id=?",Long.class,id)).isEqualTo(1);
        success(tech.post("/cases/"+otherCase+"/status",Map.of("status","CLOSED","reason","Solución coordinada aunque pertenece a otro responsable")));
        assertThat(success(admin.get("/cases/"+otherCase)).path("assignedAdminId").asLong()).isEqualTo(uid(admin));
        assertStatus(customer.get("/cases/"+otherCase),404);
    }

    @Test void technicalCaseCreationUsesSelfAndAdministratorCanAssignTechnicalDefault() throws Exception {
        Client tech=technician();long institution=institution(10,10),techId=uid(tech);
        var institutionInput=institutionPayload("Institución asignada " + unique("T"),20,20);institutionInput.put("supportAdminId",techId);
        long assignedInstitution=success(admin.post("/institutions",institutionInput)).path("id").asLong();
        JsonNode clientCase=success(institutionUser(assignedInstitution).post("/cases",casePayload(assignedInstitution,"EMAIL")));
        assertThat(clientCase.path("assignedAdminId").asLong()).isEqualTo(techId);
        var input=new LinkedHashMap<>(casePayload(institution,"EMAIL"));input.put("assignedAdminId",uid(admin));
        assertStatus(tech.post("/cases",input),403);
        input.remove("assignedAdminId");JsonNode own=success(tech.post("/cases",input));
        assertThat(own.path("assignedAdminId").asLong()).isEqualTo(techId);
        var edit=edit(own);edit.put("assignedAdminId",uid(admin));
        assertThat(success(admin.put("/cases/"+own.path("id").asLong(),edit)).path("assignedAdminId").asLong()).isEqualTo(uid(admin));
    }

    @Test void technicianTimerCommitsOnceAndRemoteAgendaRemainsOwnedByItsResponsible() throws Exception {
        Client tech=technician(),other=technician();long institution=institution(30,30),id=supportCase(institution,"REMOTE");
        Client customer=institutionUser(institution);
        long timer=success(tech.post("/cases/"+id+"/timer/start",Map.of())).path("id").asLong();
        assertStatus(other.post("/timers/"+timer+"/finish",Map.of()),404);
        success(tech.post("/timers/"+timer+"/pause",Map.of()));success(tech.post("/timers/"+timer+"/resume",Map.of()));
        success(tech.post("/timers/"+timer+"/finish",Map.of()));
        var commit=Map.of("minutes",2,"channel","REMOTE","description","Atención remota con cronómetro","internal",false,"adjustmentReason","Confirmación acordada de dos minutos");
        JsonNode confirmed=success(tech.post("/timers/"+timer+"/commit",commit));
        assertThat(success(tech.post("/timers/"+timer+"/commit",commit))).isEqualTo(confirmed);
        assertBalance(institution,0,30,2,28);
        Instant start=Instant.now().plusSeconds(7200),end=start.plusSeconds(1800);
        var proposal=new LinkedHashMap<String,Object>(Map.of("startsAt",start.toString(),"endsAt",end.toString(),"note","Revisión con cliente"));
        proposal.put("adminId",uid(admin));assertStatus(tech.post("/cases/"+id+"/remote-sessions",proposal),403);
        proposal.remove("adminId");JsonNode session=success(tech.post("/cases/"+id+"/remote-sessions",proposal));
        long sessionId=session.path("id").asLong();assertThat(session.path("adminId").asLong()).isEqualTo(uid(tech));
        assertStatus(other.post("/remote-sessions/"+sessionId+"/cancel",Map.of("response","Ajena")),403);
        assertThat(success(customer.post("/remote-sessions/"+sessionId+"/confirm",Map.of())).path("status").asText()).isEqualTo("CONFIRMED");
        proposal.put("startsAt",start.plusSeconds(3600).toString());proposal.put("endsAt",end.plusSeconds(3600).toString());
        JsonNode replacement=success(tech.post("/remote-sessions/"+sessionId+"/reschedule",proposal));
        assertThat(replacement.path("status").asText()).isEqualTo("PROPOSED");
        success(tech.post("/remote-sessions/"+replacement.path("id").asLong()+"/cancel",Map.of("response","Cliente solicita seguimiento por correo")));
        assertBalance(institution,0,30,2,28);
    }

    @Test void technicalAttachmentsPreserveInternalAndInstitutionBoundaries() throws Exception {
        Client tech=technician();long institution=institution(20,20),id=supportCase(institution,"EMAIL");Client customer=institutionUser(institution);
        long entry=lastEntry(addEntry(tech,id,"Documento interno del diagnóstico",null,0,true,200)).path("id").asLong();
        byte[] bytes="Diagnóstico técnico reservado".getBytes(StandardCharsets.UTF_8);tech.refreshCsrf();
        JsonNode attachment=success(tech.perform(multipart("/api/cases/"+id+"/attachments")
            .file(new MockMultipartFile("file","diagnostico.txt","text/plain",bytes)).param("entryId",Long.toString(entry))
            .param("requestId",UUID.randomUUID().toString()).session(tech.session)
            .cookie(tech.cookies.values().toArray(jakarta.servlet.http.Cookie[]::new)).header("X-XSRF-TOKEN",tech.csrf)));
        long file=attachment.path("id").asLong();assertThat(tech.get("/attachments/"+file+"/download").getResponse().getContentAsByteArray()).isEqualTo(bytes);
        assertStatus(customer.get("/attachments/"+file+"/download"),404);
        assertStatus(customer.get("/cases/"+id+"/attachments?entryId="+entry),404);
        assertStatus(institutionUser(institution(20,20)).get("/cases/"+id),404);
        assertBalance(institution,0,20,0,20);
    }

    @Test void technicianUsesPublishedKnowledgeWithoutEditorialAccess() throws Exception {
        Client tech=technician();String marker=unique("TecConocimiento");
        Map<String,Object> article=Map.of("title",marker,"category","INSTALLATION","siabucVersion","9","summary","Resumen","body","Diagnóstico y solución","changeNote","Alta editorial");
        JsonNode draft=success(admin.post("/knowledge",article));long articleId=draft.path("id").asLong();
        assertStatus(tech.get("/knowledge/"+articleId),404);assertStatus(tech.post("/knowledge",article),403);
        assertThat(success(tech.get("/knowledge?q="+marker+"&status=DRAFT")).path("total").asLong()).isZero();
        success(admin.post("/knowledge/"+articleId+"/status",Map.of("status","PUBLISHED","expectedRevision",1,"changeNote","Aprobado")));
        long institution=institution(20,20),id=supportCase(institution,"EMAIL");
        assertThat(success(tech.get("/knowledge/"+articleId)).path("id").asLong()).isEqualTo(articleId);
        assertStatus(tech.get("/knowledge/"+articleId+"/history"),403);
        assertStatus(tech.put("/knowledge/"+articleId,article),403);
        assertStatus(tech.post("/knowledge/"+articleId+"/status",Map.of("status","ARCHIVED","expectedRevision",2,"changeNote","No permitido")),403);
        assertThat(success(tech.post("/cases/"+id+"/knowledge-links",Map.of("articleId",articleId))).size()).isEqualTo(1);
        success(tech.delete("/cases/"+id+"/knowledge-links/"+articleId));
        success(tech.post("/cases/"+id+"/knowledge-links",Map.of("articleId",articleId)));
        success(admin.post("/knowledge/"+articleId+"/status",Map.of("status","ARCHIVED","expectedRevision",2,"changeNote","Revisión pendiente")));
        assertThat(success(tech.get("/cases/"+id+"/knowledge-links"))).isEmpty();assertStatus(tech.get("/knowledge/"+articleId),404);
        assertBalance(institution,0,20,0,20);
    }

    @Test void assignedTechnicianReceivesMailAndInternalBellEvenWhileMailIsPaused() throws Exception {
        Client tech=technician();long techId=uid(tech),institution=institution(30,30);
        Client customer=institutionUser(institution);long id=success(customer.post("/cases",casePayload(institution,"EMAIL"))).path("id").asLong();
        success(tech.post("/cases/"+id+"/claim",Map.of()));
        String email=success(tech.get("/auth/me")).path("email").asText();
        assertThat(db.queryForList("SELECT recipient FROM notification_outbox WHERE case_id=? AND event_type='CLAIM_CASE'",String.class,id)).contains(email,"contacto@example.test");
        addEntry(customer,id,"Cliente agrega diagnóstico",null,0,false,200);
        JsonNode inbox=success(tech.get("/inbox"));assertThat(inbox.path("items").toString()).contains("Cliente agrega diagnóstico");
        assertThat(inbox.path("pendingReplies").toString()).contains("\"caseId\":"+id);
        try {
            success(admin.post("/notifications/config",Map.of("paused",true)));
            addEntry(admin,id,"Nota privada disponible sin SMTP",null,0,true,200);
            JsonNode pausedInbox=success(tech.get("/inbox"));assertThat(pausedInbox.path("items").toString()).contains("Nota privada disponible sin SMTP");
            assertThat(success(customer.get("/inbox")).path("items").toString()).doesNotContain("Nota privada disponible sin SMTP");
            long noticeId=db.queryForObject("SELECT MAX(id) FROM portal_notifications WHERE recipient_id=? AND case_id=? AND internal AND event_type='CREATE_ENTRY'",Long.class,techId,id);
            // Identify by privacy and event as descriptions can include a stable consumed-time footer.
            assertStatus(tech.post("/inbox/"+noticeId+"/read",Map.of()),204);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE case_id=? AND body LIKE '%Nota privada disponible sin SMTP%' AND recipient=? AND status='SUPPRESSED'",Long.class,id,email)).isEqualTo(1);
            assertStatus(tech.post("/notifications/config",Map.of("paused",false)),403);
        } finally {success(admin.post("/notifications/config",Map.of("paused",false)));}
    }

    @Test void disablingTechnicianImmediatelyInvalidatesExistingSessionAndRoleCannotBePromoted() throws Exception {
        Client tech=technician();JsonNode user=success(tech.get("/auth/me"));long id=user.path("id").asLong();
        var promote=staffPayload(user.path("username").asText(),"ADMIN");assertStatus(admin.put("/users/"+id,promote),409);
        var disabled=staffPayload(user.path("username").asText(),"TECH");disabled.put("active",false);disabled.remove("password");
        success(admin.put("/users/"+id,disabled));assertStatus(tech.get("/cases"),401);
        assertStatus(new Client().authenticate(user.path("username").asText(),PASSWORD),401);
    }
}
