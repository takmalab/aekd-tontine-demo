package cm.aekd.tontine.contribution;

import cm.aekd.tontine.session.Session;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Application d'une {@link ContributionDefinition} à une séance donnée
 * (CLAUDE.md §13). La date limite ("dueDate") est facultative et fixée
 * librement par le trésorier : le délai exact de paiement reste une
 * décision métier à valider (docs/decisions.md §6), aucune règle de calcul
 * automatique n'est appliquée ici.
 */
@Entity
@Table(name = "contribution_period")
public class ContributionPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_definition_id", nullable = false)
    private ContributionDefinition contributionDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContributionPeriod() {
    }

    public ContributionPeriod(ContributionDefinition contributionDefinition, Session session, LocalDate dueDate) {
        this.contributionDefinition = contributionDefinition;
        this.session = session;
        this.dueDate = dueDate;
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

    public Session getSession() {
        return session;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
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
        if (!(o instanceof ContributionPeriod that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
