package mx.siabuc.helpdesk;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api")
public class ClientFinanceAccessController {
    private final ClientFinanceAccessService service;
    public ClientFinanceAccessController(ClientFinanceAccessService service){this.service=service;}
    @GetMapping("/client-finance/access") public Map<String,Boolean> mine(){return Map.of("enabled",service.myEnabled());}
    @GetMapping("/client-finance-access") public List<ClientFinanceAccessService.Access> settings(){return service.settings();}
    @PutMapping("/client-finance-access/{id}") public ClientFinanceAccessService.Access update(@PathVariable long id,@RequestBody JsonNode input){if(input==null||!input.isObject()||input.size()!=1||!input.path("enabled").isBoolean())throw ApiException.bad("Envíe enabled como booleano.");return service.update(id,input.path("enabled").asBoolean());}
}
