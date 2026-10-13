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
@RequestMapping("/api")
public class PaymentAttachmentController {
    private final PaymentAttachmentService service;
    public PaymentAttachmentController(PaymentAttachmentService service){this.service=service;}
    @GetMapping("/payments/{paymentId}/attachments")
    public List<PaymentAttachmentService.Attachment> list(@PathVariable long paymentId){return service.list(paymentId);}
    @PostMapping(value="/payments/{paymentId}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentAttachmentService.Attachment upload(@PathVariable long paymentId,@RequestParam UUID requestId,@RequestPart("file") MultipartFile file){
        if(file.getSize()>PaymentAttachmentService.MAX_FILE_BYTES)throw new ApiException(413,"Cada comprobante puede ocupar hasta 10 MiB.");
        try{return service.upload(paymentId,file.getOriginalFilename(),file.getBytes(),requestId);}
        catch(IOException ex){throw ApiException.bad("No se pudo leer el comprobante recibido.");}
    }
    @GetMapping("/payment-attachments/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable long id){
        PaymentAttachmentService.Download result=service.download(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.metadata().contentType())).contentLength(result.bytes().length)
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(result.metadata().filename(),StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL,"no-store, private").header("X-Content-Type-Options","nosniff")
            .header("Content-Security-Policy","sandbox; default-src 'none'").body(result.bytes());
    }
}
