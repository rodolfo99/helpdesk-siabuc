package mx.siabuc.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;

class ClientPortalIntegrationIT extends ApiIntegrationSupport {
    private Map<String,Object> ticket(String title,String description) {
        return Map.of("title",title,"description",description,"channel","EMAIL");
    }

    @Test void clientPortalCreatesOwnTicketAndProtectsOtherInstitutions() throws Exception {
        long own = institution(120,0);
        long other = institution(120,0);
        Client client = institutionUser(own);
        JsonNode created = success(client.post("/client-portal/tickets",
            ticket("Solicitar apoyo SIABUC", "Necesito asistencia para validar la importación de registros.")));
        assertThat(created.path("institutionId").asLong()).isEqualTo(own);
        assertThat(created.path("id").asLong()).isPositive();
        assertStatus(client.get("/cases/"+created.path("id").asLong()),200);
        assertStatus(institutionUser(other).get("/cases/"+created.path("id").asLong()),404);
        assertStatus(admin.post("/client-portal/tickets",ticket("Asunto válido", "Descripción con longitud suficiente para la solicitud.")),403);
    }

    @Test void rejectsInvalidFieldsAndKeepsOriginalBalanceRules() throws Exception {
        long own = institution(120,0);
        Client client = institutionUser(own);
        assertStatus(client.post("/client-portal/tickets",ticket("Cort", "Descripción suficientemente extensa para la prueba.")),400);
        assertStatus(client.post("/client-portal/tickets",ticket("Asunto válido", "Muy breve")),400);
        assertStatus(client.post("/client-portal/tickets",Map.of("title","Asunto válido",
            "description","Descripción suficientemente extensa para la prueba.","channel","REMOTE")),409);
    }
}
