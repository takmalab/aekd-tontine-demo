package cm.aekd.tontine.sms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmsRouterTest {

    /** Fournisseur factice : réussit, échoue, ou n'est pas configuré. */
    static class FakeProvider implements SmsProvider {
        final String id;
        final boolean simulation;
        boolean configured = true;
        boolean failing;
        final List<String> sentTo = new ArrayList<>();

        FakeProvider(String id, boolean simulation) {
            this.id = id;
            this.simulation = simulation;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String label() {
            return id;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public boolean isSimulation() {
            return simulation;
        }

        @Override
        public void sendLoginCode(String to, String code, Duration validity, String idempotencyKey) {
            if (failing) {
                throw new SmsSendException(id + " en panne");
            }
            sentTo.add(to);
        }
    }

    private final FakeProvider simulation = new FakeProvider("log", true);
    private final FakeProvider igor = new FakeProvider("igortek", false);
    private final FakeProvider scmc = new FakeProvider("scmchost", false);
    private final SmsSettings settings = mock(SmsSettings.class);
    private final SmsProperties properties = new SmsProperties();
    private SmsRouter router;

    @BeforeEach
    void setUp() {
        SmsSettingsRepository repository = mock(SmsSettingsRepository.class);
        when(repository.getSingleton()).thenReturn(settings);
        router = new SmsRouter(List.of(simulation, igor, scmc), repository, properties);
    }

    private void choose(String provider, boolean fallback) {
        when(settings.getActiveProvider()).thenReturn(provider);
        when(settings.isFallbackEnabled()).thenReturn(fallback);
    }

    @Test
    void usesTheProviderChosenByTheAdmin() {
        choose("scmchost", true);

        router.sendLoginCode("+237600000001", "123456", Duration.ofMinutes(5), "k1");

        assertThat(scmc.sentTo).containsExactly("+237600000001");
        assertThat(igor.sentTo).isEmpty();
    }

    @Test
    void usesTheConfiguredProviderUntilTheAdminChoosesOne() {
        choose(null, true);
        properties.setProvider("igortek");

        assertThat(router.activeProvider()).isSameAs(igor);
    }

    @Test
    void unknownProviderFallsBackToSimulation() {
        choose("inconnu", true);

        assertThat(router.activeProvider()).isSameAs(simulation);
    }

    @Test
    void fallsBackToAnotherRealProviderWhenTheActiveOneFails() {
        choose("igortek", true);
        igor.failing = true;

        router.sendLoginCode("+237600000001", "123456", Duration.ofMinutes(5), "k1");

        assertThat(scmc.sentTo).containsExactly("+237600000001");
        assertThat(simulation.sentTo).as("la simulation n'est jamais un secours").isEmpty();
    }

    @Test
    void noFallbackWhenDisabled() {
        choose("igortek", false);
        igor.failing = true;

        assertThatThrownBy(() -> router.sendLoginCode("+237600000001", "123456", Duration.ofMinutes(5), "k1"))
                .isInstanceOf(SmsSendException.class)
                .hasMessageContaining("igortek en panne");
        assertThat(scmc.sentTo).isEmpty();
    }

    @Test
    void unconfiguredProvidersAreNotUsedAsFallback() {
        choose("igortek", true);
        igor.failing = true;
        scmc.configured = false;

        assertThatThrownBy(() -> router.sendLoginCode("+237600000001", "123456", Duration.ofMinutes(5), "k1"))
                .isInstanceOf(SmsSendException.class);
        assertThat(scmc.sentTo).isEmpty();
    }

    @Test
    void reportsTheLastFailureWhenEveryProviderFails() {
        choose("igortek", true);
        igor.failing = true;
        scmc.failing = true;

        assertThatThrownBy(() -> router.sendLoginCode("+237600000001", "123456", Duration.ofMinutes(5), "k1"))
                .hasMessageContaining("scmchost en panne");
    }
}
