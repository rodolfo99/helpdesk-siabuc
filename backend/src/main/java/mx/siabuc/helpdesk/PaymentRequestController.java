package mx.siabuc.helpdesk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/payment-requests")
public class PaymentRequestController {
    private final PaymentRequestService service;
    public PaymentRequestController(PaymentRequestService service){this.service=service;}
    public record Decision(String reason){}
    @GetMapping public List<PaymentRequestService.Request> list(){return service.list(false);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public PaymentRequestService.Detail create(@RequestBody PaymentRequestService.Input input){return service.create(input,false);}
    @GetMapping("/{id}") public PaymentRequestService.Detail detail(@PathVariable long id){return service.detail(id,false);}
    @PostMapping("/{id}/approve") public PaymentRequestService.Detail approve(@PathVariable long id,@RequestBody Decision input){return service.approve(id,input.reason());}
    @PostMapping("/{id}/reject") public PaymentRequestService.Detail reject(@PathVariable long id,@RequestBody Decision input){return service.reject(id,input.reason());}
    @GetMapping("/{id}/attachments") public List<PaymentRequestService.Attachment> files(@PathVariable long id){return service.attachments(id,false);}
    @PostMapping(value="/{id}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public PaymentRequestService.Attachment upload(@PathVariable long id,@RequestParam UUID requestId,@RequestPart("file") MultipartFile file){return upload(service,id,requestId,file,false);}
    @GetMapping("/attachments/{id}/download") public ResponseEntity<byte[]> download(@PathVariable long id){var result=service.download(id,false);return file(result.metadata().filename(),result.metadata().contentType(),result.bytes());}
    static PaymentRequestService.Attachment upload(PaymentRequestService service,long id,UUID requestId,MultipartFile file,boolean client){
        if(file.getSize()>10*1024*1024)throw new ApiException(413,"Cada comprobante puede ocupar hasta 10 MiB.");
        try{return service.upload(id,file.getOriginalFilename(),file.getBytes(),requestId,client);}catch(IOException ex){throw ApiException.bad("No se pudo leer el comprobante.");}
    }
    static ResponseEntity<byte[]> file(String filename,String type,byte[] bytes){return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).contentLength(bytes.length)
        .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename,StandardCharsets.UTF_8).build().toString())
        .header(HttpHeaders.CACHE_CONTROL,"no-store, private").header("X-Content-Type-Options","nosniff")
        .header("Content-Security-Policy","sandbox; default-src 'none'").body(bytes);}
}
