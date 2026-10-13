package cm.aekd.tontine.sms;

import cm.aekd.tontine.sms.SmsSettingsDtos.SettingsRequest;
import cm.aekd.tontine.sms.SmsSettingsDtos.SettingsResponse;
import cm.aekd.tontine.sms.SmsSettingsDtos.TestRequest;
import cm.aekd.tontine.sms.SmsSettingsDtos.TestResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
// Clés neutralisées par application-test.yml : Igor et SCMC Host sont « non configurés ».
class SmsSettingsServiceTest {

    @Autowired
    private SmsSettingsService service;

    @Test
    void listsEveryProviderWithItsConfigurationState() {
        SettingsResponse settings = service.get();

        assertThat(settings.activeProvider()).isEqualTo("log");
        assertThat(settings.fromConfiguration()).isTrue();
        assertThat(settings.providers()).extracting(SmsSettingsDtos.ProviderResponse::id)
                .containsExactlyInAnyOrder("log", "igortek", "scmchost");
        assertThat(settings.providers()).filteredOn(p -> !p.simulation())
                .allMatch(p -> !p.configured());
    }

    @Test
    void adminCanChooseAConfiguredProvider() {
        SettingsResponse updated = service.update(new SettingsRequest("log", false));

        assertThat(updated.activeProvider()).isEqualTo("log");
        assertThat(updated.fromConfiguration()).isFalse();
        assertThat(updated.fallbackEnabled()).isFalse();
    }

    @Test
    void anUnconfiguredProviderCannotBeChosen() {
        assertThatThrownBy(() -> service.update(new SettingsRequest("scmchost", true)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("n'est pas configuré");
    }

    @Test
    void anUnknownProviderIsRejected() {
        assertThatThrownBy(() -> service.update(new SettingsRequest("whatsapp", true)))
                .hasMessageContaining("inconnu");
    }

    @Test
    void testSmsReturnsTheCodeToCheckOnThePhone() {
        TestResponse response = service.sendTest(new TestRequest("6 00 00 00 01", null));

        assertThat(response.provider()).isEqualTo("log");
        assertThat(response.phone()).isEqualTo("+237600000001");
        assertThat(response.code()).matches("\\d{6}");
        assertThat(response.message()).contains("Simulation");
    }
}
