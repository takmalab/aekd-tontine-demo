package cm.aekd.tontine.sanction;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.contribution.ContributionParticipant;
import cm.aekd.tontine.contribution.ContributionParticipantRepository;
import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionPeriodRepository;
import cm.aekd.tontine.contribution.ContributionPeriodStatusService;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Application manuelle des sanctions (CLAUDE.md §18-19). Aucune sanction
 * n'est jamais créée automatiquement : docs/decisions.md §10 (déclencher
 * automatiquement vs proposer) reste "À VALIDER", donc seule une action
 * explicite du trésorier crée une AppliedSanction. {@link #findCandidates}
 * ne fait qu'informer (§19 "identifier automatiquement... pourra ensuite
 * proposer"), sans jamais appliquer quoi que ce soit de lui-même.
 */
@Service
@Transactional
public class AppliedSanctionService {

    private final SanctionRuleRepository ruleRepository;
    private final AppliedSanctionRepository appliedRepository;
    private final ContributionPeriodRepository periodRepository;
    private final ContributionParticipantRepository participantRepository;
    private final ContributionPeriodStatusService periodStatusService;
    private final MemberRepository memberRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public AppliedSanctionService(SanctionRuleRepository ruleRepository,
                                   AppliedSanctionRepository appliedRepository,
                                   ContributionPeriodRepository periodRepository,
                                   ContributionParticipantRepository participantRepository,
                                   ContributionPeriodStatusService periodStatusService,
                                   MemberRepository memberRepository,
                                   CurrentUserProvider currentUserProvider,
                                   UserRepository userRepository,
                                   AuditLogService auditLogService) {
        this.ruleRepository = ruleRepository;
        this.appliedRepository = appliedRepository;
        this.periodRepository = periodRepository;
        this.participantRepository = participantRepository;
        this.periodStatusService = periodStatusService;
        this.memberRepository = memberRepository;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    public AppliedSanctionResponse apply(UUID sanctionRuleId, ApplySanctionRequest request) {
        SanctionRule rule = ruleRepository.findById(sanctionRuleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Règle de sanction introuvable"));
        if (!rule.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette règle de sanction est désactivée");
        }

        ContributionPeriod period = periodRepository.findById(request.contributionPeriodId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Période introuvable"));
        if (!period.getContributionDefinition().getId().equals(rule.getContributionDefinition().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cette période ne correspond pas à la cotisation de la règle");
        }

        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
        if (!participantRepository.existsByContributionDefinitionIdAndMemberId(
                rule.getContributionDefinition().getId(), member.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce membre n'est pas participant à cette cotisation");
        }

        if (!isEligible(rule, period, member.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce membre n'est pas éligible à cette sanction selon la règle configurée");
        }

        if (appliedRepository.existsBySanctionRuleIdAndMemberIdAndContributionPeriodIdAndStatus(
                rule.getId(), member.getId(), period.getId(), AppliedSanctionStatus.APPLIED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une sanction est déjà appliquée à ce membre pour cette règle et cette période");
        }

        User appliedBy = currentUserProvider.currentUserId()
                .flatMap(userRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));

        AppliedSanction sanction = new AppliedSanction(rule, member, period,
                rule.getType() == SanctionType.MONETARY ? rule.getMonetaryAmount() : null,
                rule.getType() == SanctionType.IN_KIND ? rule.getDescription() : null,
                appliedBy);
        sanction = appliedRepository.save(sanction);

        auditLogService.record(AuditAction.SANCTION_APPLIED, "AppliedSanction", sanction.getId(),
                "Application d'une sanction " + rule.getType() + " à " + member.getFullName());

        return AppliedSanctionResponse.from(sanction);
    }

    public AppliedSanctionResponse cancel(UUID id) {
        AppliedSanction sanction = appliedRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sanction introuvable"));
        if (sanction.getStatus() != AppliedSanctionStatus.APPLIED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette sanction n'est pas active");
        }
        sanction.setStatus(AppliedSanctionStatus.CANCELLED);
        sanction = appliedRepository.save(sanction);

        auditLogService.record(AuditAction.SANCTION_CANCELLED, "AppliedSanction", sanction.getId(),
                "Annulation de la sanction appliquée à " + sanction.getMember().getFullName());

        return AppliedSanctionResponse.from(sanction);
    }

    public List<AppliedSanctionResponse> findMine() {
        UUID memberId = currentUserProvider.currentMemberId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun profil membre associé à ce compte"));
        return appliedRepository.findByMemberId(memberId).stream()
                .map(AppliedSanctionResponse::from)
                .toList();
    }

    public List<AppliedSanctionResponse> findByDefinition(UUID contributionDefinitionId) {
        return appliedRepository.findByContributionPeriod_ContributionDefinition_Id(contributionDefinitionId).stream()
                .map(AppliedSanctionResponse::from)
                .toList();
    }

    /**
     * Détection en lecture seule des membres éligibles à une règle
     * (retard constaté selon le seuil configuré, pas déjà sanctionnés).
     * N'applique jamais rien automatiquement.
     */
    public List<SanctionCandidateResponse> findCandidates(UUID sanctionRuleId) {
        SanctionRule rule = ruleRepository.findById(sanctionRuleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Règle de sanction introuvable"));
        UUID definitionId = rule.getContributionDefinition().getId();

        List<ContributionParticipant> participants = participantRepository.findByContributionDefinitionId(definitionId);
        List<ContributionPeriod> periods = periodRepository.findByContributionDefinitionId(definitionId);

        List<SanctionCandidateResponse> candidates = new ArrayList<>();
        for (ContributionPeriod period : periods) {
            for (ContributionParticipant participant : participants) {
                UUID memberId = participant.getMember().getId();
                boolean alreadySanctioned = appliedRepository.existsBySanctionRuleIdAndMemberIdAndContributionPeriodIdAndStatus(
                        rule.getId(), memberId, period.getId(), AppliedSanctionStatus.APPLIED);
                if (!alreadySanctioned && isEligible(rule, period, memberId)) {
                    candidates.add(new SanctionCandidateResponse(memberId, participant.getMember().getFullName(),
                            period.getId(), period.getSession().getId(), period.getSession().getLabel()));
                }
            }
        }
        return candidates;
    }

    private boolean isEligible(SanctionRule rule, ContributionPeriod period, UUID memberId) {
        if (period.getDueDate() == null) {
            return false;
        }
        LocalDate threshold = period.getDueDate().plusDays(rule.getLateDaysThreshold());
        if (LocalDate.now().isBefore(threshold)) {
            return false;
        }
        // Paiements partiels : seul un règlement complet exclut le membre des candidats.
        // Décision validée par le porteur du projet (2026-09-25) : un membre partiellement
        // payé reste candidat à la sanction de retard.
        return !periodStatusService.isFullyPaid(period, memberId);
    }
}
