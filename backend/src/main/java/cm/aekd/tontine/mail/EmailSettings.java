package cm.aekd.tontine.mail;

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

/** Paramètres e-mail, ligne unique (id = 1) créée par la migration V31 (decisions.md n° 56). */
@Entity
@Table(name = "email_settings")
public class EmailSettings {

    public static final short SINGLETON_ID = 1;

    /** Identifiant de la ligne unique dans le journal d'audit (entity_id est un UUID). */
    public static final UUID AUDIT_ENTITY_ID = new UUID(0, 2);

    @Id
    private Short id;

    /** false : simulation forcée, même si Brevo est configuré. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmailSettings() {
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
