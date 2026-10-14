package cm.aekd.tontine.document;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfPatternTest {

    @Test
    void parsesAbsoluteCommandsAndImplicitRepeats() {
        // "M x y x y" : la deuxième paire est un L implicite ; C répété deux fois.
        float[] ops = PdfPattern.parse("M0 0 10 0V5H-2.5ZC1 2 3 4 5 6 7 8 9 10 11 12Q1 1 2 2");

        assertThat(ops).containsExactly(
                'M', 0, 0, 'L', 10, 0, 'V', 5, 'H', -2.5f, 'Z',
                'C', 1, 2, 3, 4, 5, 6, 'C', 7, 8, 9, 10, 11, 12,
                'Q', 1, 1, 2, 2);
    }

    @Test
    void rejectsUnsupportedCommands() {
        assertThatThrownBy(() -> PdfPattern.parse("M0 0A5 5 0 0 1 10 10"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bothPatternsDrawAsAReusableTile() throws Exception {
        for (PdfPattern pattern : PdfPattern.values()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();
            writer.getDirectContentUnder().addTemplate(
                    pattern.tile(writer.getDirectContentUnder(), 360, AekdPdf.BROWN, 0.05f), 0, 0);
            document.add(new Paragraph("Motif " + pattern));
            document.close();

            assertThat(pattern.tileHeight(360)).isEqualTo(202.5f);
            assertThat(new PdfReader(out.toByteArray()).getNumberOfPages()).isEqualTo(1);
        }
    }
}
