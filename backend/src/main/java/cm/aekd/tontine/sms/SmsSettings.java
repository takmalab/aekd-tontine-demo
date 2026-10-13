package cm.aekd.tontine.sms;

import cm.aekd.tontine.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Ligne unique (id = 1) créée par la migration V20. */
@Entity
@Table(name = "sms_settings")
public class SmsSettings {

    public static final short SINGLETON_ID = 1;

    /** Identifiant de la ligne unique dans le journal d'audit (entity_id est un UUID). */
    public static final UUID AUDIT_ENTITY_ID = new UUID(0, SINGLETON_ID);

    @Id
    private Short id;

    /** Fournisseur choisi par un administrateur ; null = celui de la configuration. */
    @Column(name = "active_provider", length = 30)
    private String activeProvider;

    /** En cas d'échec du fournisseur actif, essayer les autres fournisseurs réels. */
    @Column(name = "fallback_enabled", nullable = false)
    private boolean fallbackEnabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SmsSettings() {
    }

    @PrePersist
    @PreUpdate
    protected void touch() {
        this.updatedAt = Instant.now();
    }

    public String getActiveProvider() {
        return activeProvider;
    }

    public void setActiveProvider(String activeProvider) {
        this.activeProvider = activeProvider;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public User getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(User updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
