package cm.aekd.tontine.repayment;

import cm.aekd.tontine.contribution.PaymentOperator;
import cm.aekd.tontine.loan.Loan;
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
 * Remboursement d'un prêt (CLAUDE.md §23). Réutilise l'énumération
 * {@link PaymentOperator} du module contribution plutôt que d'en dupliquer
 * une identique.
 */
@Entity
@Table(name = "loan_repayment")
public class LoanRepayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 20)
    private PaymentOperator operator;

    @Column(name = "reference", nullable = false, length = 150)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LoanRepaymentStatus status = LoanRepaymentStatus.VALIDATED;

    /** Trésorier ayant validé ou rejeté le remboursement. */
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

    protected LoanRepayment() {
    }

    /** Remboursement saisi par le trésorier : VALIDATED dès sa création. */
    public LoanRepayment(Loan loan, BigDecimal amount, LocalDate paymentDate, PaymentOperator operator,
                          String reference, User recordedBy) {
        this(loan, amount, paymentDate, operator, reference, recordedBy, LoanRepaymentStatus.VALIDATED);
    }

    public LoanRepayment(Loan loan, BigDecimal amount, LocalDate paymentDate, PaymentOperator operator,
                          String reference, User recordedBy, LoanRepaymentStatus status) {
        this.loan = loan;
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.operator = operator;
        this.reference = reference;
        this.recordedBy = recordedBy;
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Loan getLoan() {
        return loan;
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

    public User getRecordedBy() {
        return recordedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public LoanRepaymentStatus getStatus() {
        return status;
    }

    public void setStatus(LoanRepaymentStatus status) {
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

    public boolean hasProof() {
        return proofStorageKey != null;
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

    public Long getProofFileSize() {
        return proofFileSize;
    }

    public void attachProof(String storageKey, String originalFilename, String contentType, long size) {
        this.proofStorageKey = storageKey;
        this.proofOriginalFilename = originalFilename;
        this.proofContentType = contentType;
        this.proofFileSize = size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoanRepayment that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
