package mx.siabuc.helpdesk;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/support-packages")
public class SupportPackageController {
 private final SupportPackageService service;public SupportPackageController(SupportPackageService service){this.service=service;}
 @GetMapping public List<SupportPackageService.Version> list(){return service.list();}
 @GetMapping("/{id}") public SupportPackageService.Detail detail(@PathVariable long id){return service.detail(id);}
 @PostMapping public SupportPackageService.Detail create(@RequestBody SupportPackageService.Input input){return service.create(input);}
 @PutMapping("/{id}") public SupportPackageService.Detail update(@PathVariable long id,@RequestBody SupportPackageService.Input input){return service.update(id,input);}
 @PostMapping("/{id}/active") public SupportPackageService.Detail active(@PathVariable long id,@RequestBody SupportPackageService.Active input){return service.active(id,input);}
}
