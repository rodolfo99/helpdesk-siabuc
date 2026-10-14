package mx.siabuc.helpdesk;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/sla-alerts")
public class SlaAlertController {
 private final SlaAlertService service;
 public SlaAlertController(SlaAlertService service){this.service=service;}
 @GetMapping("/settings") public SlaAlertService.Settings settings(){return service.settings();}
 @PutMapping("/settings") public SlaAlertService.Settings update(@RequestBody SlaAlertService.Input input){return service.update(input);}
 @PostMapping("/scan") public Map<String,Integer> scan(){return service.scanAsAdmin();}
}
