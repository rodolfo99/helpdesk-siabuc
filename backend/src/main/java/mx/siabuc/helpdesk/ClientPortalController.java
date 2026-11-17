package mx.siabuc.helpdesk;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Alta de solicitudes desde el portal cliente. El servidor obtiene institución y autor
 * de la sesión; el formulario nunca selecciona identidades ni responsables.
 * No modifica el contrato heredado /api/cases ni el control de saldo.
 */
@RestController
@RequestMapping("/api/client-portal")
public class ClientPortalController {
    public record TicketInput(String title, String description, Models.Channel channel,
                              CaseProfileService.ProfileInput profile, Long licenseId) {}

    private final HelpdeskService service;
    private final ClientInformationService information;
    private final ClientGuideService guide;
    public ClientPortalController(HelpdeskService service, ClientInformationService information, ClientGuideService guide) {
        this.service = service;
        this.information = information;
        this.guide = guide;
    }

    @GetMapping("/guide")
    public ClientGuideService.Status guide() {
        return guide.status(service.me());
    }

    @PostMapping("/guide/complete")
    public ClientGuideService.Status completeGuide() {
        return guide.complete(service.me());
    }

    @GetMapping("/my-information")
    public ClientInformationService.Summary information() {
        return information.summary(service.me());
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public Models.CaseDetail create(@RequestBody TicketInput input) {
        Models.User user = service.me();
        if (user.role() != Models.Role.USER) throw ApiException.forbidden();
        if (input == null || input.channel() == null)
            throw ApiException.bad("Seleccione un canal de soporte.");
        String title = input.title() == null ? "" : input.title().strip();
        String description = input.description() == null ? "" : input.description().strip();
        if (title.length() < 5 || title.length() > 150)
            throw ApiException.bad("El asunto debe contener entre 5 y 150 caracteres.");
        if (description.length() < 20 || description.length() > 5000)
            throw ApiException.bad("La descripción debe contener entre 20 y 5,000 caracteres.");
        return service.createCase(new Models.CaseInput(
            user.institutionId(), title, description, input.channel(), null, input.profile(), input.licenseId()));
    }
}
