package cm.aekd.tontine.notification;

import cm.aekd.tontine.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/** Canaux supplémentaires d'un type de notification : e-mail, SMS (decisions.md n° 58). */
@Entity
@Table(name = "notification_channel")
public class NotificationChannel {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50)
    private NotificationType type;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationChannel() {
    }

    public NotificationChannel(NotificationType type) {
        this.type = type;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public NotificationType getType() {
        return type;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public boolean isSmsEnabled() {
        return smsEnabled;
    }

    public void update(boolean emailEnabled, boolean smsEnabled, User by) {
        this.emailEnabled = emailEnabled;
        this.smsEnabled = smsEnabled;
        this.updatedBy = by;
    }
}
