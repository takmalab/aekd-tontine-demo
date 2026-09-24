package cm.aekd.tontine.contribution;

import cm.aekd.tontine.member.Member;
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

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Membre désigné comme bénéficiaire d'une cotisation (CLAUDE.md §12).
 * Indépendant des participants : un participant n'est pas automatiquement
 * bénéficiaire, et inversement.
 */
@Entity
@Table(name = "contribution_beneficiary")
public class ContributionBeneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_definition_id", nullable = false)
    private ContributionDefinition contributionDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ContributionBeneficiary() {
    }

    public ContributionBeneficiary(ContributionDefinition contributionDefinition, Member member) {
        this.contributionDefinition = contributionDefinition;
        this.member = member;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ContributionDefinition getContributionDefinition() {
        return contributionDefinition;
    }

    public Member getMember() {
        return member;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContributionBeneficiary that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
