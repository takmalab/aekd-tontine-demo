package cm.aekd.tontine.loan;

import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionParticipant;
import cm.aekd.tontine.contribution.ContributionParticipantRepository;
import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionPeriodRepository;
import cm.aekd.tontine.contribution.ContributionPeriodStatus;
import cm.aekd.tontine.contribution.ContributionPeriodStatusService;
import cm.aekd.tontine.document.DocumentFormat;
import cm.aekd.tontine.sanction.AppliedSanction;
import cm.aekd.tontine.sanction.AppliedSanctionRepository;
import cm.aekd.tontine.sanction.AppliedSanctionStatus;
import cm.aekd.tontine.sanction.SanctionPaymentRepository;
import cm.aekd.tontine.sanction.SanctionPaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Un membre est-il « à jour » pour être avaliste ? (decisions.md n° 52) Trois
 * conditions, validées par le propriétaire du projet :
 * <ul>
 *   <li>aucune cotisation obligatoire échue et non soldée (LATE, ou PARTIAL
 *   après l'échéance) ;</li>
 *   <li>aucune sanction monétaire appliquée qui reste à payer (seuls les
 *   paiements validés comptent) ;</li>
 *   <li>aucun prêt en cours dont la durée approuvée est dépassée.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class GuarantorStandingService {


    private final ContributionParticipantRepository participantRepository;
    private final ContributionPeriodRepository periodRepository;
    private final ContributionPeriodStatusService periodStatusService;
    private final AppliedSanctionRepository sanctionRepository;
    private final SanctionPaymentRepository sanctionPaymentRepository;
    private final LoanRepository loanRepository;

    public GuarantorStandingService(ContributionParticipantRepository participantRepository,
                                    ContributionPeriodRepository periodRepository,
                                    ContributionPeriodStatusService periodStatusService,
                                    AppliedSanctionRepository sanctionRepository,
                                    SanctionPaymentRepository sanctionPaymentRepository,
                                    LoanRepository loanRepository) {
        this.participantRepository = participantRepository;
        this.periodRepository = periodRepository;
        this.periodStatusService = periodStatusService;
        this.sanctionRepository = sanctionRepository;
        this.sanctionPaymentRepository = sanctionPaymentRepository;
        this.loanRepository = loanRepository;
    }

    /** Ce qui empêche le membre d'être à jour ; vide s'il l'est. */
    public List<String> issuesOf(UUID memberId) {
        List<String> issues = new ArrayList<>();
        LocalDate today = LocalDate.now();

        int lateContributions = 0;
        for (ContributionParticipant participant : participantRepository.findByMemberId(memberId)) {
            ContributionDefinition definition = participant.getContributionDefinition();
            if (!definition.isMandatory()) {
                continue;
            }
            for (ContributionPeriod period : periodRepository.findByContributionDefinitionId(definition.getId())) {
                if (period.getDueDate() == null || !today.isAfter(period.getDueDate())) {
                    continue;
                }
                ContributionPeriodStatus status = periodStatusService.resolve(period, memberId);
                if (status == ContributionPeriodStatus.LATE || status == ContributionPeriodStatus.PARTIAL) {
                    lateContributions++;
                }
            }
        }
        if (lateContributions > 0) {
            issues.add(lateContributions + " cotisation(s) obligatoire(s) en retard");
        }

        BigDecimal sanctionsDue = BigDecimal.ZERO;
        int unpaidSanctions = 0;
        for (AppliedSanction sanction : sanctionRepository.findByMemberId(memberId)) {
            if (sanction.getStatus() != AppliedSanctionStatus.APPLIED || sanction.getAmount() == null) {
                continue;
            }
            BigDecimal paid = sanctionPaymentRepository.sumBySanctionAndStatus(sanction.getId(),
                    SanctionPaymentStatus.VALIDATED);
            BigDecimal remaining = sanction.getAmount().subtract(paid);
            if (remaining.signum() > 0) {
                unpaidSanctions++;
                sanctionsDue = sanctionsDue.add(remaining);
            }
        }
        if (unpaidSanctions > 0) {
            issues.add(unpaidSanctions + " sanction(s) non payée(s) (" + DocumentFormat.fcfa(sanctionsDue) + ")");
        }

        long overdueLoans = loanRepository.findByMemberId(memberId).stream()
                .filter(loan -> loan.isOverdue(today))
                .count();
        if (overdueLoans > 0) {
            issues.add(overdueLoans + " prêt(s) en cours dont l'échéance est dépassée");
        }
        return issues;
    }
}
