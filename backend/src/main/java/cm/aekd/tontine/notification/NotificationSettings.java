package cm.aekd.tontine.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/** Ligne unique (id = 1) créée par la migration V17. */
@Entity
@Table(name = "notification_settings")
public class NotificationSettings {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    /** Jours avant la date limite pour rappeler les cotisations obligatoires impayées ; null = aucun rappel. */
    @Column(name = "reminder_days_before")
    private Integer reminderDaysBefore;

    /** Clé publique VAPID (point P-256 non compressé, base64url). */
    @Column(name = "vapid_public_key", length = 255)
    private String vapidPublicKey;

    /** Clé privée VAPID (PKCS#8, base64). */
    @Column(name = "vapid_private_key")
    private String vapidPrivateKey;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationSettings() {
    }

    @PrePersist
    @PreUpdate
    protected void touch() {
        this.updatedAt = Instant.now();
    }

    public Integer getReminderDaysBefore() {
        return reminderDaysBefore;
    }

    public void setReminderDaysBefore(Integer reminderDaysBefore) {
        this.reminderDaysBefore = reminderDaysBefore;
    }

    public String getVapidPublicKey() {
        return vapidPublicKey;
    }

    public String getVapidPrivateKey() {
        return vapidPrivateKey;
    }

    public void setVapidKeys(String publicKey, String privateKey) {
        this.vapidPublicKey = publicKey;
        this.vapidPrivateKey = privateKey;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
