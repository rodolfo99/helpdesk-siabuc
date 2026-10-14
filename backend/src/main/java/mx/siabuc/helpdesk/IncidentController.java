package mx.siabuc.helpdesk;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api")
public class IncidentController {
    private final IncidentService incidents;
    public IncidentController(IncidentService incidents){this.incidents=incidents;}
    @GetMapping("/incidents") public IncidentService.Page list(@RequestParam(defaultValue="") String q,@RequestParam(required=false) IncidentService.Status status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return incidents.list(q,status,page,pageSize);}
    @GetMapping("/incidents/{id}") public IncidentService.Detail get(@PathVariable long id){return incidents.get(id);}
    @PostMapping("/incidents") @ResponseStatus(HttpStatus.CREATED) public IncidentService.Detail create(@RequestBody IncidentService.Input input){return incidents.create(input);}
    @PutMapping("/incidents/{id}") public IncidentService.Detail update(@PathVariable long id,@RequestBody IncidentService.Input input){return incidents.update(id,input);}
    @PostMapping("/incidents/{id}/progress") public IncidentService.Detail progress(@PathVariable long id,@RequestBody IncidentService.ProgressInput input){return incidents.progress(id,input);}
    @PostMapping("/incidents/{id}/publication") public IncidentService.Detail publication(@PathVariable long id,@RequestBody IncidentService.PublicationInput input){return incidents.publication(id,input);}
    @GetMapping("/incidents/{id}/case-options") public List<IncidentService.CaseOption> options(@PathVariable long id,@RequestParam String q){return incidents.caseOptions(id,q);}
    @PostMapping("/incidents/{id}/cases") public IncidentService.Detail link(@PathVariable long id,@RequestBody IncidentService.CaseInput input){return incidents.link(id,input);}
    @DeleteMapping("/incidents/{id}/cases/{caseId}") public IncidentService.Detail unlink(@PathVariable long id,@PathVariable long caseId){return incidents.unlink(id,caseId);}
    @GetMapping("/cases/{id}/incidents") public List<?> caseLinks(@PathVariable long id){return incidents.links(id);}
    @PostMapping("/cases/{id}/incidents") public List<IncidentService.StaffLink> caseLink(@PathVariable long id,@RequestBody IncidentService.LinkInput input){return incidents.linkCase(id,input);}
    @DeleteMapping("/cases/{id}/incidents/{incidentId}") public List<IncidentService.StaffLink> caseUnlink(@PathVariable long id,@PathVariable long incidentId){return incidents.unlinkCase(id,incidentId);}
}
