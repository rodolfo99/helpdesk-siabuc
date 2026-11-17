package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** These tests never download or contact the institution-hosted target URLs. */
class OfficialResourceIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    private Map<String,Object> resource(String title) {
        var data=new LinkedHashMap<String,Object>();
        data.put("title",title);
        data.put("kind","INSTALLER");
        data.put("siabucVersion","SIABUC 9");
        data.put("description","Instalador de prueba; no es un archivo publicado.");
        data.put("targetUrl","https://siabuc.ucol.mx/descargas/recurso-de-prueba-inexistente");
        return data;
    }

    private JsonNode changeStatus(JsonNode resource, String target) throws Exception {
        return success(admin.post("/official-resources/"+resource.path("id").asLong()+"/status",
            Map.of("status",target,"expectedRevision",resource.path("revision").asInt())));
    }

    @Test void clientsSeeOnlyPublishedResourcesAndNeverDraftsOrArchived() throws Exception {
        Client client=institutionUser(institution(20,20));
        String marker=unique("Descarga");
        JsonNode draft=success(admin.post("/official-resources",resource(marker+" borrador")));
        JsonNode publishedDraft=success(admin.post("/official-resources",resource(marker+" publico")));
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(success(client.get("/official-resources?q="+marker))).isEmpty();
        assertStatus(client.get("/official-resources/"+draft.path("id").asLong()),404);

        JsonNode published=changeStatus(publishedDraft,"PUBLISHED");
        JsonNode visible=success(client.get("/official-resources?q="+marker));
        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).path("id")).isEqualTo(published.path("id"));
        assertThat(visible.toString()).doesNotContain(marker+" borrador");
        assertThat(success(client.get("/official-resources?kind=INSTALLER&q="+marker))).hasSize(1);
        assertThat(success(client.get("/official-resources?kind=PATCH&q="+marker))).isEmpty();

        JsonNode withdrawn=changeStatus(published,"DRAFT");
        assertThat(success(client.get("/official-resources?q="+marker))).isEmpty();
        JsonNode archived=changeStatus(withdrawn,"ARCHIVED");
        assertStatus(client.get("/official-resources/"+archived.path("id").asLong()),404);
        assertThat(success(admin.get("/official-resources?q="+marker))).hasSize(2);
    }

    @Test void blocksInsecureAndUnrelatedDomainsWithoutFetchingRemoteFiles() throws Exception {
        for(String badUrl:new String[]{
                "http://siabuc.ucol.mx/archivo.zip",
                "https://evil.example/archivo.zip",
                "https://siabuc.ucol.mx.evil.example/archivo.zip",
                "https://user:pass@siabuc.ucol.mx/archivo.zip",
                "https://siabuc.ucol.mx:8443/archivo.zip",
                "javascript:alert(1)",
                "https://siabuc.ucol.mx/archivo.zip#fragment",
                "https://ucol.mx@evil.example/archivo.zip"
        }) {
            var input=resource(unique("URL"));
            input.put("targetUrl",badUrl);
            assertStatus(admin.post("/official-resources",input),400);
        }
        var allowed=resource(unique("URL valida"));
        allowed.put("targetUrl","https://recursos.ucol.mx/ejemplo.zip");
        assertThat(success(admin.post("/official-resources",allowed)).path("status").asText()).isEqualTo("DRAFT");
    }

    @Test void onlyAdminCanPublishAndRevisionPreventsLostUpdates() throws Exception {
        JsonNode resource=success(admin.post("/official-resources",resource(unique("Permisos"))));
        long id=resource.path("id").asLong();
        Client client=institutionUser(institution(20,20));
        assertStatus(client.post("/official-resources",resource("No autorizado")),403);
        assertStatus(client.put("/official-resources/"+id,resource("No autorizado")),403);
        assertStatus(client.post("/official-resources/"+id+"/status",
            Map.of("status","PUBLISHED","expectedRevision",1)),403);
        assertStatus(client.get("/official-resources/"+id),404);
        assertStatus(admin.put("/official-resources/"+id,resource("Sin revision")),400);

        var edit=resource(unique("Corregido"));
        edit.put("expectedRevision",resource.path("revision").asInt());
        JsonNode updated=success(admin.put("/official-resources/"+id,edit));
        assertThat(updated.path("revision").asInt()).isEqualTo(2);
        assertStatus(admin.put("/official-resources/"+id,edit),409);
        JsonNode published=changeStatus(updated,"PUBLISHED");
        var afterPublished=resource(unique("No editable"));
        afterPublished.put("expectedRevision",published.path("revision").asInt());
        assertStatus(admin.put("/official-resources/"+id,afterPublished),409);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_type='OFFICIAL_RESOURCE' AND entity_id=?",Long.class,id)).isEqualTo(3L);
    }
}
