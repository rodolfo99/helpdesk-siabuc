package mx.siabuc.helpdesk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/** Static text-only document from the stored emission, never from current finance rows. */
final class InstitutionStatementPdf {
    private InstitutionStatementPdf(){}
    static byte[] render(InstitutionStatementService.Issued issued)throws IOException{
        try(PDDocument document=new PDDocument();ByteArrayOutputStream output=new ByteArrayOutputStream();Text pdf=new Text(document,issued.folio())){
            var d=issued.document();PDDocumentInformation info=new PDDocumentInformation();info.setTitle("Estado de cuenta "+issued.folio());info.setAuthor("SIABUC Helpdesk");document.setDocumentInformation(info);
            pdf.line("ESTADO DE CUENTA INSTITUCIONAL",true);pdf.line(issued.folio(),true);pdf.line(d.institutionName()+" (#"+d.institutionId()+")");
            pdf.line("Fecha de corte solicitada: "+d.cutoff()+" | Zona: "+d.timeZone());pdf.line("Corte efectivo: "+at(d.cutoffAt(),d.timeZone()));pdf.line("Emisión: "+at(d.observedAt(),d.timeZone()));pdf.line("Historia temporal disponible desde: "+at(d.supportedFrom(),d.timeZone()));
            pdf.line(d.basis());pdf.line(d.limitation());if(d.legacyOpening())pdf.line("Incluye APERTURA ANTERIOR A 1.7: sus importes y saldos no equivalen a movimientos ocurridos el día de la migración.",true);
            pdf.line("IMPORTES POR MONEDA",true);for(var t:d.totals()){pdf.line(t.currency()+": contratos activos "+money(t.activeContracts())+" | pendiente contractual "+money(t.pendingContracts()),true);pdf.line("Pagos a pólizas: "+money(t.policyPayments())+"; compras directas: "+money(t.directPayments())+"; pagos vigentes totales: "+money(t.allPayments())+"; pagos anulados (excluidos): "+money(t.voidedPayments()));}
            if(d.totals().isEmpty())pdf.line("Sin pólizas ni pagos registrados al corte.");pdf.line("Las compras directas ya pagadas no generan deuda contractual. Las monedas no se convierten ni se suman entre sí; los pagos anulados no suman a ingresos ni acreditaciones.");
            pdf.line("PÓLIZAS Y APLICACIÓN DE PAGOS",true);for(var p:d.policies()){pdf.line(p.folio()+" (#"+p.id()+") | "+(p.status().equals("ACTIVE")?"Activa":"Cancelada"),true);pdf.line("Vigencia: "+p.validFrom()+" a "+p.validUntil()+"; contratado "+money(p.contracted())+" "+p.currency()+"; pagado "+money(p.paid())+"; pendiente "+money(p.pending()));pdf.line("Minutos contratados correo/remoto: "+p.emailMinutes()+" / "+p.remoteMinutes()+"; acreditados por pagos: "+p.creditedEmailMinutes()+" / "+p.creditedRemoteMinutes());}
            pdf.line("PAGOS AL CORTE",true);for(var p:d.payments()){pdf.line("Pago #"+p.id()+" | "+p.reference()+" | "+(p.voided()?"ANULADO":"Vigente"),true);pdf.line(money(p.amount())+" "+p.currency()+"; fecha declarada "+p.paidOn()+"; método "+p.method()+"; "+(p.policyId()==null?"compra directa":"póliza #"+p.policyId()));pdf.line("Minutos del movimiento correo/remoto: "+p.emailMinutes()+" / "+p.remoteMinutes()+"; estado registrado "+at(p.recordedAt(),d.timeZone())+(p.legacyOpening()?" (apertura anterior a 1.7)":""));}
            pdf.line("SALDO DE SOPORTE AL CORTE (MINUTOS)",true);var b=d.balance();pdf.line("Correo: asignados "+b.emailAllocated()+", usados "+b.emailUsed()+", disponibles "+b.emailRemaining());pdf.line("Remoto: asignados "+b.remoteAllocated()+", usados "+b.remoteUsed()+", disponibles "+b.remoteRemaining());
            pdf.line("MOVIMIENTOS DE MINUTOS DESDE LA APERTURA",true);pdf.line("Cada fila muestra cambios de presupuesto y uso; no atribuye a una causa o persona que no esté documentada. Variación disponible = cambio asignado - cambio usado. Los cambios de presupuesto pueden incluir pagos, anulaciones o ajustes administrativos.");
            for(var m:d.minuteMovements()){pdf.line(at(m.recordedAt(),d.timeZone())+" | "+type(m.type())+" | revisión #"+m.revisionId());pdf.line("Correo asignado/usado: "+signed(m.emailAllocatedDelta())+" / "+signed(m.emailUsedDelta())+"; remoto asignado/usado: "+signed(m.remoteAllocatedDelta())+" / "+signed(m.remoteUsedDelta())+". Disponibles: "+m.balance().emailRemaining()+" correo, "+m.balance().remoteRemaining()+" remoto.");}
            pdf.line("Documento administrativo no fiscal. Esta emisión conserva su estado original aunque después cambien pólizas, pagos o saldos. Para información actualizada, genere otra emisión desde Helpdesk.");pdf.close();document.save(output);return output.toByteArray();
        }
    }
    private static String type(String type){return switch(type){case "LEGACY_OPENING"->"Apertura anterior a 1.7";case "INITIAL_BALANCE"->"Saldo inicial de institución";default->"Cambio de presupuesto/uso";};}
    private static String signed(long value){return value>0?"+"+value:Long.toString(value);}
    private static String money(BigDecimal value){return value.setScale(2).toPlainString();}
    private static String at(Instant instant,String zone){return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss XXX").format(instant.atZone(ZoneId.of(zone)));}
    private static final class Text implements AutoCloseable {
        private static final float TEXT_SIZE=9.5f, TEXT_WIDTH=510;
        private final PDDocument document;
        private final String folio;
        private final PDFont regular,bold;
        private PDPageContentStream stream;
        private float y;
        Text(PDDocument document,String folio)throws IOException {
            this.document=document;this.folio=folio;
            regular=font(document,"DejaVuSans.ttf");bold=font(document,"DejaVuSans-Bold.ttf");
            page();
        }
        private static PDFont font(PDDocument document,String filename)throws IOException {
            try(var input=InstitutionStatementPdf.class.getResourceAsStream("/fonts/"+filename)) {
                if(input==null)throw new IOException("No se encontró la fuente incluida para el PDF: "+filename);
                return PDType0Font.load(document,input,true);
            }
        }
        private void page()throws IOException {
            close();
            if(document.getNumberOfPages()>=1500)throw ApiException.bad("El estado de cuenta supera 1500 páginas.");
            PDPage page=new PDPage(PDRectangle.A4);document.addPage(page);stream=new PDPageContentStream(document,page);
            write(folio+" | Estado de cuenta",42,812,bold,10);
            write("Documento administrativo no fiscal | Página "+document.getNumberOfPages(),42,25,regular,8);
            y=786;
        }
        void line(String value)throws IOException {line(value,false);}
        void line(String value,boolean heading)throws IOException {
            PDFont font=heading?bold:regular;
            if(heading)y-=5;
            StringBuilder clean=new StringBuilder();
            for(int cp:value.codePoints().toArray()) {
                if(cp=='\n'){clean.append('\n');continue;}
                if(Character.isISOControl(cp)){clean.append(' ');continue;}
                String character=new String(Character.toChars(cp));
                try{font.encode(character);clean.append(character);}
                catch(IOException|IllegalArgumentException e){clean.append("[U+").append(Integer.toHexString(cp).toUpperCase(Locale.ROOT)).append(']');}
            }
            for(String paragraph:clean.toString().split("\\n",-1))wrap(paragraph,font);
            y-=4;
        }
        private float width(String value,PDFont font)throws IOException {return font.getStringWidth(value)/1000*TEXT_SIZE;}
        private void wrap(String paragraph,PDFont font)throws IOException {
            if(paragraph.isBlank()){print("",font);return;}
            StringBuilder row=new StringBuilder();
            for(String word:paragraph.strip().split("\\s+")) {
                if(width(word,font)>TEXT_WIDTH) {
                    // Only an individual token wider than the page is split, at Unicode code-point boundaries.
                    if(row.length()>0){print(row.toString(),font);row.setLength(0);}
                    float rowWidth=0;
                    for(int cp:word.codePoints().toArray()) {
                        String character=new String(Character.toChars(cp));float characterWidth=width(character,font);
                        if(row.length()>0&&rowWidth+characterWidth>TEXT_WIDTH){print(row.toString(),font);row.setLength(0);rowWidth=0;}
                        row.append(character);rowWidth+=characterWidth;
                    }
                } else {
                    String candidate=row.length()==0?word:row+" "+word;
                    if(row.length()>0&&width(candidate,font)>TEXT_WIDTH){print(row.toString(),font);row.setLength(0);row.append(word);}
                    else{row.setLength(0);row.append(candidate);}
                }
            }
            if(row.length()>0)print(row.toString(),font);
        }
        private void print(String value,PDFont font)throws IOException {if(y<52)page();write(value,42,y,font,TEXT_SIZE);y-=13;}
        private void write(String value,float x,float y,PDFont font,float size)throws IOException {stream.beginText();stream.setFont(font,size);stream.newLineAtOffset(x,y);stream.showText(value);stream.endText();}
        public void close()throws IOException {if(stream!=null){stream.close();stream=null;}}
    }
}
