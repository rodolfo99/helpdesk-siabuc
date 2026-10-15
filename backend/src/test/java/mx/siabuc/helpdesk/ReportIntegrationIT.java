package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;

class ReportIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    private static final String DAY="&from=2026-01-15&to=2026-01-15";

    @Test
    void separatesCaseCreationFromAttentionPeriodAndExcludesVoidedMinutes() throws Exception {
        long institution=institution(1000,1000), outside=institution(1000,1000);
        long older=supportCase(institution,"REMOTE"), current=supportCase(institution,"EMAIL"), boundary=supportCase(institution,"EMAIL");
        long unrelated=supportCase(outside,"EMAIL");
        long email=attention(older,"Consumo vigente de un caso anterior","EMAIL",12,false);
        long remote=attention(current,"Atención anulada para informe","REMOTE",7,true);
        long excluded=attention(boundary,"Fuera del día","EMAIL",20,false);
        attention(unrelated,"No debe filtrarse a otra institución","EMAIL",60,false);
        assertStatus(admin.post("/cases/"+current+"/entries/"+remote+"/void",Map.of("reason","Corrección del cómputo")),200);
        assertStatus(admin.post("/cases/"+current+"/status",Map.of("status","CLOSED","reason","Solución confirmada")),200);
        caseTime(older,"2026-01-01T12:00:00Z");caseTime(current,"2026-01-15T06:00:00Z");caseTime(boundary,"2026-01-16T06:00:00Z");
        entryTime(email,"2026-01-15T06:00:00Z");entryTime(remote,"2026-01-16T05:59:59Z");entryTime(excluded,"2026-01-16T06:00:00Z");
        JsonNode report=success(admin.get("/reports/support?institutionId="+institution+DAY));
        assertThat(report.path("timeZone").asText()).isEqualTo("America/Mexico_City");
        assertThat(ids(report.path("cases"))).containsExactly(current);
        assertThat(ids(report.path("attentions"))).containsExactly(email,remote);
        assertThat(report.at("/totals/cases").asLong()).isEqualTo(1);
        assertThat(report.at("/totals/closedCases").asLong()).isEqualTo(1);
        assertThat(report.at("/totals/openCases").asLong()).isZero();
        assertThat(report.at("/totals/emailMinutes").asLong()).isEqualTo(12);
        assertThat(report.at("/totals/remoteMinutes").asLong()).isZero();
        assertThat(report.at("/totals/attentionEntries").asLong()).isEqualTo(1);
        assertThat(report.at("/totals/voidedEntries").asLong()).isEqualTo(1);
        assertThat(report.path("consumption")).hasSize(1);
        assertThat(report.at("/consumption/0/minutes").asLong()).isEqualTo(12);
        assertThat(report.at("/attentions/1/internal").asBoolean()).isTrue();
        assertThat(report.at("/attentions/1/voidReason").asText()).isEqualTo("Corrección del cómputo");
        JsonNode onlyRemote=success(admin.get("/reports/support?institutionId="+institution+DAY+"&channel=REMOTE"));
        assertThat(onlyRemote.path("cases")).isEmpty();
        assertThat(ids(onlyRemote.path("attentions"))).containsExactly(remote);
        assertThat(onlyRemote.at("/totals/remoteMinutes").asLong()).isZero();
        assertBalance(institution,32,968,0,1000); // Reporting never changes existing balances, including out-of-period use.
    }

    @Test
    void filtersCaseResponsibleSeparatelyFromAttentionAuthor() throws Exception {
        long institution=institution(1000,1000), caseId=supportCase(institution,"EMAIL");
        long author=success(admin.get("/auth/me")).path("id").asLong();
        Map<String,Object> other=new LinkedHashMap<>();other.put("username",unique("reportadmin"));other.put("displayName","Responsable distinto");
        other.put("password",PASSWORD);other.put("role","ADMIN");other.put("active",true);
        long responsible=success(admin.post("/users",other)).path("id").asLong();
        db.update("UPDATE support_cases SET assigned_admin_id=? WHERE id=?",responsible,caseId);
        long entry=attention(caseId,"Atendido por el autor","EMAIL",9,false);
        caseTime(caseId,"2026-01-15T12:00:00Z");entryTime(entry,"2026-01-15T12:00:00Z");
        JsonNode byAuthor=success(admin.get("/reports/support?institutionId="+institution+DAY+"&administratorId="+author));
        assertThat(byAuthor.path("cases")).isEmpty();assertThat(ids(byAuthor.path("attentions"))).containsExactly(entry);
        JsonNode byResponsible=success(admin.get("/reports/support?institutionId="+institution+DAY+"&administratorId="+responsible));
        assertThat(ids(byResponsible.path("cases"))).containsExactly(caseId);assertThat(byResponsible.path("attentions")).isEmpty();
    }

    @Test
    void restrictsAllReportFormatsToAdministratorsAndValidatesFilters() throws Exception {
        long institution=institution(60,60);Client user=institutionUser(institution);
        Map<String,Object> billing=new LinkedHashMap<>();billing.put("username",unique("reportbilling"));billing.put("displayName","Cobranza");
        billing.put("password",PASSWORD);billing.put("role","BILLING");billing.put("active",true);
        success(admin.post("/users",billing));Client collector=login(billing.get("username").toString(),PASSWORD);
        for(String suffix:new String[]{"",".xlsx",".pdf"}) {
            assertStatus(user.get("/reports/support"+suffix),403);assertStatus(collector.get("/reports/support"+suffix),403);
            assertStatus(new Client().get("/reports/support"+suffix),401);
        }
        for(String query:new String[]{"from=2026-02-30","from=2026-01-16&to=2026-01-15","from=2025-01-01&to=2026-01-02","institutionId=-1","administratorId=0","channel=FTP","from=01-15-2026"})
            assertStatus(admin.get("/reports/support?"+query),400);
        MvcResult empty=admin.get("/reports/support?institutionId="+institution+DAY);
        assertStatus(empty,200);assertThat(empty.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(json(empty).path("cases")).isEmpty();
        assertThat(json(empty).path("attentions")).isEmpty();
    }

    @Test
    void rejectsOversizedResultsInsteadOfReturningAnIncompleteReport() throws Exception {
        long institution=institution(60,60), author=success(admin.get("/auth/me")).path("id").asLong();
        db.update("""
            INSERT INTO support_cases(institution_id,title,description,channel,created_by,created_at)
            SELECT ?, 'Caso de límite '||n, 'Descripción del caso', 'EMAIL', ?, ?
            FROM generate_series(1,5001) AS n
            """,institution,author,Timestamp.from(Instant.parse("2026-01-15T12:00:00Z")));
        try {
            for(String suffix:new String[]{"",".xlsx",".pdf"}) {
                MvcResult response=admin.get("/reports/support"+suffix+"?institutionId="+institution+DAY);
                assertStatus(response,400);assertThat(json(response).path("message").asText()).contains("5000", "no se han omitido filas");
            }
        } finally { db.update("DELETE FROM support_cases WHERE institution_id=?",institution); }
    }

    @Test
    void exportsReadableXlsxAndMultipagePdfWithAccentsAndFormulaSafeText() throws Exception {
        long institution=institution(1000,1000);
        String title="=HYPERLINK(\"https://example.test\",\"Fórmula no ejecutable\")";
        String description="Instalación, catalogación y préstamos. Diagnóstico con ñ y acentos.\n".repeat(90)+"Última línea: solución completa 😀";
        long caseId=success(admin.post("/cases",Map.of("institutionId",institution,"title",title,"description",description,"channel","EMAIL"))).path("id").asLong();
        long entry=attention(caseId,"Revisión técnica de instalación","EMAIL",15,false);
        caseTime(caseId,"2026-01-15T12:00:00Z");entryTime(entry,"2026-01-15T12:00:00Z");
        String query="?institutionId="+institution+DAY;
        MvcResult excel=admin.get("/reports/support.xlsx"+query);assertStatus(excel,200);
        assertThat(excel.getResponse().getContentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(excel.getResponse().getHeader("Content-Disposition")).contains("attachment;","SIABUC-soporte-2026-01-15-2026-01-15.xlsx");
        byte[] excelBytes=excel.getResponse().getContentAsByteArray();assertThat(new String(excelBytes,0,2,StandardCharsets.US_ASCII)).isEqualTo("PK");
        try(XSSFWorkbook workbook=new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
            assertThat(workbook.getSheet("Casos").getRow(1).getCell(3).getCellType()).isEqualTo(CellType.STRING);
            assertThat(workbook.getSheet("Casos").getRow(1).getCell(3).getStringCellValue()).isEqualTo(title);
            assertThat(workbook.getSheet("Casos").getRow(1).getCell(4).getStringCellValue()).isEqualTo(description);
            assertThat(workbook.getSheet("Consumos").getRow(1).getCell(9).getNumericCellValue()).isEqualTo(15);
            assertThat(workbook.getSheet("Consumo agrupado").getRow(1).getCell(6).getNumericCellValue()).isEqualTo(15);
        }
        MvcResult pdf=admin.get("/reports/support.pdf"+query);assertStatus(pdf,200);
        assertThat(pdf.getResponse().getContentType()).isEqualTo("application/pdf");
        byte[] pdfBytes=pdf.getResponse().getContentAsByteArray();assertThat(new String(pdfBytes,0,5,StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try(PDDocument document=Loader.loadPDF(pdfBytes)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            String content=new PDFTextStripper().getText(document);
            assertThat(content).contains("Instalación, catalogación y préstamos", "Última línea: solución completa [U+1F600]", "Revisión técnica de instalación", "Correo: 15 min", "Recibido", "Página 2");
        }
        assertBalance(institution,15,985,0,1000);
    }

    private long attention(long caseId,String description,String channel,int minutes,boolean internal)throws Exception {
        assertStatus(admin.post("/cases/"+caseId+"/entries",entryPayload(description,channel,minutes,internal)),200);
        return db.queryForObject("SELECT id FROM case_entries WHERE case_id=? AND description=? ORDER BY id DESC LIMIT 1",Long.class,caseId,description);
    }
    private void caseTime(long id,String instant){db.update("UPDATE support_cases SET created_at=? WHERE id=?",Timestamp.from(Instant.parse(instant)),id);}
    private void entryTime(long id,String instant){db.update("UPDATE case_entries SET created_at=? WHERE id=?",Timestamp.from(Instant.parse(instant)),id);}
}
