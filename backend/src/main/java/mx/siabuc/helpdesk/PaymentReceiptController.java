package mx.siabuc.helpdesk;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PaymentReceiptController {
    private final PaymentReceiptService receipts;
    public PaymentReceiptController(PaymentReceiptService receipts){this.receipts=receipts;}
    @GetMapping("/payments/{id}/receipt.pdf")
    public ResponseEntity<byte[]> staff(@PathVariable long id)throws IOException{return download(receipts.receipt(id,false));}
    @GetMapping("/client-finance/payments/{id}/receipt.pdf")
    public ResponseEntity<byte[]> client(@PathVariable long id)throws IOException{return download(receipts.receipt(id,true));}
    private ResponseEntity<byte[]> download(PaymentReceiptService.Receipt receipt)throws IOException{
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+receipt.folio()+".pdf\"")
            .header(HttpHeaders.CACHE_CONTROL,"no-store, private").header("X-Content-Type-Options","nosniff")
            .header("Content-Security-Policy","sandbox; default-src 'none'")
            .header("X-Receipt-Folio",receipt.folio()).body(PaymentReceiptPdf.render(receipt));
    }
}
