package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    private Map<String,Object> article(String title) {
        return Map.of("title",title,"category","CATALOGING","siabucVersion","SIABUC 9.1","summary","Cómo resolver el error del catálogo",
            "body","1. Revisar el catálogo.\n2. Ejecutar el procedimiento de diagnóstico.","changeNote","Documento inicial");
    }
    private JsonNode create(String title) throws Exception {return success(admin.post("/knowledge",article(title)));}
    private JsonNode status(JsonNode article,String status) throws Exception {
        return success(admin.post("/knowledge/"+article.path("id").asLong()+"/status",Map.of("status",status,
            "expectedRevision",article.path("revision").asInt(),"changeNote","Cambio editorial de prueba")));
    }
    private Map<String,Object> edit(JsonNode value,String title) {
        var input=new LinkedHashMap<>(article(title));input.put("expectedRevision",value.path("revision").asInt());return input;
    }
    private Client billing() throws Exception {
        String username=unique("cobranza");var body=new LinkedHashMap<>(userPayload(username,institution(0,0),true));
        body.put("role","BILLING");body.put("institutionId",null);success(admin.post("/users",body));return login(username,PASSWORD);
    }

    @Test void publishedSearchNeverExposesDraftOrArchivedEvenWithStatusFilter() throws Exception {
        Client client=institutionUser(institution(0,0));String marker=unique("Knowledge");
        JsonNode draft=create(marker+" borrador"),published=status(create(marker+" público"),"PUBLISHED"),archived=status(create(marker+" retirado"),"ARCHIVED");
        assertStatus(client.get("/knowledge/"+draft.path("id").asLong()),404);
        assertStatus(client.get("/knowledge/"+archived.path("id").asLong()),404);
        JsonNode page=success(client.get("/knowledge?q="+marker+"&status=DRAFT&category=CATALOGING&siabucVersion=9.1&pageSize=1"));
        assertThat(page.path("total").asInt()).isEqualTo(1);
        assertThat(page.path("items").get(0).path("id").asLong()).isEqualTo(published.path("id").asLong());
        assertThat(page.toString()).doesNotContain("borrador","retirado");
        assertThat(page.path("items").get(0).has("body")).isFalse();
        assertThat(success(client.get("/knowledge/"+published.path("id").asLong())).path("body").asText()).contains("diagnóstico");
        assertThat(success(admin.get("/knowledge?q="+marker)).path("total").asInt()).isEqualTo(3);
        assertThat(success(client.get("/knowledge?q="+marker+"&category=LOANS")).path("total").asInt()).isZero();
        assertThat(success(client.get("/knowledge?q="+marker+"&siabucVersion=8.0")).path("total").asInt()).isZero();
        assertStatus(client.get("/knowledge?page=0"),400);assertStatus(client.get("/knowledge?pageSize=101"),400);
    }
    @Test void usersCannotEditOrReadEditorialHistoryAndBillingCannotAccessKnowledge() throws Exception {
        Client client=institutionUser(institution(0,0)),billing=billing();JsonNode value=status(create(unique("Permisos")),"PUBLISHED");long id=value.path("id").asLong();
        assertStatus(client.post("/knowledge",article("No autorizado")),403);
        assertStatus(client.put("/knowledge/"+id,edit(value,"Cambio")),403);
        assertStatus(client.post("/knowledge/"+id+"/status",Map.of("status","DRAFT","expectedRevision",2,"changeNote","No")),403);
        assertStatus(client.get("/knowledge/"+id+"/history"),403);
        assertStatus(billing.get("/knowledge"),403);assertStatus(billing.get("/knowledge/"+id),403);
        assertStatus(billing.post("/knowledge",article("No autorizado")),403);
        assertThat(success(admin.get("/knowledge/"+id)).path("title").asText()).isEqualTo(value.path("title").asText());
    }
    @Test void editorialTransitionsRequireDraftAndExpectedRevisionWithAuditSnapshots() throws Exception {
        JsonNode draft=create(unique("Editorial"));long id=draft.path("id").asLong();
        JsonNode published=status(draft,"PUBLISHED");
        assertStatus(admin.put("/knowledge/"+id,edit(published,"No debe editar")),409);
        JsonNode secondDraft=status(published,"DRAFT");
        var input=edit(secondDraft,"Contenido corregido");input.put("changeNote","Corrección comprobada");
        JsonNode edited=success(admin.put("/knowledge/"+id,input));
        assertThat(edited.path("revision").asInt()).isEqualTo(4);
        assertStatus(admin.put("/knowledge/"+id,input),409);
        assertStatus(admin.post("/knowledge/"+id+"/status",Map.of("status","PUBLISHED","expectedRevision",3,"changeNote","Obsoleto")),409);
        JsonNode archived=status(edited,"ARCHIVED");
        assertStatus(admin.post("/knowledge/"+id+"/status",Map.of("status","PUBLISHED","expectedRevision",5,"changeNote","Salto inválido")),409);
        JsonNode restored=status(archived,"DRAFT"),republished=status(restored,"PUBLISHED");
        assertThat(republished.path("revision").asInt()).isEqualTo(7);
        JsonNode history=success(admin.get("/knowledge/"+id+"/history"));assertThat(history.size()).isEqualTo(7);
        assertThat(history.get(0).path("action").asText()).isEqualTo("PUBLISH");
        assertThat(history.get(3).path("changeNote").asText()).isEqualTo("Corrección comprobada");
        assertThat(history.get(6).path("title").asText()).isEqualTo(draft.path("title").asText());
        assertThat(history.get(6).path("actor").asText()).isEqualTo("testadmin");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_type='KNOWLEDGE' AND entity_id=?",Long.class,id)).isEqualTo(7);
    }
    @Test void invalidInputsDoNotMutateAndMarkupRemainsLiteralPlainText() throws Exception {
        var payload=new LinkedHashMap<>(article(unique("Texto")));String literal="<script>alert('diagnóstico')</script>\n<img src=x onerror=alert(1)>";payload.put("body",literal);
        JsonNode value=success(admin.post("/knowledge",payload));long id=value.path("id").asLong();
        assertThat(value.path("body").asText()).isEqualTo(literal);
        var invalid=edit(value,"Título cambiado");invalid.put("body","x".repeat(50001));assertStatus(admin.put("/knowledge/"+id,invalid),400);
        invalid.put("body","Texto\u0000binario");assertStatus(admin.put("/knowledge/"+id,invalid),400);
        invalid.put("body","   ");assertStatus(admin.put("/knowledge/"+id,invalid),400);
        assertStatus(admin.post("/knowledge/"+id+"/status",Map.of("status","PUBLISHED","expectedRevision",1,"changeNote","   ")),400);
        invalid.put("body","Válido");invalid.remove("expectedRevision");assertStatus(admin.put("/knowledge/"+id,invalid),400);
        assertThat(success(admin.get("/knowledge/"+id)).path("revision").asInt()).isEqualTo(1);
        assertThat(success(admin.get("/knowledge/"+id+"/history")).size()).isEqualTo(1);
    }
    @Test void linksEnforceInstitutionAndRoleWithoutDebitingMinutesOrDuplicatingEvents() throws Exception {
        long institution=institution(60,40),other=institution(60,40),caseId=supportCase(institution,"EMAIL");
        Client client=institutionUser(institution),outsider=institutionUser(other),billing=billing();
        JsonNode draft=create(unique("Solución"));long articleId=draft.path("id").asLong();
        assertStatus(admin.post("/cases/"+caseId+"/knowledge-links",Map.of("articleId",articleId)),409);
        status(draft,"PUBLISHED");
        assertStatus(client.post("/cases/"+caseId+"/knowledge-links",Map.of("articleId",articleId)),403);
        assertStatus(client.delete("/cases/"+caseId+"/knowledge-links/"+articleId),403);
        assertStatus(outsider.get("/cases/"+caseId+"/knowledge-links"),404);
        assertStatus(billing.get("/cases/"+caseId+"/knowledge-links"),403);
        assertStatus(billing.post("/cases/"+caseId+"/knowledge-links",Map.of("articleId",articleId)),403);
        success(admin.post("/cases/"+caseId+"/knowledge-links",Map.of("articleId",articleId)));
        success(admin.post("/cases/"+caseId+"/knowledge-links",Map.of("articleId",articleId)));
        JsonNode links=success(client.get("/cases/"+caseId+"/knowledge-links"));assertThat(links.size()).isEqualTo(1);
        assertThat(links.get(0).path("article").path("id").asLong()).isEqualTo(articleId);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM knowledge_link_events WHERE case_id=?",Long.class,caseId)).isEqualTo(1);
        assertBalance(institution,0,60,0,40);
    }
    @Test void hiddenLinkedArticlesExposeNoIdsTitlesOrMetadataAndRepublishUsesCurrentSolution() throws Exception {
        long institution=institution(60,40),caseId=supportCase(institution,"EMAIL");Client client=institutionUser(institution);
        JsonNode published=status(create(unique("Oculto")),"PUBLISHED");long id=published.path("id").asLong();
        success(admin.post("/cases/"+caseId+"/knowledge-links",Map.of("articleId",id)));
        JsonNode archived=status(published,"ARCHIVED");
        assertThat(success(client.get("/cases/"+caseId+"/knowledge-links")).toString()).isEqualTo("[]");
        assertStatus(client.get("/knowledge/"+id),404);
        assertThat(success(admin.get("/cases/"+caseId+"/knowledge-links")).get(0).path("article").path("status").asText()).isEqualTo("ARCHIVED");
        JsonNode draft=status(archived,"DRAFT");var updated=edit(draft,"Nueva solución pública");updated.put("body","Versión vigente corregida");
        JsonNode edited=success(admin.put("/knowledge/"+id,updated));
        assertThat(success(client.get("/cases/"+caseId+"/knowledge-links")).toString()).isEqualTo("[]");
        status(edited,"PUBLISHED");
        assertThat(success(client.get("/cases/"+caseId+"/knowledge-links")).get(0).path("article").path("title").asText()).isEqualTo("Nueva solución pública");
        assertThat(success(client.get("/knowledge/"+id)).path("body").asText()).isEqualTo("Versión vigente corregida");
    }
    @Test void unlinkPreservesAuditAndArticleWithoutChangingBalancesAndRelinkIsPossible() throws Exception {
        long institution=institution(60,40),caseId=supportCase(institution,"EMAIL");JsonNode value=status(create(unique("Vínculo")),"PUBLISHED");long id=value.path("id").asLong();
        success(admin.post("/cases/"+caseId+"/knowledge-links",Map.of("articleId",id)));
        success(admin.delete("/cases/"+caseId+"/knowledge-links/"+id));success(admin.delete("/cases/"+caseId+"/knowledge-links/"+id));
        assertThat(success(admin.get("/cases/"+caseId+"/knowledge-links")).size()).isZero();
        assertThat(db.queryForObject("SELECT active FROM knowledge_case_links WHERE case_id=? AND article_id=?",Boolean.class,caseId,id)).isFalse();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM knowledge_link_events WHERE case_id=?",Long.class,caseId)).isEqualTo(2);
        assertThat(success(admin.get("/knowledge/"+id)).path("status").asText()).isEqualTo("PUBLISHED");
        success(admin.post("/cases/"+caseId+"/knowledge-links",Map.of("articleId",id)));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM knowledge_link_events WHERE case_id=?",Long.class,caseId)).isEqualTo(3);
        assertBalance(institution,0,60,0,40);
    }
    @Test void searchTreatsWildcardCharactersAsLiteralAndRejectsUnknownCategories() throws Exception {
        String marker=unique("Literal");create(marker+" 100%_listo");create(marker+" normal");
        JsonNode page=success(admin.perform(admin.builder("GET","/knowledge",false).param("q",marker+" 100%_")));
        assertThat(page.path("total").asInt()).isEqualTo(1);
        assertThat(page.path("items").get(0).path("title").asText()).contains("100%_");
        assertStatus(admin.get("/knowledge?category=INVALID"),400);
    }
}
