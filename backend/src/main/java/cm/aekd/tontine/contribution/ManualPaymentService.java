package cm.aekd.tontine.contribution;

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

    public ManualPaymentService(ContributionDefinitionRepository definitionRepository,
                                 ContributionPeriodRepository periodRepository,
                                 ContributionParticipantRepository participantRepository,
                                 ContributionTransactionRepository transactionRepository,
                                 MemberRepository memberRepository,
                                 CurrentUserProvider currentUserProvider) {
        this.definitionRepository = definitionRepository;
        this.periodRepository = periodRepository;
        this.participantRepository = participantRepository;
        this.transactionRepository = transactionRepository;
        this.memberRepository = memberRepository;
        this.currentUserProvider = currentUserProvider;
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

        if (transactionRepository.existsByMemberIdAndContributionPeriodIdAndStatusIn(
                memberId, period.getId(), ACTIVE_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un paiement est déjà déclaré ou validé pour cette période");
        }

        BigDecimal amount = resolveAmount(definition, request.amount());

        ContributionTransaction transaction = new ContributionTransaction(
                member, period, amount, request.operator(), request.transactionReference(),
                request.paymentDate(), request.observation());

        return ContributionTransactionResponse.from(transactionRepository.save(transaction));
    }

    private BigDecimal resolveAmount(ContributionDefinition definition, BigDecimal requestedAmount) {
        if (definition.getAmountMode() == AmountMode.FIXED) {
            if (requestedAmount != null && requestedAmount.compareTo(definition.getAmount()) != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Le montant déclaré doit correspondre au montant fixe de la cotisation ("
                                + definition.getAmount() + ")");
            }
            return definition.getAmount();
        }

        if (requestedAmount == null || requestedAmount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un montant positif est requis pour une cotisation à montant libre");
        }
        return requestedAmount;
    }
}
