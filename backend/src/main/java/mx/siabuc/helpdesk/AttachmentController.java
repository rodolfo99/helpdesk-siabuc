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
public class AttachmentController {
    private final AttachmentService service;
    public AttachmentController(AttachmentService service) { this.service = service; }

    @GetMapping("/cases/{caseId}/attachments")
    public List<AttachmentService.Attachment> list(@PathVariable long caseId, @RequestParam(required=false) Long entryId) {
        return service.list(caseId, entryId);
    }
    @PostMapping(value="/cases/{caseId}/attachments", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentService.Attachment upload(@PathVariable long caseId, @RequestParam(required=false) Long entryId,
            @RequestParam UUID requestId, @RequestPart("file") MultipartFile file) {
        if (file.getSize() > AttachmentService.MAX_FILE_BYTES) throw new ApiException(413, "Cada archivo puede ocupar hasta 10 MiB.");
        try { return service.upload(caseId, entryId, file.getOriginalFilename(), file.getBytes(), requestId); }
        catch (IOException ex) { throw ApiException.bad("No se pudo leer el archivo recibido."); }
    }
    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable long id) {
        AttachmentService.Download result = service.download(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.metadata().contentType()))
            .contentLength(result.bytes().length)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(result.metadata().filename(), StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL,"no-store, private")
            .header("X-Content-Type-Options","nosniff")
            .header("Content-Security-Policy","sandbox; default-src 'none'")
            .body(result.bytes());
    }
}
