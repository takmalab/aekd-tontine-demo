package cm.aekd.tontine.sanction;

import cm.aekd.tontine.contribution.ContributionDefinition;
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
 * Règle de sanction configurable (CLAUDE.md §18), distincte de la
 * sanction réellement appliquée ({@link AppliedSanction}). Le seuil de
 * retard ({@code lateDaysThreshold}) est librement défini par le
 * trésorier à la création de la règle : aucun délai n'est codé en dur
 * (CLAUDE.md §19, docs/decisions.md §7-8).
 */
@Entity
@Table(name = "sanction_rule")
public class SanctionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_definition_id", nullable = false)
    private ContributionDefinition contributionDefinition;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private SanctionType type;

    @Column(name = "late_days_threshold", nullable = false)
    private int lateDaysThreshold;

    @Column(name = "monetary_amount", precision = 14, scale = 0)
    private BigDecimal monetaryAmount;

    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SanctionRule() {
    }

    public SanctionRule(ContributionDefinition contributionDefinition, SanctionType type, int lateDaysThreshold,
                         BigDecimal monetaryAmount, String description) {
        this.contributionDefinition = contributionDefinition;
        this.type = type;
        this.lateDaysThreshold = lateDaysThreshold;
        this.monetaryAmount = monetaryAmount;
        this.description = description;
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

    public ContributionDefinition getContributionDefinition() {
        return contributionDefinition;
    }

    public SanctionType getType() {
        return type;
    }

    public int getLateDaysThreshold() {
        return lateDaysThreshold;
    }

    public BigDecimal getMonetaryAmount() {
        return monetaryAmount;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
        if (!(o instanceof SanctionRule that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
