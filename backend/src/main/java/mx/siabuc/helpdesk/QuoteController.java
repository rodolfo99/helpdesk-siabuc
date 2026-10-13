package mx.siabuc.helpdesk;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/quotes")
public class QuoteController {
 private final QuoteService service;public QuoteController(QuoteService service){this.service=service;}
 @GetMapping public List<QuoteService.Quote> list(@RequestParam(required=false) Long institutionId){return service.list(institutionId);}
 @GetMapping("/{id}") public QuoteService.Detail detail(@PathVariable long id){return service.detail(id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public QuoteService.Detail create(@RequestBody QuoteService.Input input){return service.create(input);}
 @PutMapping("/{id}") public QuoteService.Detail update(@PathVariable long id,@RequestBody QuoteService.Input input){return service.update(id,input);}
 @PostMapping("/{id}/send") public QuoteService.Detail send(@PathVariable long id,@RequestBody QuoteService.Evidence input){return service.send(id,input);}
 @PostMapping("/{id}/decision") public QuoteService.Detail decision(@PathVariable long id,@RequestBody QuoteService.Decision input){return service.decide(id,input);}
 @PostMapping("/{id}/convert") public QuoteService.Detail convert(@PathVariable long id,@RequestBody QuoteService.Conversion input){return service.convert(id,input);}
}
