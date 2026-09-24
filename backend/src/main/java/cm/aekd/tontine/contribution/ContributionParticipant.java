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
 * Membre sélectionné comme participant d'une cotisation (CLAUDE.md §10).
 * Seuls les participants peuvent déclarer un paiement pour la cotisation.
 */
@Entity
@Table(name = "contribution_participant")
public class ContributionParticipant {

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

    protected ContributionParticipant() {
    }

    public ContributionParticipant(ContributionDefinition contributionDefinition, Member member) {
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
        if (!(o instanceof ContributionParticipant that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
