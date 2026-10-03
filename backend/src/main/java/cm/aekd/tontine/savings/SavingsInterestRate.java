package cm.aekd.tontine.savings;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Taux d'intérêt MENSUEL de l'épargne, en pourcentage, applicable à partir de
 * {@code effectiveFrom} (docs/decisions.md n° 39). Aucun taux par défaut.
 */
@Entity
@Table(name = "savings_interest_rate")
public class SavingsInterestRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "monthly_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal monthlyRate;

    @Column(name = "effective_from", nullable = false, unique = true)
    private LocalDate effectiveFrom;

    @Column(name = "note", length = 255)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SavingsInterestRate() {
    }

    public SavingsInterestRate(BigDecimal monthlyRate, LocalDate effectiveFrom, String note, User createdBy) {
        this.monthlyRate = monthlyRate;
        this.effectiveFrom = effectiveFrom;
        this.note = note;
        this.createdBy = createdBy;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getMonthlyRate() {
        return monthlyRate;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getNote() {
        return note;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
