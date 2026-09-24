package cm.aekd.tontine.contribution;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Détermine la situation PAID/PENDING/LATE/NOT_PAID d'un membre pour une
 * période (CLAUDE.md §13). "En retard" suit la définition littérale du
 * §25 (date limite dépassée, aucun paiement validé) sans délai de grâce
 * supplémentaire — le seuil configurable des règles de sanction (§18-19)
 * est un concept distinct, non utilisé ici.
 */
@Service
public class ContributionPeriodStatusService {

    private final ContributionTransactionRepository transactionRepository;

    public ContributionPeriodStatusService(ContributionTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public ContributionPeriodStatus resolve(ContributionPeriod period, UUID memberId) {
        if (transactionRepository.existsByMemberIdAndContributionPeriodIdAndStatus(
                memberId, period.getId(), ContributionTransactionStatus.VALIDATED)) {
            return ContributionPeriodStatus.PAID;
        }
        if (transactionRepository.existsByMemberIdAndContributionPeriodIdAndStatus(
                memberId, period.getId(), ContributionTransactionStatus.PENDING)) {
            return ContributionPeriodStatus.PENDING;
        }
        if (period.getDueDate() != null && LocalDate.now().isAfter(period.getDueDate())) {
            return ContributionPeriodStatus.LATE;
        }
        return ContributionPeriodStatus.NOT_PAID;
    }
}
