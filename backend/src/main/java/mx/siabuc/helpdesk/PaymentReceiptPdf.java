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
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Plain-text, multipage administrative receipt; no JavaScript, HTML, URLs or external resources. */
final class PaymentReceiptPdf {
    private PaymentReceiptPdf(){}
    static byte[] render(PaymentReceiptService.Receipt receipt)throws IOException{
        try(PDDocument document=new PDDocument();ByteArrayOutputStream out=new ByteArrayOutputStream();Text pdf=new Text(document,receipt)){
            var s=receipt.snapshot();
            PDDocumentInformation information=new PDDocumentInformation();information.setTitle("Recibo administrativo "+receipt.folio());information.setAuthor("SIABUC Helpdesk");document.setDocumentInformation(information);
            pdf.line("RECIBO ADMINISTRATIVO DE SOPORTE",true);
            if(receipt.clientView())pdf.line("Versión para la institución.");
            pdf.line("Folio: "+receipt.folio(),true);
            pdf.line("Emisión original: "+at(receipt.issuedAt(),s.timeZone()));
            pdf.line("Estado consultado al descargar: "+(receipt.voided()?"ANULADO":"VIGENTE")+" | "+at(receipt.downloadedAt(),s.timeZone()));
            pdf.line("DATOS CONSERVADOS EN LA EMISIÓN",true);
            pdf.line("Institución: "+s.institutionName()+" (#"+s.institutionId()+")");
            optional(pdf,"Contacto",s.contactName());optional(pdf,"Correo de la institución",s.institutionEmail());optional(pdf,"Teléfono",s.institutionPhone());
            pdf.line("Pago / movimiento: #"+s.paymentId()+" | Referencia: "+s.reference());
            pdf.line("Póliza: "+(s.policyId()==null?"Sin póliza":s.policyFolio()+" (#"+s.policyId()+")"));
            pdf.line("Fecha del pago: "+s.paidOn()+" | Método: "+method(s.method()));
            pdf.line("Importe registrado: "+money(s.amount())+" "+s.currency()+(s.amount().signum()==0?" (sin nuevo cobro)":""),true);
            if(s.amount().signum()==0)pdf.line("Concepto del movimiento: acreditación de horas de una póliza previamente liquidada.");
            else if(s.emailMinutes()==0&&s.remoteMinutes()==0)pdf.line("Concepto del movimiento: abono monetario sin nueva acreditación de minutos.");
            else pdf.line("Concepto del movimiento: pago recibido con acreditación de soporte.");
            pdf.line("SOPORTE REGISTRADO EN ESTE MOVIMIENTO",true);
            pdf.line("Correo: "+time(s.emailMinutes())+" ("+s.emailMinutes()+" minutos)");
            pdf.line("Escritorio remoto: "+time(s.remoteMinutes())+" ("+s.remoteMinutes()+" minutos)");
            pdf.line("Los tiempos describen lo acreditado por este movimiento; no representan el saldo disponible actual de la institución.");
            if(!receipt.clientView()){
                pdf.line("RESPONSABLE DEL REGISTRO",true);
                pdf.line(s.registeredByName()+" ("+s.registeredBy()+")");
            }
            pdf.line("Registro en la plataforma: "+at(s.paymentCreatedAt(),s.timeZone()));
            pdf.line("Estado del pago al emitir por primera vez: "+(s.voidedAtIssue()?"ANULADO":"VIGENTE"));
            if(!receipt.clientView()){pdf.line("DESCRIPCIÓN DEL PAGO",true);pdf.line(s.description());}
            if(receipt.voided()){
                pdf.line("ANULACIÓN DEL REGISTRO",true);
                pdf.line("Este pago está ANULADO. Se conserva el importe y tiempo originales como antecedente; no se presenta como ingreso o acreditación vigente.");
                if(!receipt.clientView()){
                    optional(pdf,"Motivo",receipt.voidReason());
                    pdf.line("Anulado por: "+(receipt.voidedByName()==null?"":receipt.voidedByName()+" ")+"("+receipt.voidedBy()+")");
                }
                if(receipt.voidedAt()!=null)pdf.line("Fecha de anulación: "+at(receipt.voidedAt(),s.timeZone()));
                pdf.line("La anulación administrativa no constituye constancia de devolución bancaria.");
            }
            pdf.line("ALCANCE DEL RECIBO",true);
            pdf.line("Este documento es un recibo administrativo de SIABUC Helpdesk. NO ES COMPROBANTE FISCAL, CFDI NI FACTURA.");
            pdf.line("Generarlo o descargarlo no registra otro pago, no acredita minutos adicionales ni envía correos. Para mayor información, consulte la plataforma Helpdesk.");
            if(pdf.replacements)pdf.line("Representación de caracteres: los no disponibles en la fuente se conservan como códigos Unicode [U+...].");
            pdf.close();document.save(out);return out.toByteArray();
        }
    }
    private static void optional(Text text,String label,String value)throws IOException{if(value!=null&&!value.isBlank())text.line(label+": "+value);}
    private static String at(Instant time,String zone){return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss XXX").format(time.atZone(ZoneId.of(zone)));}
    private static String time(long minutes){return minutes/60+" h "+minutes%60+" min";}
    private static String money(BigDecimal value){return value.setScale(2).toPlainString();}
    private static String method(String method){return switch(method){case "TRANSFER"->"Transferencia";case "DEPOSIT"->"Depósito";case "CASH"->"Efectivo";case "CREDIT_CARD"->"Tarjeta de crédito";case "DEBIT_CARD"->"Tarjeta de débito";case "CHECK"->"Cheque";case "CARD"->"Tarjeta (registro histórico)";default->"Otro";};}
    private static final class Text implements AutoCloseable{
        private static final float LEFT=42,WIDTH=PDRectangle.A4.getWidth()-84,SIZE=10;
        private final PDDocument document;private final PaymentReceiptService.Receipt receipt;
        private final PDType1Font regular=new PDType1Font(Standard14Fonts.FontName.HELVETICA),bold=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;private float y;private boolean replacements;
        Text(PDDocument document,PaymentReceiptService.Receipt receipt)throws IOException{this.document=document;this.receipt=receipt;page();}
        private void page()throws IOException{
            close();if(document.getNumberOfPages()>=100)throw ApiException.bad("El recibo supera el límite de 100 páginas.");
            document.addPage(new PDPage(PDRectangle.A4));stream=new PDPageContentStream(document,document.getPage(document.getNumberOfPages()-1));
            stream.setNonStrokingColor(receipt.voided()?0.65f:0.08f,receipt.voided()?0.09f:0.29f,receipt.voided()?0.09f:0.36f);
            write(receipt.folio()+" | "+(receipt.voided()?"ANULADO":"VIGENTE"),LEFT,PDRectangle.A4.getHeight()-30,bold,11);
            stream.setNonStrokingColor(0f,0f,0f);
            write("Recibo administrativo, no fiscal | Página "+document.getNumberOfPages(),LEFT,25,regular,8);
            y=PDRectangle.A4.getHeight()-57;
        }
        void line(String value)throws IOException{line(value,false);}
        void line(String value,boolean heading)throws IOException{
            PDType1Font font=heading?bold:regular;String clean=clean(value,font);if(heading)y-=6;
            for(String paragraph:clean.split("\\n",-1)){
                StringBuilder current=new StringBuilder();float width=0;
                for(int i=0;i<paragraph.length();i++){
                    char ch=paragraph.charAt(i);float size=font.getStringWidth(String.valueOf(ch))/1000*SIZE;
                    if(width+size>WIDTH&&current.length()>0){int space=current.lastIndexOf(" ");if(space>0){print(current.substring(0,space),font);String remainder=current.substring(space+1);current.setLength(0);current.append(remainder);width=font.getStringWidth(remainder)/1000*SIZE;}else{print(current.toString(),font);current.setLength(0);width=0;}}
                    current.append(ch);width+=size;
                }
                print(current.toString(),font);
            }
            y-=5;
        }
        private String clean(String value,PDType1Font font){
            StringBuilder clean=new StringBuilder();for(int cp:value.codePoints().toArray()){
                if(cp=='\n'){clean.append('\n');continue;}if(cp=='\r')continue;if(cp=='\t'){clean.append("    ");continue;}
                String character=new String(Character.toChars(cp));try{font.encode(character);clean.append(character);}catch(IOException|IllegalArgumentException e){clean.append("[U+").append(Integer.toHexString(cp).toUpperCase(Locale.ROOT)).append(']');replacements=true;}
            }return clean.toString();
        }
        private void print(String value,PDType1Font font)throws IOException{if(y<52)page();write(value,LEFT,y,font,SIZE);y-=14;}
        private void write(String value,float x,float y,PDType1Font font,float size)throws IOException{stream.beginText();stream.setFont(font,size);stream.newLineAtOffset(x,y);stream.showText(value);stream.endText();}
        @Override public void close()throws IOException{if(stream!=null){stream.close();stream=null;}}
    }
}
