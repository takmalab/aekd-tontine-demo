package cm.aekd.tontine.repayment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, UUID> {

    List<LoanRepayment> findByLoanId(UUID loanId);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM LoanRepayment r WHERE r.loan.id = :loanId")
    BigDecimal sumAmountByLoanId(@Param("loanId") UUID loanId);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM LoanRepayment r WHERE r.loan.member.id = :memberId")
    BigDecimal sumAmountByLoanMemberId(@Param("memberId") UUID memberId);
}
