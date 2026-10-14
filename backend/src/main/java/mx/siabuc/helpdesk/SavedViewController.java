package mx.siabuc.helpdesk;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/saved-views")
public class SavedViewController {
 private final SavedViewService service;
 public SavedViewController(SavedViewService service){this.service=service;}
 @GetMapping public List<SavedViewService.View> list(){return service.list();}
 @GetMapping("/{id}") public SavedViewService.View get(@PathVariable long id){return service.get(id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public SavedViewService.View create(@RequestBody SavedViewService.Input input){return service.create(input);}
 @PutMapping("/{id}") public SavedViewService.View update(@PathVariable long id,@RequestBody SavedViewService.Input input){return service.update(id,input);}
 @DeleteMapping("/{id}") public void delete(@PathVariable long id){service.delete(id);}
}
