package mx.siabuc.helpdesk;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LicenseController {
    private final LicenseService licenses;
    private final HelpdeskService helpdesk;
    public LicenseController(LicenseService licenses, HelpdeskService helpdesk) { this.licenses = licenses; this.helpdesk = helpdesk; }
    @GetMapping("/license-support-defaults") public LicenseService.Defaults defaults() { admin(); return licenses.defaults(); }
    @PutMapping("/license-support-defaults") public LicenseService.Defaults defaults(@RequestBody LicenseService.DefaultsInput input) { return licenses.saveDefaults(input, admin()); }
    @GetMapping("/licenses") public List<LicenseService.License> list(@RequestParam(required=false) Long institutionId) { return licenses.list(helpdesk.me(), institutionId); }
    @PostMapping("/licenses") @ResponseStatus(HttpStatus.CREATED)
    public LicenseService.License create(@RequestBody LicenseService.CreateInput input) { return licenses.create(input, admin()); }
    @PostMapping("/licenses/{id}/hours") public LicenseService.License topup(@PathVariable long id, @RequestBody LicenseService.TopupInput input) { return licenses.topup(id, input, admin()); }
    @GetMapping("/licenses/{id}/history") public List<LicenseService.Movement> history(@PathVariable long id) { return licenses.history(id, admin()); }
    private Models.User admin() { var actor = helpdesk.me(); if (actor.role() != Models.Role.ADMIN) throw ApiException.forbidden(); return actor; }
}
