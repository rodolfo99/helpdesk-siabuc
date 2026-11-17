package mx.siabuc.helpdesk;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class ClientAccessController {
    private final ClientRecoveryService recovery;
    private final ClientLoginService clientLogin;
    public ClientAccessController(ClientRecoveryService recovery, ClientLoginService clientLogin) {
        this.recovery=recovery; this.clientLogin=clientLogin;
    }

    @PostMapping("/recovery/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestRecovery(@RequestBody ClientRecoveryService.Request request) {
        recovery.request(request);
    }

    @PostMapping("/recovery/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@RequestBody ClientRecoveryService.Reset request) {
        recovery.reset(request);
    }

    @GetMapping("/client-login-settings")
    public ClientLoginService.Settings settings() { return clientLogin.settings(); }

    @PutMapping("/client-login-settings")
    public ClientLoginService.Settings settings(@RequestBody ClientLoginService.SettingsInput input) {
        return clientLogin.updateSettings(input);
    }

    @GetMapping("/client-login-locks")
    public java.util.List<ClientLoginService.LockState> locks() { return clientLogin.states(); }

    @PostMapping("/client-login-locks/{userId}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@PathVariable long userId) { clientLogin.unlock(userId); }
}
