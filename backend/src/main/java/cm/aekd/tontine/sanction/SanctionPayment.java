package cm.aekd.tontine.sanction;

import cm.aekd.tontine.contribution.PaymentOperator;
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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Paiement (éventuellement partiel) d'une sanction monétaire, déclaré par le
 * membre puis validé ou rejeté par le trésorier (decisions.md n° 50). Même
 * fonctionnement qu'un versement d'épargne (SavingsDeposit).
 */
@Entity
@Table(name = "sanction_payment")
public class SanctionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_sanction_id", nullable = false)
    private AppliedSanction appliedSanction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 20)
    private PaymentOperator operator;

    @Column(name = "reference", nullable = false, length = 150)
    private String reference;

    @Column(name = "observation")
    private String observation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SanctionPaymentStatus status = SanctionPaymentStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declared_by")
    private User declaredBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by")
    private User validatedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "proof_storage_key", length = 500)
    private String proofStorageKey;

    @Column(name = "proof_original_filename", length = 255)
    private String proofOriginalFilename;

    @Column(name = "proof_content_type", length = 100)
    private String proofContentType;

    @Column(name = "proof_file_size")
    private Long proofFileSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SanctionPayment() {
    }

    public SanctionPayment(AppliedSanction appliedSanction, BigDecimal amount, LocalDate paymentDate,
                           PaymentOperator operator, String reference, String observation, User declaredBy) {
        this.appliedSanction = appliedSanction;
        this.member = appliedSanction.getMember();
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.operator = operator;
        this.reference = reference;
        this.observation = observation;
        this.declaredBy = declaredBy;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public void decide(SanctionPaymentStatus decision, User decidedBy) {
        this.status = decision;
        this.validatedBy = decidedBy;
        this.validatedAt = Instant.now();
    }

    public void attachProof(String storageKey, String originalFilename, String contentType, long size) {
        this.proofStorageKey = storageKey;
        this.proofOriginalFilename = originalFilename;
        this.proofContentType = contentType;
        this.proofFileSize = size;
    }

    public boolean hasProof() {
        return proofStorageKey != null;
    }

    public UUID getId() {
        return id;
    }

    public AppliedSanction getAppliedSanction() {
        return appliedSanction;
    }

    public Member getMember() {
        return member;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public PaymentOperator getOperator() {
        return operator;
    }

    public String getReference() {
        return reference;
    }

    public String getObservation() {
        return observation;
    }

    public SanctionPaymentStatus getStatus() {
        return status;
    }

    public User getValidatedBy() {
        return validatedBy;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public String getProofStorageKey() {
        return proofStorageKey;
    }

    public String getProofOriginalFilename() {
        return proofOriginalFilename;
    }

    public String getProofContentType() {
        return proofContentType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SanctionPayment that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
