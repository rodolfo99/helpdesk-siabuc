package mx.siabuc.helpdesk;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class KnowledgeController {
    private final KnowledgeService knowledge;
    public KnowledgeController(KnowledgeService knowledge){this.knowledge=knowledge;}
    @GetMapping("/knowledge") public KnowledgeService.Page list(
        @RequestParam(defaultValue="") String q,@RequestParam(required=false) KnowledgeService.Category category,
        @RequestParam(defaultValue="") String siabucVersion,@RequestParam(required=false) KnowledgeService.Status status,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){
        return knowledge.list(q,category,siabucVersion,status,page,pageSize);
    }
    @GetMapping("/knowledge/{id}") public KnowledgeService.Article get(@PathVariable long id){return knowledge.get(id);}
    @PostMapping("/knowledge") @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeService.Article create(@RequestBody KnowledgeService.Input input){return knowledge.create(input);}
    @PutMapping("/knowledge/{id}") public KnowledgeService.Article update(@PathVariable long id,@RequestBody KnowledgeService.Input input){return knowledge.update(id,input);}
    @PostMapping("/knowledge/{id}/status") public KnowledgeService.Article status(@PathVariable long id,@RequestBody KnowledgeService.StatusInput input){return knowledge.changeStatus(id,input);}
    @GetMapping("/knowledge/{id}/history") public List<KnowledgeService.History> history(@PathVariable long id){return knowledge.history(id);}
    @GetMapping("/cases/{id}/knowledge-links") public List<KnowledgeService.CaseLink> links(@PathVariable long id){return knowledge.links(id);}
    @PostMapping("/cases/{id}/knowledge-links") public List<KnowledgeService.CaseLink> link(@PathVariable long id,@RequestBody KnowledgeService.LinkInput input){return knowledge.link(id,input);}
    @DeleteMapping("/cases/{id}/knowledge-links/{articleId}") public List<KnowledgeService.CaseLink> unlink(@PathVariable long id,@PathVariable long articleId){return knowledge.unlink(id,articleId);}
}
