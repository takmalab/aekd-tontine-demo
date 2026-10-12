package cm.aekd.tontine.auth.otp;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Paramètres de la connexion par OTP (docs/decisions.md n° 47). Valeurs
 * par défaut validées : code à 6 chiffres, valable 5 minutes, 5 essais,
 * 60 secondes avant de redemander un code, 5 demandes par heure et par
 * numéro.
 */
@Validated
@ConfigurationProperties(prefix = "app.otp")
public class OtpProperties {

    @Min(4)
    @Max(8)
    private int codeLength = 6;

    @NotNull
    private Duration ttl = Duration.ofMinutes(5);

    @Min(1)
    private int maxAttempts = 5;

    @NotNull
    private Duration resendCooldown = Duration.ofSeconds(60);

    @Min(1)
    private int maxRequestsPerHour = 5;

    public int getCodeLength() {
        return codeLength;
    }

    public void setCodeLength(int codeLength) {
        this.codeLength = codeLength;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getResendCooldown() {
        return resendCooldown;
    }

    public void setResendCooldown(Duration resendCooldown) {
        this.resendCooldown = resendCooldown;
    }

    public int getMaxRequestsPerHour() {
        return maxRequestsPerHour;
    }

    public void setMaxRequestsPerHour(int maxRequestsPerHour) {
        this.maxRequestsPerHour = maxRequestsPerHour;
    }
}
