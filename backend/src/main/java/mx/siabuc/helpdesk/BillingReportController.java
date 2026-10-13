package mx.siabuc.helpdesk;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class BillingReportController {
    private final BillingReportService reports;
    public BillingReportController(BillingReportService reports){this.reports=reports;}
    @GetMapping({"/billing","/billing.xlsx","/billing.pdf"})
    public ResponseEntity<?> report(@RequestParam(required=false)Long institutionId,@RequestParam(required=false)Long policyId,
        @RequestParam(required=false)String method,@RequestParam(required=false)String currency,@RequestParam(required=false)String status,
        @RequestParam(required=false)String from,@RequestParam(required=false)String to,jakarta.servlet.http.HttpServletRequest request)throws IOException{
        BillingReportService.Report report=reports.report(institutionId,policyId,method,currency,status,from,to);
        String path=request.getRequestURI();
        if(path.endsWith(".xlsx")||path.endsWith(".pdf")){
            boolean excel=path.endsWith(".xlsx");String extension=excel?"xlsx":"pdf";
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"SIABUC-cobranza-"+report.filters().from()+"-"+report.filters().to()+"."+extension+"\"")
                .contentType(MediaType.parseMediaType(excel?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"application/pdf"))
                .body(excel?BillingReportExporter.xlsx(report):BillingReportExporter.pdf(report));
        }
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").body(report);
    }
}
