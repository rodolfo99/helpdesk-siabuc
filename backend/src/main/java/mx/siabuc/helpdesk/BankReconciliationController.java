package mx.siabuc.helpdesk;

import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/reconciliation")
public class BankReconciliationController {
    private final BankReconciliationService service;
    public BankReconciliationController(BankReconciliationService service){this.service=service;}
    public record MatchInput(long paymentId,String reason){}
    @GetMapping("/imports") public List<BankReconciliationService.ImportSummary> imports(){return service.imports();}
    @GetMapping("/imports/{id}") public BankReconciliationService.ImportDetail imported(@PathVariable long id){return service.importDetail(id);}
    @PostMapping(value="/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public BankReconciliationService.Preview preview(@RequestParam String account,@RequestPart("file")MultipartFile file){return service.preview(account,bytes(file));}
    @PostMapping(value="/imports",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public BankReconciliationService.ImportDetail importCsv(@RequestParam String account,@RequestParam String expectedHash,@RequestPart("file")MultipartFile file){return service.importCsv(account,file.getOriginalFilename(),bytes(file),expectedHash);}
    @GetMapping("/movements/{id}") public BankReconciliationService.Detail detail(@PathVariable long id){return service.detail(id);}
    @GetMapping("/movements/{id}/candidates") public BankReconciliationService.Candidates candidates(@PathVariable long id,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="0")int page){return service.candidates(id,q,page);}
    @PostMapping("/movements/{id}/match") public BankReconciliationService.Detail match(@PathVariable long id,@RequestBody MatchInput input){return service.match(id,input.paymentId(),input.reason());}
    @PostMapping("/movements/{id}/unmatch") public BankReconciliationService.Detail unmatch(@PathVariable long id,@RequestBody MatchInput input){return service.unmatch(id,input.paymentId(),input.reason());}
    private byte[] bytes(MultipartFile file){if(file.getSize()>BankCsv.MAX_BYTES)throw new ApiException(413,"El CSV puede ocupar como máximo 1 MiB.");try{return file.getBytes();}catch(IOException e){throw ApiException.bad("No se pudo leer el CSV.");}}
}
