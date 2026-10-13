package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;
    private final HelpdeskService helpdesk;
    public NotificationController(NotificationService notifications, HelpdeskService helpdesk) {
        this.notifications = notifications; this.helpdesk = helpdesk;
    }
    private Models.User admin() {
        Models.User actor = helpdesk.me();
        if (actor.role() != Models.Role.ADMIN) throw ApiException.forbidden();
        return actor;
    }
    @GetMapping public List<NotificationService.Notification> list() { admin(); return notifications.notifications(); }
    @GetMapping("/{id}") public NotificationService.NotificationDetail detail(@PathVariable long id) {
        admin(); return notifications.notificationDetail(id);
    }
    @GetMapping("/config") public NotificationService.Configuration config() { admin(); return notifications.configuration(); }
    @PostMapping("/config") public NotificationService.Configuration config(@RequestBody JsonNode input) {
        Models.User actor = admin();
        if (input == null || !input.isObject() || input.size() != 1 || !input.path("paused").isBoolean())
            throw ApiException.bad("Envíe únicamente el campo paused con un valor booleano: true o false.");
        return notifications.setPaused(input.get("paused").booleanValue(), actor.username());
    }
    @PostMapping("/{id}/retry") public NotificationService.Notification retry(@PathVariable long id,
            @RequestBody(required = false) Map<String,Object> input) {
        Models.User actor = admin(); requireEmpty(input); return notifications.retry(id, actor.username());
    }
    @PostMapping("/test") @ResponseStatus(HttpStatus.CREATED)
    public NotificationService.Notification test(@RequestBody(required = false) Map<String,Object> input) {
        Models.User actor = admin(); requireEmpty(input); return notifications.test(actor);
    }
    private static void requireEmpty(Map<String,Object> input) {
        if (input != null && !input.isEmpty()) throw ApiException.bad("Este endpoint no permite elegir destinatarios ni contenido; envíe un objeto vacío.");
    }
}
