package mx.siabuc.helpdesk;

import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class InstitutionStatementController {
    private final InstitutionStatementService service;
    public InstitutionStatementController(InstitutionStatementService service){this.service=service;}
    @GetMapping("/statements/options") public InstitutionStatementService.Options options(){return service.options(false);}
    @GetMapping("/client-finance/statements/options") public InstitutionStatementService.Options clientOptions(){return service.options(true);}
    @GetMapping("/statements") public List<InstitutionStatementService.Summary> list(@RequestParam Long institutionId){return service.list(institutionId,false);}
    @GetMapping("/client-finance/statements") public List<InstitutionStatementService.Summary> clientList(){return service.list(null,true);}
    @PostMapping("/statements") public InstitutionStatementService.Issued issue(@RequestBody InstitutionStatementService.Input input){return service.issue(input,false);}
    @PostMapping("/client-finance/statements") public InstitutionStatementService.Issued clientIssue(@RequestBody InstitutionStatementService.Input input){return service.issue(input,true);}
    @GetMapping("/statements/{id}") public InstitutionStatementService.Issued get(@PathVariable long id){return service.get(id,false);}
    @GetMapping("/client-finance/statements/{id}") public InstitutionStatementService.Issued clientGet(@PathVariable long id){return service.get(id,true);}
    @GetMapping("/statements/{id}.pdf") public ResponseEntity<byte[]> pdf(@PathVariable long id)throws IOException{return download(service.get(id,false));}
    @GetMapping("/client-finance/statements/{id}.pdf") public ResponseEntity<byte[]> clientPdf(@PathVariable long id)throws IOException{return download(service.get(id,true));}
    private ResponseEntity<byte[]> download(InstitutionStatementService.Issued statement)throws IOException{return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+statement.folio()+".pdf\"").header(HttpHeaders.CACHE_CONTROL,"no-store, private").header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox; default-src 'none'").body(InstitutionStatementPdf.render(statement));}
}
