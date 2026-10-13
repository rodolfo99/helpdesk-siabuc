package mx.siabuc.helpdesk;

import static mx.siabuc.helpdesk.Models.*;

import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final HelpdeskService service;
    private final ObjectMapper json;
    public ApiController(HelpdeskService service, ObjectMapper json) { this.service = service; this.json = json; }

    @GetMapping("/health") public Map<String,String> health() { service.health(); return Map.of("status", "UP"); }
    @GetMapping("/auth/csrf") public Map<String,String> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute("rawCsrfToken");
        return Map.of("token", token.getToken());
    }
    @GetMapping("/auth/me") public User me() { return service.me(); }
    @PutMapping("/auth/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@RequestBody PasswordInput input, HttpServletRequest request) {
        service.changePassword(input);
        request.getSession().setAttribute("credentialVersion", service.credentialVersion(service.me().username()));
    }

    @GetMapping("/institutions") public List<Institution> institutions() { return service.institutions(); }
    @PostMapping("/institutions") @ResponseStatus(HttpStatus.CREATED)
    public Institution createInstitution(@RequestBody InstitutionInput input) { return service.createInstitution(input); }
    @PutMapping("/institutions/{id}") public Institution updateInstitution(@PathVariable long id, @RequestBody JsonNode input) {
        return service.updateInstitution(id, convert(input, InstitutionInput.class), input.has("supportAdminId"));
    }
    @DeleteMapping("/institutions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInstitution(@PathVariable long id) { service.deleteInstitution(id); }

    @GetMapping("/users") public List<User> users() { return service.users(); }
    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED)
    public User createUser(@RequestBody UserInput input) { return service.createUser(input); }
    @PutMapping("/users/{id}") public User updateUser(@PathVariable long id, @RequestBody UserInput input) { return service.updateUser(id, input); }

    @GetMapping("/cases") public List<SupportCase> cases() { return service.cases(); }
    @PostMapping("/cases") @ResponseStatus(HttpStatus.CREATED)
    public CaseDetail createCase(@RequestBody CaseInput input) { return service.createCase(input); }
    @GetMapping("/cases/{id}") public CaseDetail caseDetail(@PathVariable long id) { return service.caseDetail(id); }
    @PutMapping("/cases/{id}") public CaseDetail updateCase(@PathVariable long id, @RequestBody JsonNode input) {
        return service.updateCase(id, convert(input, CaseEdit.class), input.has("assignedAdminId"));
    }
    @PostMapping("/cases/{id}/status") public CaseDetail status(@PathVariable long id, @RequestBody StatusInput input) { return service.changeStatus(id, input); }
    @PostMapping("/cases/{id}/entries") public CaseDetail entry(@PathVariable long id, @RequestBody EntryInput input) { return service.addEntry(id, input); }
    @PostMapping("/cases/{id}/entries/{entryId}/void") public CaseDetail voidEntry(@PathVariable long id, @PathVariable long entryId, @RequestBody VoidInput input) { return service.voidEntry(id, entryId, input); }
    @GetMapping("/audit") public List<Audit> audit() { return service.audit(); }
    @GetMapping("/payments") public List<Payment> payments() { return service.payments(); }
    @PostMapping("/payments") @ResponseStatus(HttpStatus.CREATED)
    public Payment createPayment(@RequestBody PaymentInput input) { return service.createPayment(input); }
    @PostMapping("/payments/{id}/void")
    public Payment voidPayment(@PathVariable long id, @RequestBody VoidInput input) { return service.voidPayment(id, input); }
    private <T> T convert(JsonNode input, Class<T> type) {
        if (input == null || !input.isObject()) throw ApiException.bad("La solicitud debe ser un objeto JSON.");
        try { return json.convertValue(input, type); }
        catch (IllegalArgumentException e) { throw ApiException.bad("Solicitud inválida: revise campos, tipos de datos y valores permitidos."); }
    }
}
