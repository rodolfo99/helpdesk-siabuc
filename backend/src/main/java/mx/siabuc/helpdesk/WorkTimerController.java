package mx.siabuc.helpdesk;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class WorkTimerController {
    private final WorkTimerService service;
    public WorkTimerController(WorkTimerService service) { this.service=service; }
    @GetMapping("/cases/{id}/timer") public WorkTimerService.Timer current(@PathVariable long id) { return service.current(id); }
    @PostMapping("/cases/{id}/timer/start") public WorkTimerService.Timer start(@PathVariable long id) { return service.start(id); }
    @PostMapping("/timers/{id}/{action:pause|resume|finish|cancel}") public WorkTimerService.Timer transition(@PathVariable long id,@PathVariable String action) { return service.transition(id,action); }
    @PostMapping("/timers/{id}/commit") public WorkTimerService.Timer commit(@PathVariable long id,@RequestBody WorkTimerService.CommitInput input) { return service.commit(id,input); }
}
