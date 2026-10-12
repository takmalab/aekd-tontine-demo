package cm.aekd.tontine.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = {"670123456", "6 70 12 34 56", "670-12-34-56", "+237670123456", "237670123456",
            "00237 670 12 34 56", "(+237) 670.12.34.56"})
    void normalizesCameroonianNumbersToE164(String raw) {
        assertThat(PhoneNumbers.normalizeOrNull(raw)).isEqualTo("+237670123456");
    }

    @Test
    void acceptsFixedLineNumbers() {
        assertThat(PhoneNumbers.normalizeOrNull("233 42 12 34")).isEqualTo("+237233421234");
    }

    @Test
    void blankMeansNoNumber() {
        assertThat(PhoneNumbers.normalizeOrNull(null)).isNull();
        assertThat(PhoneNumbers.normalizeOrNull("  ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"67012345", "6701234567", "570123456", "+33612345678", "abc", "+2376701234567"})
    void rejectsNumbersOutsideCameroonFormat(String raw) {
        assertThatThrownBy(() -> PhoneNumbers.normalizeOrNull(raw))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Numéro de téléphone invalide");
    }

    @Test
    void normalizeRequiresANumber() {
        assertThatThrownBy(() -> PhoneNumbers.normalize(""))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("obligatoire");
    }
}
