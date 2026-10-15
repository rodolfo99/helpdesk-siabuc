package mx.siabuc.helpdesk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Real OOXML and PDF documents; no formulas, links, scripts or remote resources. */
final class ReportExporter {
    private ReportExporter() {}
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss XXX");
    static String at(Instant instant, String zone) { return DATE.format(instant.atZone(ZoneId.of(zone))); }
    static String channel(String value) { return "REMOTE".equals(value) ? "Escritorio remoto" : "Correo"; }
    static String status(String value) { return Models.CaseStatus.valueOf(value).label(); }
    static String administrator(String value) { return value == null || value.isBlank() ? "Sin responsable" : value; }

    static byte[] xlsx(ReportService.Report report) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle heading = book.createCellStyle();
            Font bold = book.createFont(); bold.setBold(true); bold.setColor(IndexedColors.WHITE.getIndex());
            heading.setFont(bold); heading.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle text = book.createCellStyle(); text.setWrapText(true); text.setVerticalAlignment(VerticalAlignment.TOP);
            Sheet summary = sheet(book,"Resumen",List.of("Concepto","Valor"),heading);
            row(summary,text,"Sistema","SIABUC Helpdesk - reporte administrativo de soporte");
            row(summary,text,"Generado",at(report.generatedAt(),report.timeZone()));
            row(summary,text,"Zona horaria",report.timeZone());
            row(summary,text,"Desde",report.filters().from().toString()); row(summary,text,"Hasta (inclusive)",report.filters().to().toString());
            row(summary,text,"Institución",report.filters().institutionId()==null?"Todas":"ID "+report.filters().institutionId());
            row(summary,text,"Administrativo",report.filters().administratorId()==null?"Todos":"ID "+report.filters().administratorId());
            row(summary,text,"Canal",report.filters().channel()==null?"Todos":channel(report.filters().channel()));
            row(summary,text,"Criterio de casos","Casos creados en el periodo. Responsable, canal y estado actuales.");
            row(summary,text,"Criterio de consumos","Atenciones registradas en el periodo, aunque el caso sea anterior. Administrativo = autor; canal = atención.");
            row(summary,text,"Anulaciones","Se conservan en Consumos, pero no suman minutos ni atenciones vigentes. Comentarios sin consumo no se incluyen.");
            row(summary,text,"Casos",report.totals().cases()); row(summary,text,"Pendientes",report.totals().openCases());
            row(summary,text,"Resueltos",report.totals().resolvedCases());
            row(summary,text,"Cerrados",report.totals().closedCases()); row(summary,text,"Atenciones vigentes",report.totals().attentionEntries());
            row(summary,text,"Atenciones anuladas",report.totals().voidedEntries()); row(summary,text,"Minutos correo",report.totals().emailMinutes());
            row(summary,text,"Minutos remoto",report.totals().remoteMinutes());
            summary.setColumnWidth(0,30*256); summary.setColumnWidth(1,95*256);

