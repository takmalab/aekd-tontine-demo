package cm.aekd.tontine.auth;

import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Session longue sur l'appareil (decisions.md n° 54) : un jeton de
 * renouvellement aléatoire, valable 30 jours (réglable par le super
 * administrateur, n° 57), remplacé à chaque
 * utilisation. Un compte qui n'est plus actif ne peut plus renouveler.
 */
@Service
@Transactional
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final cm.aekd.tontine.settings.SystemSettingsService settings;

    public RefreshTokenService(RefreshTokenRepository repository, cm.aekd.tontine.settings.SystemSettingsService settings) {
        this.repository = repository;
        this.settings = settings;
    }

    /** Nouveau jeton pour l'utilisateur ; renvoie la valeur à remettre à l'appareil. */
    public String issue(User user) {
        Instant now = Instant.now();
        repository.deleteStaleForUser(user.getId(), now);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(user, hash(raw),
                now.plus(Duration.ofDays(settings.sessionDays()))));
        return raw;
    }

    /** Utilise un jeton : il est révoqué et l'utilisateur est renvoyé pour en émettre un nouveau. */
    public User consume(String raw) {
        Instant now = Instant.now();
        RefreshToken token = repository.findByTokenHash(hash(raw == null ? "" : raw))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Session expirée : reconnectez-vous"));
        token.revoke(now);
        User user = token.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ce compte n'est plus actif");
        }
        return user;
    }

    /** Déconnexion : le jeton de cet appareil ne pourra plus servir. */
    public void revoke(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(raw)).ifPresent(t -> t.revoke(Instant.now()));
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
