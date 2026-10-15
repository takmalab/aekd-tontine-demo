package cm.aekd.tontine.fund;

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
import java.util.UUID;

/**
 * Dépense ou virement enregistré par le trésorier sur un compte opérable
 * (Sanctions, Fond de caisse, Ration ; decisions.md n° 50). Jamais modifié
 * après enregistrement : le journal des comptes reste fidèle.
 */
@Entity
@Table(name = "account_operation")
public class AccountOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AccountOperationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_account", nullable = false, length = 20)
    private FundAccount sourceAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_account", length = 20)
    private FundAccount targetAccount;

    @Column(name = "amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal amount;

    @Column(name = "operation_date", nullable = false)
    private LocalDate operationDate;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "observation")
    private String observation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccountOperation() {
    }

    public AccountOperation(AccountOperationType type, FundAccount sourceAccount, FundAccount targetAccount,
                            BigDecimal amount, LocalDate operationDate, String label, String observation,
                            User recordedBy) {
        this.type = type;
        this.sourceAccount = sourceAccount;
        this.targetAccount = targetAccount;
        this.amount = amount;
        this.operationDate = operationDate;
        this.label = label;
        this.observation = observation;
        this.recordedBy = recordedBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public AccountOperationType getType() {
        return type;
    }

    public FundAccount getSourceAccount() {
        return sourceAccount;
    }

    public FundAccount getTargetAccount() {
        return targetAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getOperationDate() {
        return operationDate;
    }

    public String getLabel() {
        return label;
    }

    public String getObservation() {
        return observation;
    }

    public User getRecordedBy() {
        return recordedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
