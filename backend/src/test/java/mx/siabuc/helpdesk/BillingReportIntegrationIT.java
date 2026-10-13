package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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

class BillingReportIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    private static final String DAY="&from=2026-01-15&to=2026-01-15";

    @Test
    void filtersByPaidOnAndTotalsExactlyByCurrencyInstitutionAndMethod()throws Exception{
        long institution=institution(0,0),foreign=institution(0,0);
        long cash=payment(institution,null,"100.10","CASH","2026-01-15",60,0,"Primer cobro");
        long deposit=payment(institution,null,"200.20","DEPOSIT","2026-01-15",0,20,"Segundo cobro");
        long cancelled=payment(institution,null,"30.30","CHECK","2026-01-15",5,0,"Anulado para informe");
        success(admin.post("/payments/"+cancelled+"/void",Map.of("reason","Corrección de referencia")));
        long legacy=legacy(institution,"USD","12.34","2026-01-15");
        payment(institution,null,"40.00","TRANSFER","2026-01-14",1,0,"Fuera por fecha de pago");
        payment(institution,null,"50.00","DEBIT_CARD","2026-01-16",1,0,"Posterior al periodo");
        payment(foreign,null,"900.00","CASH","2026-01-15",1,0,"Otra institución");
        Client billing=billing();String path="/reports/billing?institutionId="+institution+DAY;
        JsonNode report=success(billing.get(path));
        assertThat(ids(report.path("payments"))).containsExactly(cash,deposit,cancelled,legacy);
        assertThat(report.path("totals")).hasSize(2);
        JsonNode mxn=currency(report.path("totals"),"MXN"),usd=currency(report.path("totals"),"USD");
        assertThat(mxn.path("activeAmount").decimalValue()).isEqualByComparingTo("300.30");
        assertThat(mxn.path("voidedAmount").decimalValue()).isEqualByComparingTo("30.30");
        assertThat(mxn.path("activeCount").asInt()).isEqualTo(2);
        assertThat(mxn.path("emailMinutes").asLong()).isEqualTo(60);assertThat(mxn.path("remoteMinutes").asLong()).isEqualTo(20);
        assertThat(usd.path("activeAmount").decimalValue()).isEqualByComparingTo("12.34");
        assertThat(report.path("institutionTotals")).hasSize(2);
        assertThat(currency(report.path("institutionTotals"),"MXN").path("activeAmount").decimalValue()).isEqualByComparingTo("300.30");
        assertThat(report.path("groups")).hasSize(4);
        JsonNode filtered=success(billing.get(path+"&method=DEPOSIT&currency=MXN&status=ACTIVE"));
        assertThat(ids(filtered.path("payments"))).containsExactly(deposit);
        assertThat(currency(filtered.path("totals"),"MXN").path("activeAmount").decimalValue()).isEqualByComparingTo("200.20");
        JsonNode voids=success(billing.get(path+"&status=VOIDED"));assertThat(ids(voids.path("payments"))).containsExactly(cancelled);
        assertThat(currency(voids.path("totals"),"MXN").path("activeAmount").decimalValue()).isEqualByComparingTo("0.00");
        assertBalance(institution,0,63,0,20); // Two out-of-period purchases and one historical USD minute remain untouched.
    }

    @Test
    void separatesCurrentPolicyDebtFromPeriodReceiptsAndExcludesCancelledPolicies()throws Exception{
        long institution=institution(0,0);long policy=policy(institution,"1000.00",100,100);
        payment(institution,policy,"100.00","TRANSFER","2026-01-01",10,0,"Abono anterior");
        payment(institution,policy,"200.00","DEPOSIT","2026-01-15",0,0,"Abono sin liberar minutos");
        long cancelled=policy(institution,"900.00",90,0);
        success(admin.post("/policies/"+cancelled+"/cancel",Map.of("reason","Contratación cancelada sin cobros")));
        Client billing=billing();String path="/reports/billing?institutionId="+institution+DAY;
        JsonNode report=success(billing.get(path));assertThat(report.path("payments")).hasSize(1);
        assertThat(currency(report.path("totals"),"MXN").path("activeAmount").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(report.path("policyBalances")).hasSize(2);
        assertThat(report.at("/policyTotals/agreedMXN").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(report.at("/policyTotals/paidMXN").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(report.at("/policyTotals/pendingMXN").decimalValue()).isEqualByComparingTo("700.00");
        assertThat(report.at("/policyTotals/cancelledPolicies").asLong()).isEqualTo(1);
        JsonNode separated=success(billing.get(path+"&status=VOIDED&method=CASH&currency=USD"));
        assertThat(separated.path("payments")).isEmpty();assertThat(separated.at("/policyTotals/pendingMXN").decimalValue()).isEqualByComparingTo("700.00");
        JsonNode onlyPolicy=success(billing.get(path+"&policyId="+policy));assertThat(onlyPolicy.path("policyBalances")).hasSize(1);
        assertThat(onlyPolicy.at("/policyTotals/cancelledPolicies").asInt()).isZero();
        assertBalance(institution,0,10,0,0);
    }

    @Test
    void billingCanDownloadFinancialReportsButNotTechnicalDataAndRejectsInvalidFilters()throws Exception{
        long institution=institution(60,60);Client billing=billing(),customer=institutionUser(institution);
        success(admin.post("/cases",Map.of("institutionId",institution,"title","DIAGNÓSTICO_PRIVADO_NO_FINANCIERO","description","Error técnico reservado","channel","EMAIL")));
        for(String suffix:new String[]{"",".xlsx",".pdf"}){
            String path="/reports/billing"+suffix+"?institutionId="+institution+DAY;
            assertStatus(billing.get(path),200);assertStatus(admin.get(path),200);assertStatus(customer.get(path),403);assertStatus(new Client().get(path),401);
        }
        assertStatus(billing.get("/reports/support"),403);assertStatus(billing.get("/cases"),403);
        MvcResult financial=billing.get("/reports/billing?institutionId="+institution+DAY);
        assertThat(financial.getResponse().getContentAsString()).doesNotContain("DIAGNÓSTICO_PRIVADO_NO_FINANCIERO","Error técnico reservado");
        assertThat(financial.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        for(String query:new String[]{"institutionId=-1","policyId=0","method=BITCOIN","currency=mxn","status=PENDING","from=2026-02-30","from=2026-01-16&to=2026-01-15","from=2000-01-01&to=2026-01-01"})assertStatus(billing.get("/reports/billing?"+query),400);
    }

    @Test
    void exportsRealFormulaSafeExcelAndMultipagePdfWithSeparateFinancialSections()throws Exception{
        long institution=institution(0,0),policy=policy(institution,"1000.00",60,0);
        String reference="=HYPERLINK(\"https://example.test\",\"Póliza\")";
        String notes="Pago de póliza: depósito en pesos, descripción con ñ y acentos.\n".repeat(90)+"Última línea financiera 😀";
        Map<String,Object> data=paymentData(institution,policy,"123.45","DEPOSIT","2026-01-15",30,0,notes);data.put("reference",reference);
        success(admin.post("/payments",data));legacy(institution,"USD","10.25","2026-01-15");
        Client billing=billing();String query="?institutionId="+institution+DAY;
        MvcResult xlsx=billing.get("/reports/billing.xlsx"+query);assertStatus(xlsx,200);
        assertThat(xlsx.getResponse().getContentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(xlsx.getResponse().getHeader("Content-Disposition")).contains("attachment;","SIABUC-cobranza-2026-01-15-2026-01-15.xlsx");
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(xlsx.getResponse().getContentAsByteArray()))){
            assertThat(book.getNumberOfSheets()).isEqualTo(5);
            assertThat(book.getSheet("Pagos").getRow(1).getCell(5).getCellType()).isEqualTo(CellType.STRING);
            assertThat(book.getSheet("Pagos").getRow(1).getCell(5).getStringCellValue()).isEqualTo(reference);
            assertThat(book.getSheet("Pagos").getRow(1).getCell(12).getStringCellValue()).isEqualTo(notes);
            assertThat(book.getSheet("Pagos").getRow(1).getCell(6).getNumericCellValue()).isEqualTo(123.45);
            assertThat(book.getSheet("Totales por institución").getLastRowNum()).isEqualTo(2);
            assertThat(book.getSheet("Pólizas situación actual").getRow(1).getCell(10).getNumericCellValue()).isEqualTo(876.55);
        }
        MvcResult pdf=billing.get("/reports/billing.pdf"+query);assertStatus(pdf,200);assertThat(pdf.getResponse().getContentType()).isEqualTo("application/pdf");
        try(PDDocument doc=Loader.loadPDF(pdf.getResponse().getContentAsByteArray())){
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);String text=new PDFTextStripper().getText(doc);
            assertThat(text).contains("123.45 MXN","10.25 USD","876.55 MXN","Última línea financiera [U+1F600]","TOTALES POR INSTITUCIÓN Y MONEDA","SITUACIÓN ACTUAL DE PÓLIZAS (TODO EL HISTÓRICO)","Página 2");
        }
    }

    @Test
    void oversizedFinancialReportsFailInsteadOfSilentlyDroppingTransactions()throws Exception{
        long institution=institution(0,0),author=success(admin.get("/auth/me")).path("id").asLong();
        db.update("""
            INSERT INTO payments(request_id,institution_id,reference,amount,currency,paid_on,method,email_minutes,remote_minutes,notes,created_by)
            SELECT ('71450123-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,?,'Reporte límite '||n,1.00,'MXN',DATE '2026-01-15','CASH',1,0,'Compra histórica',?
            FROM generate_series(1,5001) AS n
            """,institution,author);
        try{for(String suffix:new String[]{"",".xlsx",".pdf"}){MvcResult response=admin.get("/reports/billing"+suffix+"?institutionId="+institution+DAY);assertStatus(response,400);assertThat(json(response).path("message").asText()).contains("5000","no se han omitido filas");}}
        finally{db.update("DELETE FROM payments WHERE institution_id=?",institution);}
    }

    private Client billing()throws Exception{String username=unique("financieros");success(admin.post("/users",Map.of("username",username,"displayName","Usuario de cobranza","password",PASSWORD,"role","BILLING","active",true)));return login(username,PASSWORD);}
    private long policy(long institution,String amount,int email,int remote)throws Exception{return success(admin.post("/policies",Map.of("institutionId",institution,"folio",unique("POL"),"description","Póliza de soporte institucional","validFrom","2026-01-01","validUntil","2026-12-31","agreedAmount",new BigDecimal(amount),"emailMinutes",email,"remoteMinutes",remote))).path("policy").path("id").asLong();}
    private Map<String,Object> paymentData(long institution,Long policy,String amount,String method,String date,int email,int remote,String notes){Map<String,Object> data=new LinkedHashMap<>();data.put("requestId",UUID.randomUUID().toString());data.put("institutionId",institution);data.put("policyId",policy);data.put("reference",unique("PAGO"));data.put("amount",new BigDecimal(amount));data.put("currency","MXN");data.put("paidOn",date);data.put("method",method);data.put("emailMinutes",email);data.put("remoteMinutes",remote);data.put("notes",notes);return data;}
    private long payment(long institution,Long policy,String amount,String method,String date,int email,int remote,String notes)throws Exception{return success(admin.post("/payments",paymentData(institution,policy,amount,method,date,email,remote,notes))).path("id").asLong();}
    private long legacy(long institution,String currency,String amount,String day)throws Exception{long author=success(admin.get("/auth/me")).path("id").asLong();long id=db.queryForObject("INSERT INTO payments(request_id,institution_id,reference,amount,currency,paid_on,method,email_minutes,remote_minutes,notes,created_by) VALUES (?,?,?,?,?,?,'CARD',1,0,'Registro histórico anterior a MXN obligatorio',?) RETURNING id",Long.class,UUID.randomUUID(),institution,unique("LEGADO"),new BigDecimal(amount),currency,Date.valueOf(day),author);db.update("UPDATE institutions SET email_allocated_minutes=email_allocated_minutes+1 WHERE id=?",institution);return id;}
    private JsonNode currency(JsonNode rows,String currency){for(JsonNode row:rows)if(row.path("currency").asText().equals(currency))return row;throw new AssertionError("Falta moneda "+currency);}
}
