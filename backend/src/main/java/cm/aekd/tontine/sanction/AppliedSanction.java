package cm.aekd.tontine.sanction;

import cm.aekd.tontine.contribution.ContributionPeriod;
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
 * Sanction réellement appliquée à un membre (CLAUDE.md §18), distincte
 * de la règle qui l'a justifiée. Montant/description sont recopiés
 * depuis la règle au moment de l'application (photographie figée,
 * indépendante d'une modification ultérieure de la règle).
 */
@Entity
@Table(name = "applied_sanction")
public class AppliedSanction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sanction_rule_id", nullable = false)
    private SanctionRule sanctionRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_period_id", nullable = false)
    private ContributionPeriod contributionPeriod;

    @Column(name = "amount", precision = 14, scale = 0)
    private BigDecimal amount;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppliedSanctionStatus status = AppliedSanctionStatus.APPLIED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_by", nullable = false)
    private User appliedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppliedSanction() {
    }

    public AppliedSanction(SanctionRule sanctionRule, Member member, ContributionPeriod contributionPeriod,
                            BigDecimal amount, String description, User appliedBy) {
        this.sanctionRule = sanctionRule;
        this.member = member;
        this.contributionPeriod = contributionPeriod;
        this.amount = amount;
        this.description = description;
        this.appliedBy = appliedBy;
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

    public SanctionRule getSanctionRule() {
        return sanctionRule;
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

    public String getDescription() {
        return description;
    }

    public AppliedSanctionStatus getStatus() {
        return status;
    }

    public void setStatus(AppliedSanctionStatus status) {
        this.status = status;
    }

    public User getAppliedBy() {
        return appliedBy;
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
        if (!(o instanceof AppliedSanction that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
