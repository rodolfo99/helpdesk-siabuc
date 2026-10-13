package mx.siabuc.helpdesk;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RemoteSessionController {
    private final RemoteSessionService service;
    public RemoteSessionController(RemoteSessionService service) { this.service=service; }
    @GetMapping("/cases/{id}/remote-sessions") public List<RemoteSessionService.Session> list(@PathVariable long id) { return service.list(id); }
    @PostMapping("/cases/{id}/remote-sessions") public RemoteSessionService.Session propose(@PathVariable long id,@RequestBody RemoteSessionService.Proposal input) { return service.propose(id,input); }
    @PostMapping("/remote-sessions/{id}/reschedule") public RemoteSessionService.Session reschedule(@PathVariable long id,@RequestBody RemoteSessionService.Proposal input) { return service.reschedule(id,input); }
    @PostMapping("/remote-sessions/{id}/{action:confirm|decline|cancel|complete}") public RemoteSessionService.Session respond(@PathVariable long id,@PathVariable String action,@RequestBody(required=false) RemoteSessionService.Response input) { return service.respond(id,action,input); }
}
