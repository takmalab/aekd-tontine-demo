package cm.aekd.tontine.loan;

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
import java.util.Objects;
import java.util.UUID;

/**
 * Demande de prêt et son suivi (CLAUDE.md §21-22). Le statut évolue
 * REQUESTED → APPROVED → IN_PROGRESS → REPAID, ou REQUESTED → REJECTED.
 * La transition APPROVED → IN_PROGRESS se fait au premier remboursement
 * enregistré (module repayment) : le MVP ne modélise pas de décaissement
 * séparé de l'approbation.
 */
@Entity
@Table(name = "loan")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_policy_id", nullable = false)
    private LoanPolicy loanPolicy;

    @Column(name = "requested_amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 14, scale = 0)
    private BigDecimal approvedAmount;

    @Column(name = "requested_duration_months", nullable = false)
    private int requestedDurationMonths;

    @Column(name = "approved_duration_months")
    private Integer approvedDurationMonths;

    /** Taux mensuel (%) de la politique, figé à l'approbation (decisions.md n° 14). */
    @Column(name = "interest_rate", precision = 6, scale = 2)
    private BigDecimal interestRate;

    /** Intérêts dus, calculés à l'approbation ; 0 pour les prêts approuvés avant le 2026-10-14. */
    @Column(name = "interest_amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal interestAmount = BigDecimal.ZERO;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LoanStatus status = LoanStatus.REQUESTED;

    @Column(name = "decision_date")
    private Instant decisionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_by")
    private User decisionBy;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Période du taux en mois (decisions.md n° 53), figée à l'approbation avec le taux. */
    @Column(name = "interest_period_months")
    private Integer interestPeriodMonths;

    /** Avaliste désigné par le membre (decisions.md n° 52), facultatif selon la politique. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guarantor_member_id")
    private Member guarantor;

    @Enumerated(EnumType.STRING)
    @Column(name = "guarantor_status", length = 20)
    private GuarantorStatus guarantorStatus;

    @Column(name = "guarantor_decided_at")
    private Instant guarantorDecidedAt;

    protected Loan() {
    }

    public Loan(Member member, LoanPolicy loanPolicy, BigDecimal requestedAmount, int requestedDurationMonths,
                String reason) {
        this.member = member;
        this.loanPolicy = loanPolicy;
        this.requestedAmount = requestedAmount;
        this.requestedDurationMonths = requestedDurationMonths;
        this.reason = reason;
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

    public LoanPolicy getLoanPolicy() {
        return loanPolicy;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public int getRequestedDurationMonths() {
        return requestedDurationMonths;
    }

    public Integer getApprovedDurationMonths() {
        return approvedDurationMonths;
    }

    public void setApprovedDurationMonths(Integer approvedDurationMonths) {
        this.approvedDurationMonths = approvedDurationMonths;
    }

    /**
     * Fige le taux et sa période (decisions.md n° 53) et calcule les intérêts sur
     * le capital et la durée approuvés (LoanInterest).
     */
    public void applyInterest(BigDecimal ratePercent, int periodMonths) {
        this.interestRate = ratePercent;
        this.interestPeriodMonths = periodMonths;
        this.interestAmount = LoanInterest.interest(approvedAmount, ratePercent,
                approvedDurationMonths != null ? approvedDurationMonths : 0, periodMonths);
    }

    public Integer getInterestPeriodMonths() {
        return interestPeriodMonths;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public BigDecimal getInterestAmount() {
        return interestAmount;
    }

    /** Capital + intérêts à rembourser ; null tant que le prêt n'est pas approuvé. */
    public BigDecimal getTotalDue() {
        return approvedAmount != null ? approvedAmount.add(interestAmount) : null;
    }

    public String getReason() {
        return reason;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }

    public Instant getDecisionDate() {
        return decisionDate;
    }

    public void setDecisionDate(Instant decisionDate) {
        this.decisionDate = decisionDate;
    }

    public User getDecisionBy() {
        return decisionBy;
    }

    public void setDecisionBy(User decisionBy) {
        this.decisionBy = decisionBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
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
        if (!(o instanceof Loan loan)) return false;
        return id != null && id.equals(loan.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public Member getGuarantor() {
        return guarantor;
    }

    public GuarantorStatus getGuarantorStatus() {
        return guarantorStatus;
    }

    public Instant getGuarantorDecidedAt() {
        return guarantorDecidedAt;
    }

    /** Désigne (ou remplace) l'avaliste : sa réponse est de nouveau attendue. */
    public void designateGuarantor(Member member) {
        this.guarantor = member;
        this.guarantorStatus = member == null ? null : GuarantorStatus.PENDING;
        this.guarantorDecidedAt = null;
    }

    public void guarantorAnswers(boolean accepted) {
        this.guarantorStatus = accepted ? GuarantorStatus.ACCEPTED : GuarantorStatus.DECLINED;
        this.guarantorDecidedAt = Instant.now();
    }
}
