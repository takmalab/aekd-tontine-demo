package cm.aekd.tontine.sms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/** Échanges de l'écran « Paramètres SMS » (docs/decisions.md n° 48). */
public final class SmsSettingsDtos {

    private SmsSettingsDtos() {
    }

    public record ProviderResponse(
            String id,
            String label,
            boolean configured,
            boolean simulation
    ) {
        static ProviderResponse from(SmsProvider provider) {
            return new ProviderResponse(provider.id(), provider.label(), provider.isConfigured(),
                    provider.isSimulation());
        }
    }

    public record SettingsResponse(
            /** Fournisseur réellement utilisé (choix de l'admin, sinon configuration). */
            String activeProvider,
            /** Vrai si aucun choix n'a été fait dans l'écran : la configuration s'applique. */
            boolean fromConfiguration,
            boolean fallbackEnabled,
            List<ProviderResponse> providers,
            Instant updatedAt,
            String updatedBy
    ) {
    }

    public record SettingsRequest(
            @NotBlank String activeProvider,
            @NotNull Boolean fallbackEnabled
    ) {
    }

    public record TestRequest(
            @NotBlank String phone,
            /** Fournisseur à tester ; à défaut, le fournisseur actif. */
            String provider
    ) {
    }

    public record TestResponse(
            String provider,
            String phone,
            /** Code contenu dans le SMS de test, pour vérifier la réception. */
            String code,
            String message
    ) {
    }
}
