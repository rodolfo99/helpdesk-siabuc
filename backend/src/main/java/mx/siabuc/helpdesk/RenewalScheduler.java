package mx.siabuc.helpdesk;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class RenewalScheduler {
 private final RenewalService service;public RenewalScheduler(RenewalService service){this.service=service;}
 @Scheduled(fixedDelayString="${app.renewals.scan-ms:60000}",initialDelayString="${app.renewals.initial-delay-ms:60000}")
 public void scan(){service.scanDuePolicies();}
}
