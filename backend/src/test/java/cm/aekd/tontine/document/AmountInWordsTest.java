package cm.aekd.tontine.document;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AmountInWordsTest {

    @Test
    void writesKnownAekdAmounts() {
        assertThat(AmountInWords.francsCfa(new BigDecimal(50000))).isEqualTo("cinquante mille francs CFA");
        assertThat(AmountInWords.francsCfa(new BigDecimal(10000))).isEqualTo("dix mille francs CFA");
        assertThat(AmountInWords.francsCfa(new BigDecimal(2000))).isEqualTo("deux mille francs CFA");
        assertThat(AmountInWords.francsCfa(new BigDecimal(62000))).isEqualTo("soixante-deux mille francs CFA");
        assertThat(AmountInWords.francsCfa(new BigDecimal(300000))).isEqualTo("trois cent mille francs CFA");
        assertThat(AmountInWords.francsCfa(BigDecimal.ONE)).isEqualTo("un franc CFA");
    }

    @Test
    void appliesFrenchSpellingRules() {
        assertThat(AmountInWords.of(21)).isEqualTo("vingt et un");
        assertThat(AmountInWords.of(71)).isEqualTo("soixante et onze");
        assertThat(AmountInWords.of(80)).isEqualTo("quatre-vingts");
        assertThat(AmountInWords.of(81)).isEqualTo("quatre-vingt-un");
        assertThat(AmountInWords.of(97)).isEqualTo("quatre-vingt-dix-sept");
        assertThat(AmountInWords.of(200)).isEqualTo("deux cents");
        assertThat(AmountInWords.of(80000)).isEqualTo("quatre-vingt mille");
        assertThat(AmountInWords.of(1000)).isEqualTo("mille");
        assertThat(AmountInWords.of(1_250_500)).isEqualTo("un million deux cent cinquante mille cinq cents");
        assertThat(AmountInWords.of(200_000_000)).isEqualTo("deux cents millions");
    }
}