            Sheet cases=sheet(book,"Casos",List.of("Caso","Institución ID","Institución","Título","Descripción actual","Canal principal","Estado actual","Responsable ID","Responsable actual","Creado por","Fecha creación"),heading);
            for (ReportService.CaseRow c:report.cases()) row(cases,text,c.id(),c.institutionId(),c.institutionName(),c.title(),c.description(),channel(c.channel()),status(c.status()),c.administratorId(),administrator(c.administratorName()),c.createdBy(),at(c.createdAt(),report.timeZone()));
            cases.setColumnWidth(4,80*256);
            Sheet entries=sheet(book,"Consumos",List.of("Atención","Caso","Institución ID","Institución","Título del caso","Administrativo ID","Administrativo","Usuario","Canal atención","Minutos registrados","Visibilidad","Descripción","Fecha atención","Anulada","Motivo anulación"),heading);
            for (ReportService.AttentionRow e:report.attentions()) row(entries,text,e.id(),e.caseId(),e.institutionId(),e.institutionName(),e.caseTitle(),e.administratorId(),e.administratorName(),e.username(),channel(e.channel()),e.minutes(),e.internal()?"Interna":"Pública",e.description(),at(e.createdAt(),report.timeZone()),e.voided()?"Sí":"No",e.voidReason());
            entries.setColumnWidth(11,80*256); entries.setColumnWidth(14,60*256);
            Sheet grouped=sheet(book,"Consumo agrupado",List.of("Institución ID","Institución","Administrativo ID","Administrativo","Canal","Atenciones vigentes","Minutos consumidos"),heading);
            for (ReportService.Consumption c:report.consumption()) row(grouped,text,c.institutionId(),c.institutionName(),c.administratorId(),c.administratorName(),channel(c.channel()),c.entries(),c.minutes());
            for (Sheet s:List.of(cases,entries,grouped)) s.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,s.getLastRowNum(),0,s.getRow(0).getLastCellNum()-1));
            book.getProperties().getCoreProperties().setTitle("SIABUC Helpdesk - soporte");
            book.write(out); return out.toByteArray();
        }
    }

    private static Sheet sheet(XSSFWorkbook book,String name,List<String> columns,CellStyle heading) {
        Sheet sheet=book.createSheet(name); Row row=sheet.createRow(0);
        for(int i=0;i<columns.size();i++){Cell cell=row.createCell(i);cell.setCellValue(columns.get(i));cell.setCellStyle(heading);sheet.setColumnWidth(i,26*256);}
        sheet.createFreezePane(0,1); return sheet;
    }
    private static void row(Sheet sheet,CellStyle style,Object...values) {
        Row row=sheet.createRow(sheet.getLastRowNum()+1);
        for(int i=0;i<values.length;i++) {
            Cell cell=row.createCell(i); cell.setCellStyle(style); Object value=values[i];
            if(value instanceof Number number) cell.setCellValue(number.doubleValue());
            // Explicit string cells preserve text beginning =, +, -, @ without Excel evaluating it.
            else cell.setCellValue(value==null?"":value.toString());
        }
    }

    static byte[] pdf(ReportService.Report report) throws IOException {
        try (PDDocument document=new PDDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream(); PdfText pdf=new PdfText(document,report)) {
            PDDocumentInformation info=new PDDocumentInformation();info.setTitle("SIABUC Helpdesk - reporte de soporte");info.setAuthor("SIABUC Helpdesk");document.setDocumentInformation(info);
            pdf.line("REPORTE ADMINISTRATIVO DE SOPORTE",true);
            pdf.line("Periodo: "+report.filters().from()+" a "+report.filters().to()+" (ambos inclusive). Zona: "+report.timeZone());
            pdf.line("Generado: "+at(report.generatedAt(),report.timeZone()));
            pdf.line("Institución: "+(report.filters().institutionId()==null?"Todas":"ID "+report.filters().institutionId())+" | Administrativo: "+(report.filters().administratorId()==null?"Todos":"ID "+report.filters().administratorId())+" | Canal: "+(report.filters().channel()==null?"Todos":channel(report.filters().channel())));
            pdf.line("Casos: creados en el periodo; responsable, estado y canal principal actuales. Consumos: atenciones registradas en el periodo, incluyendo casos anteriores; administrativo autor y canal de la atención. Las anuladas no suman. Se incluyen notas internas con consumo. Los comentarios sin consumo no se incluyen.");
            pdf.line("Casos: "+report.totals().cases()+" | Pendientes: "+report.totals().openCases()+" | Resueltos: "+report.totals().resolvedCases()+" | Cerrados: "+report.totals().closedCases());
            pdf.line("Atenciones vigentes: "+report.totals().attentionEntries()+" | Anuladas: "+report.totals().voidedEntries()+" | Correo: "+report.totals().emailMinutes()+" min | Remoto: "+report.totals().remoteMinutes()+" min");
            pdf.line("CONSUMO POR INSTITUCIÓN, ADMINISTRATIVO Y CANAL",true);
            if(report.consumption().isEmpty()) pdf.line("Sin consumo vigente en el periodo y filtros seleccionados.");
            for(ReportService.Consumption c:report.consumption()) pdf.line(c.institutionName()+" (#"+c.institutionId()+") | "+c.administratorName()+" (#"+c.administratorId()+") | "+channel(c.channel())+" | "+c.entries()+" atenciones | "+c.minutes()+" minutos");
            pdf.line("CASOS CREADOS EN EL PERIODO",true);
            if(report.cases().isEmpty()) pdf.line("Sin casos creados en el periodo y filtros seleccionados.");
            for(ReportService.CaseRow c:report.cases()) {
                pdf.line("Caso #"+c.id()+" - "+c.title(),true);
                pdf.line("Institución: "+c.institutionName()+" (#"+c.institutionId()+") | Responsable: "+administrator(c.administratorName())+(c.administratorId()==null?"":" (#"+c.administratorId()+")"));
                pdf.line("Estado: "+status(c.status())+" | Canal: "+channel(c.channel())+" | Creado por: "+c.createdBy()+" | "+at(c.createdAt(),report.timeZone()));
                pdf.line("Descripción actual: "+c.description());
            }
            pdf.line("ATENCIONES CON CONSUMO REGISTRADAS EN EL PERIODO",true);
            if(report.attentions().isEmpty()) pdf.line("Sin atenciones en el periodo y filtros seleccionados.");
            for(ReportService.AttentionRow e:report.attentions()) {
                pdf.line("Atención #"+e.id()+" | Caso #"+e.caseId()+" - "+e.caseTitle(),true);
                pdf.line("Institución: "+e.institutionName()+" (#"+e.institutionId()+") | Administrativo: "+e.administratorName()+" ("+e.username()+", #"+e.administratorId()+")");
                pdf.line("Fecha: "+at(e.createdAt(),report.timeZone())+" | Canal: "+channel(e.channel())+" | Minutos: "+e.minutes()+" | Visibilidad: "+(e.internal()?"Interna":"Pública")+" | Anulada: "+(e.voided()?"Sí":"No"));
                pdf.line("Descripción: "+e.description());
                if(e.voided())pdf.line("Motivo de anulación: "+e.voidReason());
            }
            if(pdf.replacements)pdf.line("Nota de representación: caracteres no disponibles en la fuente PDF se conservan como códigos [U+...]. El archivo Excel conserva el texto Unicode original.");
            pdf.close(); document.save(out); return out.toByteArray();
        }
    }

    private static final class PdfText implements AutoCloseable {
        private final PDDocument document; private final ReportService.Report report;
        private final PDType1Font regular=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream; private float y; private boolean replacements;
        private static final float LEFT=42, WIDTH=PDRectangle.A4.getWidth()-84, SIZE=9.5f;
        PdfText(PDDocument document,ReportService.Report report)throws IOException{this.document=document;this.report=report;page();}
        private void page()throws IOException{
            close(); if(document.getNumberOfPages()>=1000)throw ApiException.bad("El PDF supera 1000 páginas. Reduzca el periodo o agregue filtros.");
            PDPage page=new PDPage(PDRectangle.A4);document.addPage(page);stream=new PDPageContentStream(document,page);
            text("SIABUC Helpdesk | Soporte administrativo",LEFT,PDRectangle.A4.getHeight()-30,bold,9);
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
