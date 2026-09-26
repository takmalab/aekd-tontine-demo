package cm.aekd.tontine.contribution;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Déclaration manuelle d'un paiement par le membre courant (CLAUDE.md §15
 * "ManualPaymentService"). Le membre est toujours résolu depuis le
 * contexte de sécurité, jamais depuis la requête : un membre ne peut
 * déclarer que son propre paiement (regles-metier.md §35).
 */
@Service
@Transactional
public class ManualPaymentService implements PaymentService {

    private static final List<ContributionTransactionStatus> ACTIVE_STATUSES =
            List.of(ContributionTransactionStatus.PENDING, ContributionTransactionStatus.VALIDATED);

    private final ContributionDefinitionRepository definitionRepository;
    private final ContributionPeriodRepository periodRepository;
    private final ContributionParticipantRepository participantRepository;
    private final ContributionTransactionRepository transactionRepository;
    private final MemberRepository memberRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final ContributionPeriodStatusService periodStatusService;

    public ManualPaymentService(ContributionDefinitionRepository definitionRepository,
                                 ContributionPeriodRepository periodRepository,
                                 ContributionParticipantRepository participantRepository,
                                 ContributionTransactionRepository transactionRepository,
                                 MemberRepository memberRepository,
                                 CurrentUserProvider currentUserProvider,
                                 AuditLogService auditLogService,
                                 ContributionPeriodStatusService periodStatusService) {
        this.definitionRepository = definitionRepository;
        this.periodRepository = periodRepository;
        this.participantRepository = participantRepository;
        this.transactionRepository = transactionRepository;
        this.memberRepository = memberRepository;
        this.currentUserProvider = currentUserProvider;
        this.auditLogService = auditLogService;
        this.periodStatusService = periodStatusService;
    }

    @Override
    public ContributionTransactionResponse declare(UUID contributionDefinitionId, ContributionTransactionRequest request) {
        ContributionDefinition definition = definitionRepository.findById(contributionDefinitionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cotisation introuvable"));

        ContributionPeriod period = periodRepository.findById(request.contributionPeriodId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Période introuvable"));
        if (!period.getContributionDefinition().getId().equals(contributionDefinitionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cette période ne correspond pas à cette cotisation");
        }

        UUID memberId = currentUserProvider.currentMemberId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun profil membre associé à ce compte"));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun profil membre associé à ce compte"));

        if (!participantRepository.existsByContributionDefinitionIdAndMemberId(contributionDefinitionId, memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous n'êtes pas participant à cette cotisation");
        }

        BigDecimal amount = resolveAmount(definition, period, memberId, request.amount());

        ContributionTransaction transaction = new ContributionTransaction(
                member, period, amount, request.operator(), request.transactionReference(),
                request.paymentDate(), request.observation());
        transaction = transactionRepository.save(transaction);

        auditLogService.record(AuditAction.PAYMENT_DECLARED, "ContributionTransaction", transaction.getId(),
                "Déclaration d'un paiement de " + amount + " pour \"" + definition.getName() + "\" par "
                        + member.getFullName());

        return ContributionTransactionResponse.from(transaction);
    }

    /**
     * Montant fixe : paiements partiels autorisés (décision du porteur du projet).
     * Une seule déclaration en attente à la fois ; le montant déclaré doit être
     * strictement positif et ne pas dépasser le reste dû (montant - somme validée).
     * Sans montant fourni, le reste dû est retenu.
     *
     * <p>Montant libre : comportement inchangé — une seule déclaration active
     * (en attente ou validée) par période, montant strictement positif.
     */
    private BigDecimal resolveAmount(ContributionDefinition definition, ContributionPeriod period, UUID memberId,
                                     BigDecimal requestedAmount) {
        if (definition.getAmountMode() == AmountMode.FIXED) {
            if (periodStatusService.hasPending(period, memberId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Un paiement est déjà en attente de validation pour cette période");
            }
            BigDecimal remaining = periodStatusService.remainingAmount(period, memberId);
            if (remaining.signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cette cotisation est déjà entièrement payée pour cette période");
            }
            if (requestedAmount == null) {
                return remaining;
            }
            if (requestedAmount.signum() <= 0 || requestedAmount.compareTo(remaining) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Le montant déclaré doit être positif et ne pas dépasser le reste dû ("
                                + remaining.toPlainString() + ")");
            }
            return requestedAmount;
        }

        if (transactionRepository.existsByMemberIdAndContributionPeriodIdAndStatusIn(
                memberId, period.getId(), ACTIVE_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un paiement est déjà déclaré ou validé pour cette période");
        }
        if (requestedAmount == null || requestedAmount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un montant positif est requis pour une cotisation à montant libre");
        }
        return requestedAmount;
    }
}
