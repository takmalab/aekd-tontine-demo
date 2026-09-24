package cm.aekd.tontine.contribution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Définition/configuration d'une cotisation (CLAUDE.md §8), indépendante
 * d'une séance précise : c'est un modèle réutilisable (ex. "Cotisation
 * 50 000 FCFA", récurrente chaque mois). Son application à une séance
 * donnée est portée par {@link ContributionPeriod} (§13), pas par une
 * référence directe à Session — ajustement documenté par rapport au
 * schéma simplifié du §27, cohérent avec la nature récurrente des
 * cotisations connues (§9) et le texte du §13.
 */
@Entity
@Table(name = "contribution_definition")
public class ContributionDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "amount", precision = 14, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "amount_mode", nullable = false, length = 20)
    private AmountMode amountMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private ContributionFrequency frequency;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "fund_destination", nullable = false, length = 30)
    private FundDestination fundDestination;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContributionStatus status = ContributionStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContributionDefinition() {
    }

    public ContributionDefinition(String name, String description, BigDecimal amount, AmountMode amountMode,
                                   ContributionFrequency frequency, boolean mandatory, Visibility visibility,
                                   FundDestination fundDestination) {
        this.name = name;
        this.description = description;
        this.amount = amount;
        this.amountMode = amountMode;
        this.frequency = frequency;
        this.mandatory = mandatory;
        this.visibility = visibility;
        this.fundDestination = fundDestination;
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

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public AmountMode getAmountMode() {
        return amountMode;
    }

    public void setAmountMode(AmountMode amountMode) {
        this.amountMode = amountMode;
    }

    public ContributionFrequency getFrequency() {
        return frequency;
    }

    public void setFrequency(ContributionFrequency frequency) {
        this.frequency = frequency;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public FundDestination getFundDestination() {
        return fundDestination;
    }

    public void setFundDestination(FundDestination fundDestination) {
        this.fundDestination = fundDestination;
    }

    public ContributionStatus getStatus() {
        return status;
    }

    public void setStatus(ContributionStatus status) {
        this.status = status;
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
        if (!(o instanceof ContributionDefinition that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
