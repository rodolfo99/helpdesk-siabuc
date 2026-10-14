package mx.siabuc.helpdesk;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BankCsvTest {
    private BankCsv.Parsed parse(String csv){return BankCsv.parse("Cuenta 123",csv.getBytes(StandardCharsets.UTF_8));}
    private static final String HEADER="fecha,referencia,importe,moneda\n";
    @Test void readsBomCrLfEscapedQuotesAndCommaWithoutExecutingSpreadsheetContent(){
        var result=parse("\uFEFFfecha,referencia,importe,moneda\r\n2026-10-13,\"Transferencia, \"\"SIABUC\"\"\",1250.5,MXN\r\n2026-10-14,=SUM(A1),10,USD\r\n");
        assertThat(result.rows()).hasSize(2);assertThat(result.rows().get(0).reference()).isEqualTo("Transferencia, \"SIABUC\"");assertThat(result.rows().get(0).amount()).isEqualByComparingTo("1250.50");assertThat(result.rows().get(1).reference()).isEqualTo("=SUM(A1)");
    }
    @Test void stableCanonicalHashIgnoresFileOrderBomAndNumericSpellingButNotAccountOrCurrency(){
        var first=parse(HEADER+"2026-10-13, Uno ,1,MXN\n2026-10-14,DOS,2.00,USD");
        var second=parse("\uFEFF"+HEADER+"2026-10-14,dos,2,USD\n2026-10-13,uno,1.00,MXN\n");
        assertThat(first.hash()).isEqualTo(second.hash());
        assertThat(BankCsv.parse("Otra cuenta",(HEADER+"2026-10-13,Uno,1,MXN\n2026-10-14,DOS,2.00,USD").getBytes(StandardCharsets.UTF_8)).hash()).isNotEqualTo(first.hash());
    }
    @Test void validatesEveryRecordAndRejectsDuplicateOrMalformedRows(){
        for(String body:List.of("2026-02-30,x,1,MXN","13/10/2026,x,1,MXN","2026-10-13,x,0,MXN","2026-10-13,x,-1,MXN","2026-10-13,x,1.001,MXN","2026-10-13,x,1e2,MXN","2026-10-13,x,1000000000,MXN","2026-10-13,x,1,mxn","2026-10-13,,1,MXN","2026-10-13,x,1,MXN,extra","2026-10-13,\"multi\nline\",1,MXN","2026-10-13,\"x\"text,1,MXN","2026-10-13,\"x,1,MXN","2026-10-13,x,1,MXN\n2026-10-13,X,1.00,MXN"))
            assertThatThrownBy(()->parse(HEADER+body)).as(body).isInstanceOf(ApiException.class);
    }
    @Test void rejectsInvalidEncodingHeaderAndEmptyFile(){
        assertThatThrownBy(()->BankCsv.parse("Cuenta",new byte[]{(byte)0xc3,0x28})).isInstanceOf(ApiException.class).hasMessageContaining("UTF-8");
        assertThatThrownBy(()->parse("date,reference,amount,currency\n2026-10-13,X,1,MXN")).isInstanceOf(ApiException.class).hasMessageContaining("encabezado");
        assertThatThrownBy(()->parse(HEADER)).isInstanceOf(ApiException.class);assertThatThrownBy(()->parse("")).isInstanceOf(ApiException.class);
    }
    @Test void rejectsOversizeRowCountAndControlCharacters(){
        assertThatThrownBy(()->BankCsv.parse("Cuenta",new byte[BankCsv.MAX_BYTES+1])).isInstanceOf(ApiException.class).hasMessageContaining("1 MiB");
        StringBuilder csv=new StringBuilder(HEADER);for(int i=0;i<1001;i++)csv.append("2026-10-13,R").append(i).append(",1,MXN\n");
        assertThatThrownBy(()->parse(csv.toString())).isInstanceOf(ApiException.class).hasMessageContaining("1000");
        assertThatThrownBy(()->parse(HEADER+"2026-10-13,abc\u0000def,1,MXN")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->BankCsv.parse("Cuenta\tprivada",(HEADER+"2026-10-13,x,1,MXN").getBytes(StandardCharsets.UTF_8))).isInstanceOf(ApiException.class);
    }
}
