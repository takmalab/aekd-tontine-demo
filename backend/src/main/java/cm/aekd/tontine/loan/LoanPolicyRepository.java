package cm.aekd.tontine.loan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LoanPolicyRepository extends JpaRepository<LoanPolicy, UUID> {

    Optional<LoanPolicy> findFirstByActiveTrueOrderByCreatedAtDesc();
}
