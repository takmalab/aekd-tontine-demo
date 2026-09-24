package cm.aekd.tontine.loan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoanRuleEvaluationRepository extends JpaRepository<LoanRuleEvaluation, UUID> {

    List<LoanRuleEvaluation> findByLoanId(UUID loanId);
}
