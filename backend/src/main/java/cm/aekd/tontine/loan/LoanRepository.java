package cm.aekd.tontine.loan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<Loan, UUID> {

    List<Loan> findByMemberId(UUID memberId);

    List<Loan> findByStatus(LoanStatus status);

    long countByMemberIdAndStatusIn(UUID memberId, List<LoanStatus> statuses);

    long countByStatus(LoanStatus status);

    long countByStatusIn(List<LoanStatus> statuses);
}
