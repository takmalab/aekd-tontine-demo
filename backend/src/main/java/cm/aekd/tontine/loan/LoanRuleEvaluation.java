package cm.aekd.tontine.loan;

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
 * Résultat de l'évaluation d'une règle de {@link LoanPolicy} pour une
 * demande de prêt donnée (CLAUDE.md §21, regles-metier.md §29 :
 * RESPECTEE/NON_RESPECTEE + explication). Purement informatif : ne
 * bloque jamais la création de la demande ni la décision du trésorier.
 */
@Entity
@Table(name = "loan_rule_evaluation")
public class LoanRuleEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "rule_name", nullable = false, length = 50)
    private String ruleName;

    @Column(name = "respected", nullable = false)
    private boolean respected;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private Instant evaluatedAt;

    protected LoanRuleEvaluation() {
    }

    public LoanRuleEvaluation(Loan loan, String ruleName, boolean respected, String message) {
        this.loan = loan;
        this.ruleName = ruleName;
        this.respected = respected;
        this.message = message;
    }

    @PrePersist
    protected void onCreate() {
        this.evaluatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Loan getLoan() {
        return loan;
    }

    public String getRuleName() {
        return ruleName;
    }

    public boolean isRespected() {
        return respected;
    }

    public String getMessage() {
        return message;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoanRuleEvaluation that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
