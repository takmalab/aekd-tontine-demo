package cm.aekd.tontine.dashboard;

import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.contribution.ContributionParticipant;
import cm.aekd.tontine.contribution.ContributionParticipantRepository;
import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionPeriodRepository;
import cm.aekd.tontine.contribution.ContributionPeriodStatus;
import cm.aekd.tontine.contribution.ContributionPeriodStatusService;
import cm.aekd.tontine.contribution.ContributionStatus;
import cm.aekd.tontine.contribution.ContributionTransaction;
import cm.aekd.tontine.contribution.ContributionTransactionRepository;
import cm.aekd.tontine.contribution.ContributionTransactionStatus;
import cm.aekd.tontine.fund.FundService;
import cm.aekd.tontine.loan.LoanRepository;
import cm.aekd.tontine.loan.LoanStatus;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.repayment.LoanRepaymentRepository;
import cm.aekd.tontine.sanction.AppliedSanctionRepository;
import cm.aekd.tontine.sanction.AppliedSanctionStatus;
import cm.aekd.tontine.session.Session;
import cm.aekd.tontine.session.SessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tableaux de bord (CLAUDE.md §25), calculés à la volée à partir des
 * données existantes — aucune table dédiée, même logique que FundService.
 * "Séance actuelle" = la dernière séance tenue, c'est-à-dire la séance la plus
 * récente dont la date est ≤ aujourd'hui (une séance est désormais un jour
 * unique, plus une période). Absente (null) s'il n'y en a aucune ; une séance
 * future n'est jamais retenue. DÉCISION À VALIDER : définition proposée suite
 * au passage à une date unique, à confirmer par le porteur du projet.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final List<LoanStatus> IN_PROGRESS_STATUSES = List.of(LoanStatus.APPROVED, LoanStatus.IN_PROGRESS);

    private final SessionRepository sessionRepository;
    private final MemberRepository memberRepository;
    private final ContributionPeriodRepository periodRepository;
    private final ContributionParticipantRepository participantRepository;
    private final ContributionTransactionRepository transactionRepository;
    private final ContributionPeriodStatusService periodStatusService;
    private final FundService fundService;
    private final LoanRepository loanRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final AppliedSanctionRepository appliedSanctionRepository;
    private final CurrentUserProvider currentUserProvider;

    public DashboardService(SessionRepository sessionRepository, MemberRepository memberRepository,
                             ContributionPeriodRepository periodRepository,
                             ContributionParticipantRepository participantRepository,
                             ContributionTransactionRepository transactionRepository,
                             ContributionPeriodStatusService periodStatusService, FundService fundService,
                             LoanRepository loanRepository, LoanRepaymentRepository loanRepaymentRepository,
                             AppliedSanctionRepository appliedSanctionRepository,
                             CurrentUserProvider currentUserProvider) {
        this.sessionRepository = sessionRepository;
        this.memberRepository = memberRepository;
        this.periodRepository = periodRepository;
        this.participantRepository = participantRepository;
        this.transactionRepository = transactionRepository;
        this.periodStatusService = periodStatusService;
        this.fundService = fundService;
        this.loanRepository = loanRepository;
        this.loanRepaymentRepository = loanRepaymentRepository;
        this.appliedSanctionRepository = appliedSanctionRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public Object currentDashboard() {
        if (currentUserProvider.hasAnyRole("ADMIN", "TRESORIER")) {
            return adminDashboard();
        }
        return memberDashboard();
    }

    public AdminDashboardResponse adminDashboard() {
        Session currentSession = currentSession().orElse(null);
        List<ContributionPeriod> activePeriods = activePeriodsOf(currentSession);

        long mandatoryExpected = 0;
        long validated = 0;
        long pending = 0;
        long late = 0;
        BigDecimal amountCollected = BigDecimal.ZERO;

        for (ContributionPeriod period : activePeriods) {
            for (ContributionTransaction tx : transactionRepository.findByContributionPeriodIdAndStatus(
                    period.getId(), ContributionTransactionStatus.VALIDATED)) {
                amountCollected = amountCollected.add(tx.getAmount());
            }

            if (period.getContributionDefinition().isMandatory()) {
                List<ContributionParticipant> participants =
                        participantRepository.findByContributionDefinitionId(period.getContributionDefinition().getId());
                mandatoryExpected += participants.size();
                for (ContributionParticipant participant : participants) {
                    ContributionPeriodStatus status = periodStatusService.resolve(period, participant.getMember().getId());
                    if (status == ContributionPeriodStatus.PAID) validated++;
                    else if (status == ContributionPeriodStatus.PENDING) pending++;
                    else if (status == ContributionPeriodStatus.LATE) late++;
                }
            }
        }

        return new AdminDashboardResponse(
                memberRepository.countByActiveTrue(),
                currentSession != null ? SessionSummaryResponse.from(currentSession) : null,
                mandatoryExpected,
                validated,
                pending,
                late,
                amountCollected,
                fundService.tontineFundBalance(),
                fundService.totalIndividualSavings(),
                loanRepository.countByStatus(LoanStatus.REQUESTED),
                loanRepository.countByStatusIn(IN_PROGRESS_STATUSES),
                appliedSanctionRepository.countByStatus(AppliedSanctionStatus.APPLIED)
        );
    }

    public MemberDashboardResponse memberDashboard() {
        UUID memberId = currentUserProvider.currentMemberId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun profil membre associé à ce compte"));

        Session currentSession = currentSession().orElse(null);
        List<ContributionPeriod> myPeriods = activePeriodsOf(currentSession).stream()
                .filter(period -> participantRepository.existsByContributionDefinitionIdAndMemberId(
                        period.getContributionDefinition().getId(), memberId))
                .toList();

        long mandatoryCount = 0;
        long paidCount = 0;
        long pendingCount = 0;
        long lateCount = 0;
        long optionalCount = 0;

        for (ContributionPeriod period : myPeriods) {
            if (period.getContributionDefinition().isMandatory()) {
                mandatoryCount++;
                ContributionPeriodStatus status = periodStatusService.resolve(period, memberId);
                if (status == ContributionPeriodStatus.PAID) paidCount++;
                else if (status == ContributionPeriodStatus.PENDING) pendingCount++;
                else if (status == ContributionPeriodStatus.LATE) lateCount++;
            } else {
                optionalCount++;
            }
        }

        return new MemberDashboardResponse(
                currentSession != null ? SessionSummaryResponse.from(currentSession) : null,
                mandatoryCount,
                paidCount,
                pendingCount,
                lateCount,
                optionalCount,
                fundService.individualSavingsOf(memberId),
                loanRepository.countByMemberIdAndStatusIn(memberId, IN_PROGRESS_STATUSES),
                loanRepository.countByMemberIdAndStatusIn(memberId, List.of(LoanStatus.REQUESTED)),
                loanRepaymentRepository.sumAmountByLoanMemberId(memberId),
                appliedSanctionRepository.countByMemberIdAndStatus(memberId, AppliedSanctionStatus.APPLIED)
        );
    }

    private Optional<Session> currentSession() {
        LocalDate today = LocalDate.now();
        return sessionRepository.findFirstBySessionDateLessThanEqualOrderBySessionDateDesc(today);
    }

    private List<ContributionPeriod> activePeriodsOf(Session session) {
        if (session == null) {
            return List.of();
        }
        return periodRepository.findBySessionId(session.getId()).stream()
                .filter(period -> period.getContributionDefinition().getStatus() == ContributionStatus.ACTIVE)
                .toList();
    }
}
