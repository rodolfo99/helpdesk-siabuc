package mx.siabuc.helpdesk;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CaseProfileController {
    private final HelpdeskService helpdesk;
    private final CaseProfileService profiles;
    public CaseProfileController(HelpdeskService helpdesk,CaseProfileService profiles) { this.helpdesk=helpdesk;this.profiles=profiles; }
    @GetMapping("/case-profiles") public List<CaseProfileService.Summary> profiles() {
        Models.User actor=helpdesk.me(); if(actor.role()==Models.Role.BILLING) throw ApiException.forbidden();
        return profiles.summaries(actor);
    }
    @GetMapping("/cases/{id}/profile") public CaseProfileService.Profile profile(@PathVariable long id) {
        helpdesk.caseDetail(id);return profiles.profile(id);
    }
    @PutMapping("/cases/{id}/profile") public CaseProfileService.Profile update(@PathVariable long id,@RequestBody CaseProfileService.ProfileInput input) {
        helpdesk.caseDetail(id);return profiles.update(id,input,helpdesk.me());
    }
    @GetMapping("/support-settings") public CaseProfileService.Settings settings() { admin();return profiles.settings(); }
    @PutMapping("/support-settings") public CaseProfileService.Settings updateSettings(@RequestBody CaseProfileService.Settings input) {
        return profiles.updateSettings(input,admin().username());
    }
    private Models.User admin() { Models.User actor=helpdesk.me();if(actor.role()!=Models.Role.ADMIN) throw ApiException.forbidden();return actor; }
}
