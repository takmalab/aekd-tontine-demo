package cm.aekd.tontine.contribution;

import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Déclaration de paiement (CLAUDE.md §14). Référence une
 * {@link ContributionPeriod} plutôt que directement une
 * ContributionDefinition + Session séparément : la période porte déjà
 * les deux (cf. décision documentée sur ContributionDefinition/Period).
 * Un paiement déclaré n'est pas automatiquement encaissé (statut PENDING
 * par défaut) ; seul un paiement VALIDATED doit être pris en compte
 * dans les fonds (§14, §17 — cette agrégation des fonds n'est pas encore
 * implémentée, elle fera l'objet d'une étape séparée).
 */
@Entity
@Table(name = "contribution_transaction")
public class ContributionTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_period_id", nullable = false)
    private ContributionPeriod contributionPeriod;

    @Column(name = "amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 20)
    private PaymentOperator operator;

    @Column(name = "transaction_reference", nullable = false, length = 150)
    private String transactionReference;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "observation")
    private String observation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContributionTransactionStatus status = ContributionTransactionStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by")
    private User validatedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContributionTransaction() {
    }

    public ContributionTransaction(Member member, ContributionPeriod contributionPeriod, BigDecimal amount,
                                    PaymentOperator operator, String transactionReference, LocalDate paymentDate,
                                    String observation) {
        this.member = member;
        this.contributionPeriod = contributionPeriod;
        this.amount = amount;
        this.operator = operator;
        this.transactionReference = transactionReference;
        this.paymentDate = paymentDate;
        this.observation = observation;
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

    public Member getMember() {
        return member;
    }

    public ContributionPeriod getContributionPeriod() {
        return contributionPeriod;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentOperator getOperator() {
        return operator;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public String getObservation() {
        return observation;
    }

    public ContributionTransactionStatus getStatus() {
        return status;
    }

    public void setStatus(ContributionTransactionStatus status) {
        this.status = status;
    }

    public User getValidatedBy() {
        return validatedBy;
    }

    public void setValidatedBy(User validatedBy) {
        this.validatedBy = validatedBy;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(Instant validatedAt) {
        this.validatedAt = validatedAt;
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
        if (!(o instanceof ContributionTransaction that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
