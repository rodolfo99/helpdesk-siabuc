package mx.siabuc.helpdesk;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/reply-templates")
public class ReplyTemplateController {
 private final ReplyTemplateService service;
 public ReplyTemplateController(ReplyTemplateService service){this.service=service;}
 @GetMapping public List<ReplyTemplateService.Summary> list(@RequestParam(defaultValue="") String q,@RequestParam(required=false) CaseProfileService.Category category,@RequestParam(defaultValue="") String siabucVersion,@RequestParam(defaultValue="false") boolean includeArchived){return service.list(q,category,siabucVersion,includeArchived);}
 @GetMapping("/{id}") public ReplyTemplateService.Template get(@PathVariable long id){return service.get(id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public ReplyTemplateService.Template create(@RequestBody ReplyTemplateService.Input input){return service.create(input);}
 @PutMapping("/{id}") public ReplyTemplateService.Template update(@PathVariable long id,@RequestBody ReplyTemplateService.Input input){return service.update(id,input);}
 @PostMapping("/{id}/status") public ReplyTemplateService.Template status(@PathVariable long id,@RequestBody ReplyTemplateService.StatusInput input){return service.status(id,input);}
 @GetMapping("/{id}/history") public List<ReplyTemplateService.History> history(@PathVariable long id){return service.history(id);}
}
