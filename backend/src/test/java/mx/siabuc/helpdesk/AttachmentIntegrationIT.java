package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

class AttachmentIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    @Test
    void institutionCanUploadAndDownloadItsCaseWithOriginalBytesAndHistoryButNoCrossInstitutionAccess() throws Exception {
        long institution=institution(20,20), caseId=supportCase(institution,"EMAIL");
        Client owner=institutionUser(institution), outsider=institutionUser(institution(20,20));
        byte[] bytes="Diagnóstico SIABUC: préstamo sin devolución\nCódigo 123\n".getBytes(StandardCharsets.UTF_8);
        JsonNode file=success(upload(owner,caseId,null,"../../diagnostico.log","image/png",bytes,UUID.randomUUID()));
        long id=file.path("id").asLong();
        assertThat(file.path("filename").asText()).isEqualTo("diagnostico.log");
        assertThat(file.path("contentType").asText()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(file.path("sha256").asText()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertThat(file.has("fileBytes")).isFalse();
        assertThat(ids(success(owner.get("/cases/"+caseId+"/attachments")))).containsExactly(id);
        MvcResult download=owner.get("/attachments/"+id+"/download");assertStatus(download,200);
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(bytes);
        assertThat(download.getResponse().getHeader("Content-Disposition")).startsWith("attachment;").contains("diagnostico.log");
        assertThat(download.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(download.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(success(owner.get("/cases/"+caseId)).path("entries").toString()).contains("diagnostico.log");
        assertStatus(admin.get("/attachments/"+id+"/download"),200);
        assertStatus(outsider.get("/attachments/"+id+"/download"),404);
        assertStatus(outsider.get("/cases/"+caseId+"/attachments"),404);
        assertStatus(upload(outsider,caseId,null,"x.txt","text/plain",bytes,UUID.randomUUID()),404);
        assertBalance(institution,0,20,0,20);
    }

    @Test
    void privateEntryFilesAndMetadataRemainPrivateAndCrossCaseEntryIdsAreRejected() throws Exception {
        long institution=institution(50,50), caseId=supportCase(institution,"EMAIL");
        Client owner=institutionUser(institution);
        JsonNode detail=addEntry(admin,caseId,"Diagnóstico reservado",null,0,true,200);
        long privateEntry=detail.path("entries").get(detail.path("entries").size()-1).path("id").asLong();
        byte[] text="información interna".getBytes(StandardCharsets.UTF_8);
        long privateFile=success(upload(admin,caseId,privateEntry,"interno.txt","text/plain",text,UUID.randomUUID())).path("id").asLong();
        assertThat(success(admin.get("/cases/"+caseId+"/attachments?entryId="+privateEntry))).hasSize(1);
        assertThat(success(owner.get("/cases/"+caseId+"/attachments"))).isEmpty();
        assertStatus(owner.get("/cases/"+caseId+"/attachments?entryId="+privateEntry),404);
        assertStatus(owner.get("/attachments/"+privateFile+"/download"),404);
        assertStatus(upload(owner,caseId,privateEntry,"intruso.txt","text/plain",text,UUID.randomUUID()),404);
        long second=supportCase(institution,"REMOTE");
        assertStatus(upload(admin,second,privateEntry,"otro.txt","text/plain",text,UUID.randomUUID()),404);
        assertThat(success(owner.get("/cases/"+caseId)).path("entries").toString()).doesNotContain("interno.txt");
        assertThat(db.queryForList("SELECT recipient_type FROM notification_outbox WHERE case_id=? AND event_type='ATTACHMENT_ADDED'",String.class,caseId)).containsExactly("USER");
        String billing=unique("cobranza");
        success(admin.post("/users",Map.of("username",billing,"displayName","Cobranza","password",PASSWORD,"role","BILLING","active",true)));
        Client billingUser=login(billing,PASSWORD);
        assertStatus(billingUser.get("/attachments/"+privateFile+"/download"),403);
        assertStatus(billingUser.get("/cases/"+caseId+"/attachments"),403);
    }

    @Test
    void contentValidationRejectsDisguisedHtmlExecutablesInvalidUtf8AndOversizeFiles() throws Exception {
        long caseId=supportCase(institution(10,10),"EMAIL");
        assertStatus(upload(admin,caseId,null,"fake.png","image/png","not an image".getBytes(),UUID.randomUUID()),400);
        assertStatus(upload(admin,caseId,null,"diagnostico.txt","text/plain","<html><script>alert(1)</script></html>".getBytes(),UUID.randomUUID()),400);
        assertStatus(upload(admin,caseId,null,"diagnostico.log","text/plain","#!/bin/sh\nexit 0".getBytes(),UUID.randomUUID()),400);
        assertStatus(upload(admin,caseId,null,"programa.exe","text/plain","test".getBytes(),UUID.randomUUID()),400);
        assertStatus(upload(admin,caseId,null,"archivo.zip","application/zip",new byte[]{80,75,3,4},UUID.randomUUID()),400);
        assertStatus(upload(admin,caseId,null,"invalid.txt","text/plain",new byte[]{(byte)0xc3,0x28},UUID.randomUUID()),400);
        assertStatus(upload(admin,caseId,null,"empty.txt","text/plain",new byte[0],UUID.randomUUID()),400);
        assertStatus(upload(admin,caseId,null,"large.txt","text/plain",new byte[AttachmentService.MAX_FILE_BYTES+1],UUID.randomUUID()),413);
        assertThat(success(admin.get("/cases/"+caseId+"/attachments"))).isEmpty();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE case_id=? AND event_type='ATTACHMENT_ADDED'",Long.class,caseId)).isZero();
    }

    @Test
    void repeatSubmissionIsIdempotentAndClosedCasesRetainDownloadsButRejectNewUploads() throws Exception {
        long institution=institution(20,20),caseId=supportCase(institution,"EMAIL");
        Client owner=institutionUser(institution);
        UUID request=UUID.randomUUID();byte[] bytes="detalle".getBytes(StandardCharsets.UTF_8);
        JsonNode first=success(upload(owner,caseId,null,"detalle.txt","text/plain",bytes,request));
        JsonNode duplicate=success(upload(owner,caseId,null,"detalle.txt","text/plain",bytes,request));
        assertThat(duplicate).isEqualTo(first);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM case_entries WHERE case_id=? AND description LIKE 'Archivo adjunto:%'",Long.class,caseId)).isEqualTo(1);
        assertStatus(upload(owner,caseId,null,"diferente.txt","text/plain",bytes,request),409);
        assertStatus(admin.post("/cases/"+caseId+"/status",Map.of("status","CLOSED","reason","Solución confirmada")),200);
        assertStatus(upload(owner,caseId,null,"nuevo.txt","text/plain",bytes,UUID.randomUUID()),409);
        assertStatus(owner.get("/attachments/"+first.path("id").asLong()+"/download"),200);
        assertThat(success(upload(owner,caseId,null,"detalle.txt","text/plain",bytes,request))).isEqualTo(first);
    }

    @Test
    void fileCountLimitRejectsTheFirstFileBeyondTheCaseLimit() throws Exception {
        long caseId=supportCase(institution(20,20),"EMAIL");
        long actor=success(admin.get("/auth/me")).path("id").asLong();
        db.update("""
            INSERT INTO case_attachments(case_id,uploaded_by,request_id,filename,content_type,size_bytes,sha256,file_bytes)
            SELECT ?,?,gen_random_uuid(),'existente-'||n||'.txt','text/plain',1,repeat('0',64),decode('78','hex')
            FROM generate_series(1,99) AS n
            """,caseId,actor);
        success(upload(admin,caseId,null,"numero100.txt","text/plain",new byte[]{120},UUID.randomUUID()));
        assertStatus(upload(admin,caseId,null,"numero101.txt","text/plain",new byte[]{120},UUID.randomUUID()),409);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM case_attachments WHERE case_id=?",Long.class,caseId)).isEqualTo(100);
    }

    @Test
    void pngJpegAndPdfUseDetectedMimeAndPublicEntryAttachmentsAreVisibleToClients() throws Exception {
        long institution=institution(20,20),caseId=supportCase(institution,"EMAIL");
        Client owner=institutionUser(institution);
        JsonNode detail=addEntry(owner,caseId,"Captura del problema",null,0,false,200);
        long entryId=detail.path("entries").get(detail.path("entries").size()-1).path("id").asLong();
        byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jW0YAAAAASUVORK5CYII=");
        JsonNode file=success(upload(owner,caseId,entryId,"captura.png","application/octet-stream",png,UUID.randomUUID()));
        assertThat(file.path("contentType").asText()).isEqualTo("image/png");
        assertThat(success(owner.get("/cases/"+caseId+"/attachments?entryId="+entryId))).hasSize(1);
        assertThat(success(owner.get("/cases/"+caseId+"/attachments"))).isEmpty();
        assertThat(owner.get("/attachments/"+file.path("id").asLong()+"/download").getResponse().getContentAsByteArray()).isEqualTo(png);
        byte[] pdf="%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        assertThat(success(upload(admin,caseId,null,"reporte.pdf","text/html",pdf,UUID.randomUUID())).path("contentType").asText()).isEqualTo("application/pdf");
        byte[] jpeg={(byte)255,(byte)216,(byte)255,(byte)224,0,2,(byte)255,(byte)217};
        assertThat(success(upload(admin,caseId,null,"captura.jpeg","application/octet-stream",jpeg,UUID.randomUUID())).path("contentType").asText()).isEqualTo("image/jpeg");
    }

    @Test
    void totalBytesLimitAppliesBeforeTheFileCountLimit() throws Exception {
        long caseId=supportCase(institution(20,20),"EMAIL");
        long actor=success(admin.get("/auth/me")).path("id").asLong();
        try {
            db.update("""
                INSERT INTO case_attachments(case_id,uploaded_by,request_id,filename,content_type,size_bytes,sha256,file_bytes)
                SELECT ?,?,gen_random_uuid(),'diagnostico-'||n||'.txt','text/plain',10485760,repeat('0',64),convert_to(repeat('x',10485760),'UTF8')
                FROM generate_series(1,10) AS n
                """,caseId,actor);
            assertStatus(upload(admin,caseId,null,"adicional.txt","text/plain",new byte[]{120},UUID.randomUUID()),409);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM case_attachments WHERE case_id=?",Long.class,caseId)).isEqualTo(10);
            assertThat(db.queryForObject("SELECT SUM(size_bytes) FROM case_attachments WHERE case_id=?",Long.class,caseId)).isEqualTo(AttachmentService.MAX_CASE_BYTES);
        } finally {
            // These large quota fixtures are local to this test, never user data.
            db.update("DELETE FROM case_attachments WHERE case_id=?",caseId);
        }
    }

    private MvcResult upload(Client client,long caseId,Long entryId,String name,String type,byte[] data,UUID requestId) throws Exception {
        client.refreshCsrf();
        MockHttpServletRequestBuilder builder=multipart("/api/cases/"+caseId+"/attachments")
            .file(new MockMultipartFile("file",name,type,data)).param("requestId",requestId.toString())
            .session(client.session).cookie(client.cookies.values().toArray(jakarta.servlet.http.Cookie[]::new))
            .header("X-XSRF-TOKEN",client.csrf);
        if(entryId!=null)builder.param("entryId",entryId.toString());
        return client.perform(builder);
    }
}
