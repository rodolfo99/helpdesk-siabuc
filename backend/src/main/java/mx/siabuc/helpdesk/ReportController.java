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
public class ReportController {
    private final ReportService reports;
    public ReportController(ReportService reports) { this.reports = reports; }

    @GetMapping("/support")
    public ResponseEntity<ReportService.Report> support(@RequestParam(required=false) Long institutionId,
            @RequestParam(required=false) Long administratorId, @RequestParam(required=false) String channel,
            @RequestParam(required=false) String from, @RequestParam(required=false) String to) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store")
            .body(reports.support(institutionId, administratorId, channel, from, to));
    }

    @GetMapping("/support.xlsx")
    public ResponseEntity<byte[]> excel(@RequestParam(required=false) Long institutionId,
            @RequestParam(required=false) Long administratorId, @RequestParam(required=false) String channel,
            @RequestParam(required=false) String from, @RequestParam(required=false) String to) throws IOException {
        ReportService.Report report = reports.support(institutionId, administratorId, channel, from, to);
        return file(report, "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ReportExporter.xlsx(report));
    }

    @GetMapping("/support.pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam(required=false) Long institutionId,
            @RequestParam(required=false) Long administratorId, @RequestParam(required=false) String channel,
            @RequestParam(required=false) String from, @RequestParam(required=false) String to) throws IOException {
        ReportService.Report report = reports.support(institutionId, administratorId, channel, from, to);
        return file(report, "pdf", MediaType.APPLICATION_PDF_VALUE, ReportExporter.pdf(report));
    }

    private ResponseEntity<byte[]> file(ReportService.Report report, String extension, String contentType, byte[] content) {
        String name = "SIABUC-soporte-"+report.filters().from()+"-"+report.filters().to()+"."+extension;
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store")
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"")
            .header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(contentType)).body(content);
    }
}
