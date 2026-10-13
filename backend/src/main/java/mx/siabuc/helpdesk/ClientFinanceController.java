package mx.siabuc.helpdesk;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/client-finance")
public class ClientFinanceController {
    private final ClientFinanceService finance;private final PaymentRequestService requests;
    public ClientFinanceController(ClientFinanceService finance,PaymentRequestService requests){this.finance=finance;this.requests=requests;}
    @GetMapping public ClientFinanceService.Summary summary(){return finance.summary();}
    @GetMapping("/payments/{id}/attachments") public List<ClientFinanceService.Proof> proofs(@PathVariable long id){return finance.proofs(id);}
    @GetMapping("/payment-attachments/{id}/download") public ResponseEntity<byte[]> downloadProof(@PathVariable long id){var file=finance.download(id);return PaymentRequestController.file(file.metadata().filename(),file.metadata().contentType(),file.bytes());}
    @GetMapping("/payment-requests") public List<PaymentRequestService.Request> requests(){return requests.list(true);}
    @PostMapping("/payment-requests") @ResponseStatus(HttpStatus.CREATED) public PaymentRequestService.Detail create(@RequestBody PaymentRequestService.Input input){return requests.create(input,true);}
    @GetMapping("/payment-requests/{id}") public PaymentRequestService.Detail detail(@PathVariable long id){return requests.detail(id,true);}
    @GetMapping("/payment-requests/{id}/attachments") public List<PaymentRequestService.Attachment> files(@PathVariable long id){return requests.attachments(id,true);}
    @PostMapping(value="/payment-requests/{id}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public PaymentRequestService.Attachment upload(@PathVariable long id,@RequestParam UUID requestId,@RequestPart("file") MultipartFile file){return PaymentRequestController.upload(requests,id,requestId,file,true);}
    @GetMapping("/payment-requests/attachments/{id}/download") public ResponseEntity<byte[]> downloadRequest(@PathVariable long id){var file=requests.download(id,true);return PaymentRequestController.file(file.metadata().filename(),file.metadata().contentType(),file.bytes());}
}
