package cm.aekd.tontine.auth.otp;

import cm.aekd.tontine.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Code OTP envoyé par SMS. Seul son hash est conservé. {@code user} est
 * vide quand le numéro ne correspond à aucun compte actif : la demande
 * compte dans les limites d'envoi, mais aucun SMS n'est envoyé.
 */
@Entity
@Table(name = "otp_challenge")
public class OtpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "phone", nullable = false, length = 13)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OtpChallenge() {
    }

    public OtpChallenge(String phone, User user, String codeHash, Instant expiresAt) {
        this.phone = phone;
        this.user = user;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public boolean isUsable(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }

    public void registerFailedAttempt() {
        this.attempts++;
    }

    public void consume() {
        this.consumedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getPhone() {
        return phone;
    }

    public User getUser() {
        return user;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OtpChallenge that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
