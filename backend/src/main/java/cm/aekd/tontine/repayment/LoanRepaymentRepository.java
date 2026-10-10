package cm.aekd.tontine.repayment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, UUID> {

    List<LoanRepayment> findByLoanId(UUID loanId);

    /** Total remboursé d'un prêt : remboursements VALIDÉS uniquement (docs/decisions.md n° 38). */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM LoanRepayment r WHERE r.loan.id = :loanId "
            + "AND r.status = cm.aekd.tontine.repayment.LoanRepaymentStatus.VALIDATED")
    BigDecimal sumAmountByLoanId(@Param("loanId") UUID loanId);

    /** Remboursements déclarés par le membre, en attente de validation. */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM LoanRepayment r WHERE r.loan.id = :loanId "
            + "AND r.status = cm.aekd.tontine.repayment.LoanRepaymentStatus.PENDING")
    BigDecimal sumPendingAmountByLoanId(@Param("loanId") UUID loanId);

    List<LoanRepayment> findByStatusOrderByCreatedAtAsc(LoanRepaymentStatus status);

    /** Remboursements VALIDÉS des prêts dans l'un des statuts donnés (encours, décision n° 46). */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM LoanRepayment r WHERE r.loan.status IN :statuses "
            + "AND r.status = cm.aekd.tontine.repayment.LoanRepaymentStatus.VALIDATED")
    BigDecimal sumValidatedAmountByLoanStatusIn(@Param("statuses") List<cm.aekd.tontine.loan.LoanStatus> statuses);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM LoanRepayment r WHERE r.loan.member.id = :memberId "
            + "AND r.status = cm.aekd.tontine.repayment.LoanRepaymentStatus.VALIDATED")
    BigDecimal sumAmountByLoanMemberId(@Param("memberId") UUID memberId);
}
