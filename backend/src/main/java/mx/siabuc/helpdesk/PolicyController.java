package mx.siabuc.helpdesk;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {
    private final HelpdeskService helpdesk;
    private final PolicyService policies;
    public PolicyController(HelpdeskService helpdesk,PolicyService policies){this.helpdesk=helpdesk;this.policies=policies;}
    @GetMapping public List<PolicyService.Policy> list(@RequestParam(required=false) Long institutionId){actor();return policies.list(institutionId);}
    @GetMapping("/{id}") public PolicyService.Detail detail(@PathVariable long id){actor();return policies.detail(id);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public PolicyService.Detail create(@RequestBody PolicyService.PolicyInput input){return policies.create(input,actor());}
    @PutMapping("/{id}") public PolicyService.Detail update(@PathVariable long id,@RequestBody PolicyService.PolicyInput input){return policies.update(id,input,actor());}
    @PostMapping("/{id}/cancel") public PolicyService.Detail cancel(@PathVariable long id,@RequestBody Models.VoidInput input){return policies.cancel(id,input.reason(),actor());}
    private Models.User actor(){Models.User actor=helpdesk.me();if(actor.role()==Models.Role.USER)throw ApiException.forbidden();return actor;}
}
