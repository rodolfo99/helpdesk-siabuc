package mx.siabuc.helpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class HelpdeskApplication {
    public static void main(String[] args) { SpringApplication.run(HelpdeskApplication.class, args); }
    @Bean ApplicationRunner initializeAdministrator(HelpdeskService service) {
        return args -> service.bootstrapAdmin();
    }
}
