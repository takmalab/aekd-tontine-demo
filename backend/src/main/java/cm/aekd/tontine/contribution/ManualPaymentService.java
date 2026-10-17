package cm.aekd.tontine.contribution;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.document.DocumentFormat;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.notification.NotificationService;
import cm.aekd.tontine.notification.NotificationType;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
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
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public ManualPaymentService(ContributionDefinitionRepository definitionRepository,
                                 ContributionPeriodRepository periodRepository,
                                 ContributionParticipantRepository participantRepository,
                                 ContributionTransactionRepository transactionRepository,
                                 MemberRepository memberRepository,
                                 CurrentUserProvider currentUserProvider,
                                 AuditLogService auditLogService,
                                 ContributionPeriodStatusService periodStatusService,
                                 NotificationService notificationService,
                                 UserRepository userRepository) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
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

        ContributionParticipant participant = participantRepository
                .findByContributionDefinitionIdAndMemberId(contributionDefinitionId, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Vous n'êtes pas participant à cette cotisation"));
        if (!participant.appliesTo(period)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous participez à cette cotisation à partir des séances suivant votre ajout : "
                            + "cette séance ne vous concerne pas");
        }

        BigDecimal amount = resolveAmount(definition, period, memberId, request.amount());

        ContributionTransaction transaction = new ContributionTransaction(
                member, period, amount, request.operator(), request.transactionReference(),
                request.paymentDate(), request.observation());
        transaction = transactionRepository.save(transaction);

        auditLogService.record(AuditAction.PAYMENT_DECLARED, "ContributionTransaction", transaction.getId(),
                "Déclaration d'un paiement de " + amount + " pour \"" + definition.getName() + "\" par "
                        + member.getFullName());
        notificationService.notifyStaff(NotificationType.PAYMENT_DECLARED, "Paiement à valider",
                member.getFullName() + " a déclaré " + DocumentFormat.fcfa(amount) + " pour \""
                        + definition.getName() + "\" (" + period.getSession().getLabel() + ").",
                "/payments");

        return ContributionTransactionResponse.from(transaction);
    }

    /**
     * Décision n° 65 : le trésorier (ou un administrateur) enregistre le paiement d'un membre,
     * par exemple des espèces remises à la séance. Le paiement est validé d'emblée et tracé
     * (enregistré par, validé par). Mêmes règles que la déclaration (participant pour cette
     * séance, reste dû) ; personne n'enregistre son propre paiement (décision n° 40).
     */
    public ContributionTransactionResponse record(UUID contributionDefinitionId, RecordPaymentRequest request) {
        ContributionDefinition definition = definitionRepository.findById(contributionDefinitionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cotisation introuvable"));
        ContributionPeriod period = periodRepository.findById(request.contributionPeriodId())
                .filter(p -> p.getContributionDefinition().getId().equals(contributionDefinitionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cette séance ne correspond pas à cette cotisation"));
        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));

        if (currentUserProvider.currentMemberId().map(member.getId()::equals).orElse(false)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous ne pouvez pas enregistrer votre propre paiement : il doit l'être par un autre membre du bureau");
        }
        if (!participantRepository.participatesIn(period, member.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    member.getFullName() + " ne participe pas à cette cotisation pour cette séance");
        }
        User recorder = currentUserProvider.currentUserId().flatMap(userRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));

        BigDecimal amount = resolveAmount(definition, period, member.getId(), request.amount());
        String reference = request.transactionReference() == null || request.transactionReference().isBlank()
                ? "Enregistré par le trésorier" : request.transactionReference().trim();

        ContributionTransaction transaction = new ContributionTransaction(member, period, amount, request.operator(),
                reference, request.paymentDate(), request.observation());
        transaction.setStatus(ContributionTransactionStatus.VALIDATED);
        transaction.setValidatedBy(recorder);
        transaction.setValidatedAt(Instant.now());
        transaction.setRecordedBy(recorder);
        transaction = transactionRepository.save(transaction);

        auditLogService.record(AuditAction.PAYMENT_RECORDED, "ContributionTransaction", transaction.getId(),
                "Paiement de " + amount + " (" + request.operator() + ") enregistré pour " + member.getFullName()
                        + " — \"" + definition.getName() + "\", " + period.getSession().getLabel());
        notificationService.notifyMember(member, NotificationType.PAYMENT_VALIDATED, "Paiement enregistré",
                "Le trésorier a enregistré votre paiement de " + DocumentFormat.fcfa(amount) + " pour \""
                        + definition.getName() + "\" (" + period.getSession().getLabel() + ").",
                "/my-contributions");
        // Décision n° 45 confirmée (n° 67) : transparence envers tous les membres.
        notificationService.notifyOtherMembers(member, definition, "Paiement de cotisation",
                member.getFullName() + " a payé " + DocumentFormat.fcfa(amount) + " pour \"" + definition.getName()
                        + "\" (" + period.getSession().getLabel() + ").");

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
