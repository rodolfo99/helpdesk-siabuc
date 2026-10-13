package mx.siabuc.helpdesk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

final class BillingReportExporter {
    private BillingReportExporter(){}
    static String method(String method){return switch(method){case "TRANSFER"->"Transferencia";case "DEPOSIT"->"Depósito";case "CASH"->"Efectivo";case "CREDIT_CARD"->"Tarjeta de crédito";case "DEBIT_CARD"->"Tarjeta de débito";case "CHECK"->"Cheque";case "CARD"->"Tarjeta (histórico)";default->"Otro";};}
    static String money(BigDecimal value,String currency){return value.setScale(2).toPlainString()+" "+currency;}
    private static String policy(Long id,String folio){return id==null?"Sin póliza":folio+" (#"+id+")";}
    static byte[] xlsx(BillingReportService.Report r)throws IOException{
        try(XSSFWorkbook book=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            CellStyle heading=book.createCellStyle();Font bold=book.createFont();bold.setBold(true);bold.setColor(IndexedColors.WHITE.getIndex());heading.setFont(bold);heading.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle text=book.createCellStyle();text.setWrapText(true);text.setVerticalAlignment(VerticalAlignment.TOP);
            CellStyle decimal=book.createCellStyle();decimal.cloneStyleFrom(text);decimal.setDataFormat(book.createDataFormat().getFormat("#,##0.00"));
            Sheet summary=sheet(book,"Resumen",heading,"Concepto","Valor");
            row(summary,text,decimal,"Sistema","SIABUC Helpdesk - reporte de cobranza");row(summary,text,decimal,"Generado",ReportExporter.at(r.generatedAt(),r.timeZone()));
            row(summary,text,decimal,"Periodo de pagos",r.filters().from()+" a "+r.filters().to()+" inclusive (fecha del pago, no registro)");
            row(summary,text,decimal,"Zona horaria",r.timeZone());row(summary,text,decimal,"Institución ID",r.filters().institutionId()==null?"Todas":r.filters().institutionId());
            row(summary,text,decimal,"Póliza ID",r.filters().policyId()==null?"Todas":r.filters().policyId());row(summary,text,decimal,"Método",r.filters().method()==null?"Todos":method(r.filters().method()));
            row(summary,text,decimal,"Moneda de pagos",r.filters().currency());row(summary,text,decimal,"Estado de pagos",r.filters().status());
            row(summary,text,decimal,"Criterio monetario","Importes vigentes y anulados separados por moneda; no hay conversión ni suma entre monedas.");
            row(summary,text,decimal,"Criterio de pólizas","Situación actual de todo el histórico. Solo aplican filtros institución/póliza. Fechas, método, moneda y estado de pagos no limitan este apartado. Canceladas excluidas de deuda.");
            for(var total:r.totals()){
                row(summary,text,decimal,"Cobrado vigente "+total.currency(),total.activeAmount());row(summary,text,decimal,"Importe anulado "+total.currency(),total.voidedAmount());
                row(summary,text,decimal,"Movimientos vigentes "+total.currency(),total.activeCount());row(summary,text,decimal,"Movimientos anulados "+total.currency(),total.voidedCount());
                row(summary,text,decimal,"Minutos correo vigentes "+total.currency(),total.emailMinutes());row(summary,text,decimal,"Minutos remoto vigentes "+total.currency(),total.remoteMinutes());
            }
            row(summary,text,decimal,"Pólizas activas (todo el histórico)",r.policyTotals().activePolicies());row(summary,text,decimal,"Pólizas canceladas (todo el histórico)",r.policyTotals().cancelledPolicies());
            row(summary,text,decimal,"Contratado en pólizas activas MXN",r.policyTotals().agreedMXN());row(summary,text,decimal,"Pagado en pólizas activas MXN",r.policyTotals().paidMXN());row(summary,text,decimal,"Pendiente en pólizas activas MXN",r.policyTotals().pendingMXN());
            summary.setColumnWidth(0,48*256);summary.setColumnWidth(1,95*256);
            Sheet payments=sheet(book,"Pagos",heading,"Pago","Institución ID","Institución","Póliza ID","Folio póliza","Referencia","Importe","Moneda","Fecha pago","Método","Correo min","Remoto min","Descripción","Autor","Fecha registro","Estado","Motivo anulación","Anulado por","Fecha anulación");
            for(var p:r.payments())row(payments,text,decimal,p.id(),p.institutionId(),p.institutionName(),p.policyId(),p.policyFolio(),p.reference(),p.amount(),p.currency(),p.paidOn().toString(),method(p.method()),p.emailMinutes(),p.remoteMinutes(),p.notes(),p.createdBy(),ReportExporter.at(p.createdAt(),r.timeZone()),p.voided()?"Anulado":"Vigente",p.voidReason(),p.voidedBy(),p.voidedAt()==null?"":ReportExporter.at(p.voidedAt(),r.timeZone()));
            payments.setColumnWidth(12,80*256);payments.setColumnWidth(16,60*256);
            Sheet institutions=sheet(book,"Totales por institución",heading,"Institución ID","Institución","Moneda","Movimientos vigentes","Importe vigente","Movimientos anulados","Importe anulado","Correo min vigentes","Remoto min vigentes");
            for(var g:r.institutionTotals())row(institutions,text,decimal,g.institutionId(),g.institutionName(),g.currency(),g.activeCount(),g.activeAmount(),g.voidedCount(),g.voidedAmount(),g.emailMinutes(),g.remoteMinutes());
            Sheet grouped=sheet(book,"Agrupado",heading,"Institución ID","Institución","Método","Moneda","Movimientos vigentes","Importe vigente","Movimientos anulados","Importe anulado","Correo min vigentes","Remoto min vigentes");
            for(var g:r.groups())row(grouped,text,decimal,g.institutionId(),g.institutionName(),method(g.method()),g.currency(),g.activeCount(),g.activeAmount(),g.voidedCount(),g.voidedAmount(),g.emailMinutes(),g.remoteMinutes());
            Sheet policies=sheet(book,"Pólizas situación actual",heading,"Póliza","Institución ID","Institución","Folio","Desde","Hasta","Estado","Moneda","Contratado","Pagado todo histórico","Pendiente contractual","Deuda computable","Correo contratado min","Remoto contratado min","Correo acreditado min","Remoto acreditado min");
            for(var p:r.policyBalances())row(policies,text,decimal,p.id(),p.institutionId(),p.institutionName(),p.folio(),p.validFrom().toString(),p.validUntil().toString(),p.status().equals("ACTIVE")?"Activa":"Cancelada",p.currency(),p.agreedAmount(),p.paidAmount(),p.remainingAmount(),p.status().equals("ACTIVE")?p.remainingAmount():new BigDecimal("0.00"),p.emailMinutes(),p.remoteMinutes(),p.creditedEmailMinutes(),p.creditedRemoteMinutes());
            for(Sheet s:List.of(payments,institutions,grouped,policies))s.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,s.getLastRowNum(),0,s.getRow(0).getLastCellNum()-1));
            book.getProperties().getCoreProperties().setTitle("SIABUC Helpdesk - cobranza");book.write(out);return out.toByteArray();
        }
    }
    private static Sheet sheet(XSSFWorkbook book,String name,CellStyle heading,String...columns){Sheet sheet=book.createSheet(name);Row row=sheet.createRow(0);for(int i=0;i<columns.length;i++){Cell c=row.createCell(i);c.setCellValue(columns[i]);c.setCellStyle(heading);sheet.setColumnWidth(i,25*256);}sheet.createFreezePane(0,1);return sheet;}
    private static void row(Sheet sheet,CellStyle text,CellStyle decimal,Object...values){Row row=sheet.createRow(sheet.getLastRowNum()+1);for(int i=0;i<values.length;i++){Cell cell=row.createCell(i);Object v=values[i];cell.setCellStyle(v instanceof BigDecimal?decimal:text);if(v instanceof Number n)cell.setCellValue(n.doubleValue());else cell.setCellValue(v==null?"":v.toString());}}
    static byte[] pdf(BillingReportService.Report r)throws IOException{
        try(PDDocument document=new PDDocument();ByteArrayOutputStream out=new ByteArrayOutputStream();PdfText pdf=new PdfText(document,r)){
            PDDocumentInformation info=new PDDocumentInformation();info.setTitle("SIABUC Helpdesk - reporte de cobranza");info.setAuthor("SIABUC Helpdesk");document.setDocumentInformation(info);
            pdf.line("REPORTE DE COBRANZA",true);pdf.line("Periodo de pagos: "+r.filters().from()+" a "+r.filters().to()+" inclusive. Fecha del pago; no fecha de registro.");
            pdf.line("Generado: "+ReportExporter.at(r.generatedAt(),r.timeZone())+" | Zona: "+r.timeZone());
            pdf.line("Institución: "+(r.filters().institutionId()==null?"Todas":r.filters().institutionId())+" | Póliza: "+(r.filters().policyId()==null?"Todas":r.filters().policyId())+" | Método: "+(r.filters().method()==null?"Todos":method(r.filters().method()))+" | Moneda: "+r.filters().currency()+" | Estado: "+r.filters().status());
            pdf.line("Importes vigentes y anulados separados por moneda. No se convierten ni suman monedas diferentes. Los movimientos de acreditación posterior con importe cero no incrementan el dinero cobrado.");
            pdf.line("TOTALES DEL PERIODO POR MONEDA",true);
            if(r.totals().isEmpty())pdf.line("Sin pagos con los filtros seleccionados.");
            for(var t:r.totals())pdf.line(t.currency()+": cobrado vigente "+money(t.activeAmount(),t.currency())+" ("+t.activeCount()+" movimientos); anulado "+money(t.voidedAmount(),t.currency())+" ("+t.voidedCount()+" movimientos). Correo acreditado vigente: "+t.emailMinutes()+" min; remoto: "+t.remoteMinutes()+" min.");
            pdf.line("TOTALES POR INSTITUCIÓN Y MONEDA",true);
            for(var g:r.institutionTotals())pdf.line(g.institutionName()+" (#"+g.institutionId()+") | Vigente: "+money(g.activeAmount(),g.currency())+" ("+g.activeCount()+" movimientos) | Anulado: "+money(g.voidedAmount(),g.currency())+" ("+g.voidedCount()+" movimientos) | Correo: "+g.emailMinutes()+" min | Remoto: "+g.remoteMinutes()+" min");
            pdf.line("POR INSTITUCIÓN, MÉTODO Y MONEDA",true);
            for(var g:r.groups())pdf.line(g.institutionName()+" (#"+g.institutionId()+") | "+method(g.method())+" | Vigente: "+money(g.activeAmount(),g.currency())+" ("+g.activeCount()+") | Anulado: "+money(g.voidedAmount(),g.currency())+" ("+g.voidedCount()+") | Correo: "+g.emailMinutes()+" min | Remoto: "+g.remoteMinutes()+" min");
            pdf.line("HISTORIAL DE PAGOS DEL PERIODO",true);
            for(var p:r.payments()){
                pdf.line("Pago #"+p.id()+" | Referencia: "+p.reference()+" | "+(p.voided()?"Anulado":"Vigente"),true);
                pdf.line("Institución: "+p.institutionName()+" (#"+p.institutionId()+") | Póliza: "+policy(p.policyId(),p.policyFolio()));
                pdf.line("Importe: "+money(p.amount(),p.currency())+" | Método: "+method(p.method())+" | Fecha del pago: "+p.paidOn());
                pdf.line("Correo: "+p.emailMinutes()+" min | Remoto: "+p.remoteMinutes()+" min | Autor: "+p.createdBy()+" | Registro: "+ReportExporter.at(p.createdAt(),r.timeZone()));
                pdf.line("Descripción: "+p.notes());
                if(p.voided())pdf.line("Anulación: "+p.voidReason()+" | Por: "+p.voidedBy()+" | "+(p.voidedAt()==null?"":ReportExporter.at(p.voidedAt(),r.timeZone())));
            }
            pdf.line("SITUACIÓN ACTUAL DE PÓLIZAS (TODO EL HISTÓRICO)",true);
            pdf.line("Solo aplican filtros institución/póliza. El periodo, método, moneda y estado del reporte de pagos no limitan estos saldos. Las pólizas canceladas no cuentan como deuda. No es un corte contable a la fecha final.");
            pdf.line("Activas: "+r.policyTotals().activePolicies()+" | Canceladas: "+r.policyTotals().cancelledPolicies()+" | Contratado en activas: "+money(r.policyTotals().agreedMXN(),"MXN")+" | Pagado en activas: "+money(r.policyTotals().paidMXN(),"MXN")+" | Pendiente en activas: "+money(r.policyTotals().pendingMXN(),"MXN"));
            for(var p:r.policyBalances()){
                pdf.line("Póliza "+p.folio()+" (#"+p.id()+") | "+p.institutionName()+" (#"+p.institutionId()+") | "+(p.status().equals("ACTIVE")?"Activa":"Cancelada"),true);
                pdf.line("Vigencia: "+p.validFrom()+" a "+p.validUntil()+" | Contratado: "+money(p.agreedAmount(),p.currency())+" | Pagado: "+money(p.paidAmount(),p.currency())+" | Pendiente contractual: "+money(p.remainingAmount(),p.currency())+(p.status().equals("ACTIVE")?"":" (no cuenta como deuda)"));
                pdf.line("Correo: "+p.creditedEmailMinutes()+" / "+p.emailMinutes()+" min acreditados | Remoto: "+p.creditedRemoteMinutes()+" / "+p.remoteMinutes()+" min acreditados.");
            }
            if(pdf.replacements)pdf.line("Nota: caracteres no disponibles en la fuente PDF se conservan como códigos [U+...]. Excel conserva el texto Unicode original.");
            pdf.close();document.save(out);return out.toByteArray();
        }
    }
    private static final class PdfText implements AutoCloseable {
        private final PDDocument document; private final BillingReportService.Report report;
        private final PDType1Font regular=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream; private float y; private boolean replacements;
        private static final float LEFT=42, WIDTH=PDRectangle.A4.getWidth()-84, SIZE=9.5f;
        PdfText(PDDocument document,BillingReportService.Report report)throws IOException{this.document=document;this.report=report;page();}
        private void page()throws IOException{
            close(); if(document.getNumberOfPages()>=1000)throw ApiException.bad("El PDF supera 1000 páginas. Reduzca el periodo o agregue filtros.");
            PDPage page=new PDPage(PDRectangle.A4);document.addPage(page);stream=new PDPageContentStream(document,page);
            text("SIABUC Helpdesk | Reporte de cobranza",LEFT,PDRectangle.A4.getHeight()-30,bold,9);
            text("Página "+document.getNumberOfPages()+" | "+report.filters().from()+" a "+report.filters().to(),LEFT,24,regular,8);
            y=PDRectangle.A4.getHeight()-54;
        }
        void line(String value)throws IOException{line(value,false);}
        void line(String value,boolean heading)throws IOException{
            PDType1Font font=heading?bold:regular;String clean=clean(value,font);
            if(heading)y-=5;
            for(String paragraph:clean.split("\\n",-1)) {
                StringBuilder current=new StringBuilder();float width=0;
                for(int i=0;i<paragraph.length();i++) {
                    char ch=paragraph.charAt(i);float w=font.getStringWidth(String.valueOf(ch))/1000*SIZE;
                    if(width+w>WIDTH&&current.length()>0){
                        int space=current.lastIndexOf(" ");
                        if(space>0){
                            print(current.substring(0,space),font);
                            String remainder=current.substring(space+1);current.setLength(0);current.append(remainder);
                            width=font.getStringWidth(remainder)/1000*SIZE;
                        } else {print(current.toString(),font);current.setLength(0);width=0;}
                    }
                    current.append(ch);width+=w;
                }
                print(current.toString(),font);
            }
            y-=4;
        }
        private String clean(String value,PDType1Font font){
            StringBuilder result=new StringBuilder();
            for(int cp:value.codePoints().toArray()) {
                if(cp=='\n'){result.append('\n');continue;}
                if(cp=='\r')continue;
                if(cp=='\t'){result.append("    ");continue;}
                String s=new String(Character.toChars(cp));
                try{font.encode(s);result.append(s);}catch(IllegalArgumentException|IOException e){result.append("[U+").append(Integer.toHexString(cp).toUpperCase()).append(']');replacements=true;}
            }
            return result.toString();
        }
        private void print(String line,PDType1Font font)throws IOException{if(y<48)page();text(line,LEFT,y,font,SIZE);y-=13;}
        private void text(String value,float x,float y,PDType1Font font,float size)throws IOException{stream.beginText();stream.setFont(font,size);stream.newLineAtOffset(x,y);stream.showText(value);stream.endText();}
        @Override public void close()throws IOException{if(stream!=null){stream.close();stream=null;}}
    }
}
