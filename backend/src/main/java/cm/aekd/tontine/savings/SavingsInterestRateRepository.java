package cm.aekd.tontine.savings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SavingsInterestRateRepository extends JpaRepository<SavingsInterestRate, UUID> {

    List<SavingsInterestRate> findAllByOrderByEffectiveFromAsc();

    boolean existsByEffectiveFrom(LocalDate effectiveFrom);
}
