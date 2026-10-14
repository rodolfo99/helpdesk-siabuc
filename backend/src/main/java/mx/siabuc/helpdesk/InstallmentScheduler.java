package mx.siabuc.helpdesk;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class InstallmentScheduler {
 private final InstallmentService service;public InstallmentScheduler(InstallmentService service){this.service=service;}
 @Scheduled(fixedDelayString="${app.installments.scan-ms:60000}",initialDelayString="${app.installments.initial-delay-ms:60000}")
 public void scan(){service.scanDue();}
}
