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

    /** Prélèvements sur l'épargne d'un membre (decisions.md n° 53). */
    List<LoanRepayment> findBySourceAndPayerId(RepaymentSource source, UUID payerMemberId);

    List<LoanRepayment> findBySource(RepaymentSource source);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM LoanRepayment r WHERE r.payer.id = :memberId "
            + "AND r.source = cm.aekd.tontine.repayment.RepaymentSource.SAVINGS_DEDUCTION")
    BigDecimal sumSavingsDeductionsByPayer(@Param("memberId") UUID memberId);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM LoanRepayment r "
            + "WHERE r.source = cm.aekd.tontine.repayment.RepaymentSource.SAVINGS_DEDUCTION")
    BigDecimal sumAllSavingsDeductions();

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM LoanRepayment r WHERE r.loan.member.id = :memberId "
            + "AND r.status = cm.aekd.tontine.repayment.LoanRepaymentStatus.VALIDATED")
    BigDecimal sumAmountByLoanMemberId(@Param("memberId") UUID memberId);
}
