package mx.siabuc.helpdesk;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/renewals")
public class RenewalController {
 private final RenewalService service;public RenewalController(RenewalService service){this.service=service;}
 @GetMapping public RenewalService.Overview overview(){return service.overview();}
 @GetMapping("/settings") public RenewalService.Settings settings(){return service.settings();}
 @PutMapping("/settings") public RenewalService.Settings settings(@RequestBody RenewalService.Settings input){return service.updateSettings(input);}
 @PostMapping("/scan") public RenewalService.ScanResult scan(){return service.scan();}
 @PostMapping("/notices/{id}/read") public Map<String,Boolean> read(@PathVariable long id){service.markRead(id);return Map.of("read",true);}
}
