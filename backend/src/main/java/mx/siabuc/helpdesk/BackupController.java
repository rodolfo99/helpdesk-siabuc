package mx.siabuc.helpdesk;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/backups")
public class BackupController {
    private final BackupService service;
    public BackupController(BackupService service){this.service=service;}
    @GetMapping("/status") public BackupService.Status status(){return service.status();}
    @PutMapping("/config") public BackupService.Status configure(@RequestBody BackupService.Configuration input){return service.configure(input);}
    @PostMapping("/enabled") public BackupService.Status enabled(@RequestBody JsonNode input){
        if(input==null||!input.isObject()||input.size()!=1||!input.path("enabled").isBoolean())throw ApiException.bad("Envíe únicamente enabled como booleano.");
        return service.enabled(input.get("enabled").booleanValue());}
    @PostMapping("/run") @ResponseStatus(HttpStatus.CREATED) public BackupService.Run trigger(@RequestBody(required=false) Map<String,Object> input){
        if(input!=null&&!input.isEmpty())throw ApiException.bad("Envíe un objeto vacío; el destino se configura en el servidor.");return service.trigger();}
    @GetMapping("/{id}") public BackupService.Detail detail(@PathVariable long id){return service.detail(id);}
}
