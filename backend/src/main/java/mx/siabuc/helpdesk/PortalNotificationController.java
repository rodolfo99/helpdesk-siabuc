package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PortalNotificationController {
    private final PortalNotificationService service;
    public PortalNotificationController(PortalNotificationService service) { this.service=service; }
    @GetMapping("/inbox") public PortalNotificationService.Inbox inbox() { return service.inbox(); }
    @PostMapping("/inbox/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable long id,@RequestBody(required=false) Map<String,Object> input) {
        requireEmpty(input); service.markRead(id);
    }
    @PostMapping("/inbox/read-all") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@RequestBody(required=false) Map<String,Object> input) {
        requireEmpty(input); service.markAllRead();
    }
    @GetMapping("/balance-alert-settings")
    public List<PortalNotificationService.BalanceAlertSetting> settings() { return service.settings(); }
    @PutMapping("/balance-alert-settings/{institutionId}")
    public PortalNotificationService.BalanceAlertSetting updateSettings(@PathVariable long institutionId,@RequestBody JsonNode input) {
        if (input==null || !input.isObject() || input.size()!=3 || !input.path("emailThresholdMinutes").isIntegralNumber()
                || !input.path("remoteThresholdMinutes").isIntegralNumber() || !input.path("enabled").isBoolean()
                || !input.path("emailThresholdMinutes").canConvertToLong() || !input.path("remoteThresholdMinutes").canConvertToLong())
            throw ApiException.bad("Envíe emailThresholdMinutes, remoteThresholdMinutes como enteros y enabled como booleano.");
        return service.updateSettings(institutionId,input.get("emailThresholdMinutes").longValue(),input.get("remoteThresholdMinutes").longValue(),input.get("enabled").booleanValue());
    }
    private void requireEmpty(Map<String,Object> input) {
        if (input!=null && !input.isEmpty()) throw ApiException.bad("Envíe un objeto vacío.");
    }
}
