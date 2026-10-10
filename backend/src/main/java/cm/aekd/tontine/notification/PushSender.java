package cm.aekd.tontine.notification;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/** Envoie un message chiffré au service push d'un navigateur (FCM, Mozilla, Apple…). */
@Component
public class PushSender {

    /** Résultat d'un envoi : {@code expired} = abonnement à supprimer (404 / 410). */
    public record Result(int status, boolean expired) {
        public boolean delivered() {
            return status >= 200 && status < 300;
        }
    }

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final VapidKeyProvider keyProvider;

    public PushSender(VapidKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    public Result send(PushSubscription subscription, byte[] payload) throws IOException, InterruptedException {
        byte[] body = WebPushCrypto.encrypt(payload, subscription.getP256dh(), subscription.getAuth());
        String authorization = WebPushCrypto.vapidAuthorization(subscription.getEndpoint(), keyProvider.keys(),
                keyProvider.subject(), Instant.now().plus(Duration.ofHours(12)));

        HttpRequest request = HttpRequest.newBuilder(URI.create(subscription.getEndpoint()))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", authorization)
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                // Conservé 24 h par le service push si l'appareil est hors ligne.
                .header("TTL", "86400")
                .header("Urgency", "normal")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        int status = response.statusCode();
        return new Result(status, status == 404 || status == 410);
    }
}
