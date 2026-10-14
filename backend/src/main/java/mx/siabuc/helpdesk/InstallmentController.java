package mx.siabuc.helpdesk;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/installments")
public class InstallmentController {
 private final InstallmentService service;public InstallmentController(InstallmentService service){this.service=service;}
 @GetMapping public InstallmentService.Overview overview(){return service.overview();}
 @GetMapping("/{id}") public InstallmentService.Detail detail(@PathVariable long id){return service.detail(id);}
 @PostMapping public InstallmentService.Detail create(@RequestBody InstallmentService.Input input){return service.create(input);}
 @PostMapping("/{id}/cancel") public InstallmentService.Detail cancel(@PathVariable long id,@RequestBody InstallmentService.Reason input){return service.cancel(id,input);}
 @PostMapping("/{id}/allocations") public InstallmentService.Detail allocate(@PathVariable long id,@RequestBody InstallmentService.Link input){return service.allocate(id,input);}
 @PostMapping("/{id}/allocations/{allocation}/remove") public InstallmentService.Detail remove(@PathVariable long id,@PathVariable long allocation,@RequestBody InstallmentService.Reason input){return service.remove(id,allocation,input);}
 @PutMapping("/settings") public InstallmentService.Settings settings(@RequestBody InstallmentService.Settings input){return service.settings(input);}
 @PostMapping("/scan") public InstallmentService.ScanResult scan(){return service.scan();}
 @PostMapping("/notices/{id}/read") public void read(@PathVariable long id){service.read(id);}
}
