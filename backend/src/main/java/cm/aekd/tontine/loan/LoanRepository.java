package cm.aekd.tontine.loan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<Loan, UUID> {

    List<Loan> findByMemberId(UUID memberId);

    List<Loan> findByGuarantorIdOrderByCreatedAtDesc(UUID guarantorMemberId);

    List<Loan> findByStatus(LoanStatus status);

    List<Loan> findByStatusIn(List<LoanStatus> statuses);

    long countByMemberIdAndStatusIn(UUID memberId, List<LoanStatus> statuses);

    long countByStatus(LoanStatus status);

    long countByStatusIn(List<LoanStatus> statuses);

    /** Empêche de modifier/supprimer une politique déjà utilisée par un prêt (intégrité de l'historique). */
    boolean existsByLoanPolicyId(UUID loanPolicyId);
}
