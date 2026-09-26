package cm.aekd.tontine.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Politique de prêt configurable (CLAUDE.md §20). Chaque contrainte est
 * facultative : si elle n'est pas définie par l'administrateur, elle
 * n'est simplement pas vérifiée à la demande de prêt — aucune valeur par
 * défaut n'est inventée (docs/decisions.md §11-17, toutes "À VALIDER").
 * Le taux d'intérêt est stocké pour configuration mais n'intervient dans
 * aucun calcul de remboursement : son mode de calcul exact reste "À
 * VALIDER" (§14).
 */
@Entity
@Table(name = "loan_policy")
public class LoanPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "min_amount", precision = 14, scale = 0)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 14, scale = 0)
    private BigDecimal maxAmount;

    @Column(name = "max_duration_months")
    private Integer maxDurationMonths;

    @Column(name = "interest_rate", precision = 6, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "min_savings_required", precision = 14, scale = 0)
    private BigDecimal minSavingsRequired;

    @Column(name = "min_seniority_months")
    private Integer minSeniorityMonths;

    @Column(name = "max_active_loans")
    private Integer maxActiveLoans;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LoanPolicy() {
    }

    public LoanPolicy(String name, String description, BigDecimal minAmount, BigDecimal maxAmount,
                       Integer maxDurationMonths, BigDecimal interestRate, BigDecimal minSavingsRequired,
                       Integer minSeniorityMonths, Integer maxActiveLoans) {
        this.name = name;
        this.description = description;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.maxDurationMonths = maxDurationMonths;
        this.interestRate = interestRate;
        this.minSavingsRequired = minSavingsRequired;
        this.minSeniorityMonths = minSeniorityMonths;
        this.maxActiveLoans = maxActiveLoans;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public Integer getMaxDurationMonths() {
        return maxDurationMonths;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public BigDecimal getMinSavingsRequired() {
        return minSavingsRequired;
    }

    public Integer getMinSeniorityMonths() {
        return minSeniorityMonths;
    }

    public Integer getMaxActiveLoans() {
        return maxActiveLoans;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Applique une modification complète des critères (Modifier, CLAUDE.md §20).
     * Réservé par {@link LoanPolicyService#update} aux politiques non encore
     * utilisées par un prêt, pour ne jamais changer rétroactivement le sens
     * d'une évaluation déjà effectuée.
     */
    public void update(String name, String description, BigDecimal minAmount, BigDecimal maxAmount,
                        Integer maxDurationMonths, BigDecimal interestRate, BigDecimal minSavingsRequired,
                        Integer minSeniorityMonths, Integer maxActiveLoans) {
        this.name = name;
        this.description = description;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.maxDurationMonths = maxDurationMonths;
        this.interestRate = interestRate;
        this.minSavingsRequired = minSavingsRequired;
        this.minSeniorityMonths = minSeniorityMonths;
        this.maxActiveLoans = maxActiveLoans;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoanPolicy that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
