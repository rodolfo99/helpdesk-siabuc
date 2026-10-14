package mx.siabuc.helpdesk;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class SlaAlertScheduler {
 private final SlaAlertService service;public SlaAlertScheduler(SlaAlertService service){this.service=service;}
 @Scheduled(fixedDelayString="${app.sla-alerts.scan-ms:60000}",initialDelayString="${app.sla-alerts.initial-delay-ms:60000}")
 public void scan(){service.scanDueAlerts();}
}
