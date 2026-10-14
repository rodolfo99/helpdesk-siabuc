package mx.siabuc.helpdesk;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Small bounded RFC 4180 parser. No bank or spreadsheet code is ever executed. */
final class BankCsv {
    static final int MAX_BYTES=1024*1024,MAX_ROWS=1000;
    record Row(int rowNumber,LocalDate date,String reference,String referenceKey,BigDecimal amount,String currency,String hash){}
    record Parsed(String account,String accountKey,String hash,List<Row> rows){}
    static Parsed parse(String account,byte[] bytes){
        String label=text(account,100,"Identificador de cuenta"),key=key(label);
        if(bytes==null||bytes.length==0)throw ApiException.bad("El archivo CSV está vacío.");
        if(bytes.length>MAX_BYTES)throw new ApiException(413,"El CSV puede ocupar como máximo 1 MiB.");
        String source;
        try{source=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException e){throw ApiException.bad("El CSV debe estar codificado en UTF-8; puede incluir BOM.");}
        if(source.startsWith("\uFEFF"))source=source.substring(1);
        List<List<String>> records=records(source);
        if(records.isEmpty()||!records.get(0).equals(List.of("fecha","referencia","importe","moneda")))throw ApiException.bad("El encabezado debe ser exactamente: fecha,referencia,importe,moneda.");
        if(records.size()<2)throw ApiException.bad("El CSV debe contener al menos un movimiento.");
        List<Row> rows=new ArrayList<>();HashSet<String> unique=new HashSet<>();
        for(int i=1;i<records.size();i++){
            int number=i+1;List<String> cells=records.get(i);
            if(cells.size()!=4)throw rowError(number,"se requieren cuatro columnas.");
            String rawDate=cells.get(0).trim(),ref=cells.get(1).trim(),rawAmount=cells.get(2).trim(),currency=cells.get(3).trim();
            LocalDate date;
            try{if(!rawDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new DateTimeParseException("format",rawDate,0);date=LocalDate.parse(rawDate);if(date.getYear()<1900)throw new DateTimeParseException("range",rawDate,0);}
            catch(DateTimeParseException e){throw rowError(number,"fecha inválida; use AAAA-MM-DD desde 1900.");}
            if(ref.isBlank()||ref.length()>200||ref.codePoints().anyMatch(Character::isISOControl))throw rowError(number,"la referencia debe contener de 1 a 200 caracteres y no incluir saltos de línea ni controles.");
            if(!rawAmount.matches("[0-9]{1,9}(\\.[0-9]{1,2})?"))throw rowError(number,"importe inválido; use un número positivo, punto decimal y hasta dos decimales, sin separadores de miles.");
            BigDecimal amount=new BigDecimal(rawAmount).setScale(2);
            if(amount.signum()<=0||amount.compareTo(new BigDecimal("999999999.99"))>0)throw rowError(number,"el importe debe ser mayor que cero y no exceder 999999999.99.");
            if(!currency.matches("[A-Z]{3}"))throw rowError(number,"la moneda debe tener tres letras mayúsculas, por ejemplo MXN; no se convierte entre monedas.");
            String refKey=key(ref),hash=hash(List.of(key,date.toString(),refKey,amount.toPlainString(),currency));
            if(!unique.add(hash))throw rowError(number,"movimiento duplicado dentro del archivo (misma cuenta, fecha, referencia, importe y moneda).");
            rows.add(new Row(number,date,ref,refKey,amount,currency,hash));
        }
        // File order, BOM, CRLF and numeric spelling do not create a second import.
        List<String> signature=new ArrayList<>();signature.add(key);rows.stream().map(Row::hash).sorted().forEach(signature::add);
        return new Parsed(label,key,hash(signature),List.copyOf(rows));
    }
    private static List<List<String>> records(String source){
        List<List<String>> records=new ArrayList<>();List<String> record=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false,closed=false,fieldStart=true;
        for(int i=0;i<source.length();i++){
            char ch=source.charAt(i);
            if(quoted){if(ch=='"'){if(i+1<source.length()&&source.charAt(i+1)=='"'){cell.append('"');i++;}else{quoted=false;closed=true;}}else cell.append(ch);}
            else if(ch==','){record.add(cell.toString());cell.setLength(0);closed=false;fieldStart=true;}
            else if(ch=='\r'||ch=='\n'){
                if(ch=='\r'){if(i+1>=source.length()||source.charAt(i+1)!='\n')throw ApiException.bad("CSV inválido: use saltos LF o CRLF, no CR aislado.");i++;}
                record.add(cell.toString());records.add(List.copyOf(record));record.clear();cell.setLength(0);closed=false;fieldStart=true;
                if(records.size()>MAX_ROWS+1)throw ApiException.bad("El CSV admite como máximo 1000 movimientos.");
            }else if(ch=='"'){if(!fieldStart||closed)throw ApiException.bad("CSV inválido: comilla fuera del inicio de un campo.");quoted=true;fieldStart=false;}
            else{if(closed)throw ApiException.bad("CSV inválido: hay texto después de cerrar un campo entre comillas.");cell.append(ch);fieldStart=false;}
        }
        if(quoted)throw ApiException.bad("CSV inválido: hay un campo entre comillas sin cerrar.");
        if(!record.isEmpty()||cell.length()>0||closed){record.add(cell.toString());records.add(List.copyOf(record));}
        if(records.size()>MAX_ROWS+1)throw ApiException.bad("El CSV admite como máximo 1000 movimientos.");
        return records;
    }
    static String text(String value,int max,String field){String result=value==null?"":value.trim();if(result.isBlank()||result.length()>max||result.codePoints().anyMatch(Character::isISOControl))throw ApiException.bad(field+" debe contener de 1 a "+max+" caracteres, sin controles.");return result;}
    static String key(String text){return text.trim().toLowerCase(Locale.ROOT);}
    static String hash(List<String> parts){try{MessageDigest digest=MessageDigest.getInstance("SHA-256");for(String part:parts){byte[] bytes=part.getBytes(StandardCharsets.UTF_8);digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static ApiException rowError(int record,String message){return ApiException.bad("Registro CSV "+record+": "+message);}
}
