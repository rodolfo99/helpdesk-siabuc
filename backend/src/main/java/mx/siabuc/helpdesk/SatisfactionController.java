package mx.siabuc.helpdesk;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/satisfaction")
public class SatisfactionController {
    private final SatisfactionService service;
    public SatisfactionController(SatisfactionService service){this.service=service;}
    @GetMapping("/cases/{id}") public ResponseEntity<SatisfactionService.View> view(@PathVariable long id){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store, private").body(service.view(id));}
    @PostMapping("/cases/{id}") public ResponseEntity<SatisfactionService.View> submit(@PathVariable long id,@RequestBody SatisfactionService.Input input){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store, private").body(service.submit(id,input));}
    @GetMapping("/options") public ResponseEntity<SatisfactionService.Options> options(){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store, private").body(service.options());}
    @GetMapping("/reports") public ResponseEntity<SatisfactionService.Report> report(@RequestParam(required=false)String from,@RequestParam(required=false)String to,@RequestParam(required=false)Long institutionId,@RequestParam(required=false)Long administratorId){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store, private").body(service.report(from,to,institutionId,administratorId));}
    @GetMapping("/reports.csv") public ResponseEntity<byte[]> csv(@RequestParam(required=false)String from,@RequestParam(required=false)String to,@RequestParam(required=false)Long institutionId,@RequestParam(required=false)Long administratorId){
        SatisfactionService.Report report=service.report(from,to,institutionId,administratorId);
        StringBuilder csv=new StringBuilder("\uFEFFEncuesta,Caso,Institución,Título,Puntuación,Comentario,Autor,Administrativo asignado al responder,Fecha UTC\r\n");
        for(var s:report.responses())csv.append(s.id()).append(',').append(s.caseId()).append(',').append(cell(s.institutionName())).append(',').append(cell(s.caseTitle())).append(',').append(s.score()).append(',').append(cell(s.comment())).append(',').append(cell(s.respondentName()+" ("+s.respondentUsername()+")")).append(',').append(cell(s.administratorId()==null?"Sin administrativo asignado":s.administratorName()+" ("+s.administratorUsername()+")")).append(',').append(cell(s.createdAt().toString())).append("\r\n");
        return ResponseEntity.ok().contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).header(HttpHeaders.CACHE_CONTROL,"no-store, private")
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"SIABUC-satisfaccion-"+report.filters().from()+"-"+report.filters().to()+".csv\"")
            .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox; default-src 'none'").body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static String cell(String value){String trimmed=value.stripLeading();if((!trimmed.isEmpty()&&"=+-@".indexOf(trimmed.charAt(0))>=0)||value.startsWith("\t")||value.startsWith("\r")||value.startsWith("\n"))value="'"+value;return "\""+value.replace("\"","\"\"")+"\"";}
}
