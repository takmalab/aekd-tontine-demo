package cm.aekd.tontine.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Abonnement fourni par le navigateur (PushSubscription.toJSON()). */
public record PushSubscriptionRequest(
        @NotBlank @Size(max = 2000) String endpoint,
        @NotBlank @Size(max = 255) String p256dh,
        @NotBlank @Size(max = 255) String auth
) {
}
