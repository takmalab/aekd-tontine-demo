package cm.aekd.tontine.contribution;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.sanction.AppliedSanctionRepository;
import cm.aekd.tontine.session.Session;
import cm.aekd.tontine.session.SessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * CLAUDE.md §11 : une cotisation PRIVATE n'est visible que par ADMIN,
 * TRESORIER et ses participants. Une cotisation non ACTIVE (brouillon)
 * n'est visible que par ADMIN/TRESORIER. En dehors de ces cas, l'entité
 * est traitée comme inexistante (404) plutôt que refusée (403), pour ne
 * pas révéler son existence à un membre non autorisé.
 */
@Service
@Transactional
public class ContributionDefinitionService {

    private final ContributionDefinitionRepository definitionRepository;
    private final ContributionParticipantRepository participantRepository;
    private final ContributionBeneficiaryRepository beneficiaryRepository;
    private final ContributionPeriodRepository periodRepository;
    private final MemberRepository memberRepository;
    private final SessionRepository sessionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final ContributionPeriodStatusService periodStatusService;
    private final ContributionTransactionRepository transactionRepository;
    private final AppliedSanctionRepository appliedSanctionRepository;

    public ContributionDefinitionService(ContributionDefinitionRepository definitionRepository,
                                          ContributionParticipantRepository participantRepository,
                                          ContributionBeneficiaryRepository beneficiaryRepository,
                                          ContributionPeriodRepository periodRepository,
                                          MemberRepository memberRepository,
                                          SessionRepository sessionRepository,
                                          CurrentUserProvider currentUserProvider,
                                          AuditLogService auditLogService,
                                          ContributionPeriodStatusService periodStatusService,
                                          ContributionTransactionRepository transactionRepository,
                                          AppliedSanctionRepository appliedSanctionRepository) {
        this.definitionRepository = definitionRepository;
        this.participantRepository = participantRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.periodRepository = periodRepository;
        this.memberRepository = memberRepository;
        this.sessionRepository = sessionRepository;
        this.currentUserProvider = currentUserProvider;
        this.auditLogService = auditLogService;
        this.periodStatusService = periodStatusService;
        this.transactionRepository = transactionRepository;
        this.appliedSanctionRepository = appliedSanctionRepository;
    }

    public ContributionDefinitionResponse create(ContributionDefinitionRequest request) {
        validateAmount(request);

        ContributionDefinition definition = new ContributionDefinition(
                request.name(), request.description(), request.amount(), request.amountMode(),
                request.frequency(), request.mandatory(), request.visibility(), request.fundDestination()
        );
        definition = definitionRepository.save(definition);

        auditLogService.record(AuditAction.CONTRIBUTION_CREATED, "ContributionDefinition", definition.getId(),
                "Création de la cotisation \"" + definition.getName() + "\"");

        return ContributionDefinitionResponse.from(definition, false);
    }

    public List<ContributionDefinitionResponse> findAll() {
        Set<UUID> withPayments = transactionRepository.findContributionDefinitionIdsWithPayments();
        return definitionRepository.findAll().stream()
                .filter(this::isVisibleToCurrentUser)
                .map(definition -> ContributionDefinitionResponse.from(definition,
                        withPayments.contains(definition.getId())))
                .toList();
    }

    public ContributionDefinitionResponse findById(UUID id) {
        return toResponse(getVisibleOrThrow(id));
    }

    /**
     * Décision validée le 03/10/2026 (docs/decisions.md n° 36) : nom, description,
     * fréquence et visibilité restent modifiables ; montant, mode de montant,
     * caractère obligatoire et destination des fonds sont verrouillés dès qu'un
     * paiement existe, pour ne pas fausser rétroactivement statuts et fonds.
     */
    public ContributionDefinitionResponse update(UUID id, ContributionDefinitionRequest request) {
        ContributionDefinition definition = getDefinitionOrThrow(id);
        validateAmount(request);

        if (hasPayments(id)) {
            List<String> changed = new ArrayList<>();
            if (request.amountMode() != definition.getAmountMode()) {
                changed.add("mode de montant");
            }
            if (!sameAmount(request.amount(), definition.getAmount())) {
                changed.add("montant");
            }
            if (request.mandatory() != definition.isMandatory()) {
                changed.add("caractère obligatoire");
            }
            if (request.fundDestination() != definition.getFundDestination()) {
                changed.add("destination des fonds");
            }
            if (!changed.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Des paiements existent déjà pour cette cotisation : modification impossible ("
                                + String.join(", ", changed) + ").");
            }
        }

        definition.setName(request.name().trim());
        definition.setDescription(request.description());
        definition.setAmount(request.amount());
        definition.setAmountMode(request.amountMode());
        definition.setFrequency(request.frequency());
        definition.setMandatory(request.mandatory());
        definition.setVisibility(request.visibility());
        definition.setFundDestination(request.fundDestination());
        definition = definitionRepository.save(definition);

        auditLogService.record(AuditAction.CONTRIBUTION_UPDATED, "ContributionDefinition", definition.getId(),
                "Modification de la cotisation \"" + definition.getName() + "\"");

        return toResponse(definition);
    }

    /** Alternative à la suppression quand des paiements existent : la cotisation n'est plus proposée. */
    public ContributionDefinitionResponse deactivate(UUID id) {
        ContributionDefinition definition = getDefinitionOrThrow(id);
        if (definition.getStatus() != ContributionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seule une cotisation active peut être désactivée");
        }
        definition.setStatus(ContributionStatus.INACTIVE);
        definition = definitionRepository.save(definition);

        auditLogService.record(AuditAction.CONTRIBUTION_DEACTIVATED, "ContributionDefinition", definition.getId(),
                "Désactivation de la cotisation \"" + definition.getName() + "\"");

        return toResponse(definition);
    }

    /**
     * Décision validée le 03/10/2026 (docs/decisions.md n° 35) : suppression refusée
     * dès qu'un paiement (quel que soit son statut) ou une sanction appliquée existe ;
     * la désactivation est proposée à la place. Sinon, participants, bénéficiaires,
     * périodes et règles de sanction sont supprimés avec elle (ON DELETE CASCADE).
     */
    public void delete(UUID id) {
        ContributionDefinition definition = getDefinitionOrThrow(id);
        if (hasPayments(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer cette cotisation : des paiements ont déjà été déclarés. "
                            + "Désactivez-la plutôt.");
        }
        if (appliedSanctionRepository.existsByContributionPeriod_ContributionDefinition_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer cette cotisation : des sanctions ont été appliquées. "
                            + "Désactivez-la plutôt.");
        }

        String description = "Suppression de la cotisation \"" + definition.getName() + "\"";
        definitionRepository.deleteCascadeById(id);
        auditLogService.record(AuditAction.CONTRIBUTION_DELETED, "ContributionDefinition", id, description);
    }

    public ContributionDefinitionResponse activate(UUID id) {
        ContributionDefinition definition = getDefinitionOrThrow(id);
        if (definition.getStatus() == ContributionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette cotisation est déjà active");
        }
        definition.setStatus(ContributionStatus.ACTIVE);
        definition = definitionRepository.save(definition);

        auditLogService.record(AuditAction.CONTRIBUTION_ACTIVATED, "ContributionDefinition", definition.getId(),
                "Activation de la cotisation \"" + definition.getName() + "\"");

        return toResponse(definition);
    }

    public ParticipantResponse addParticipant(UUID definitionId, MemberRefRequest request) {
        ContributionDefinition definition = getDefinitionOrThrow(definitionId);
        Member member = getMemberOrThrow(request.memberId());
        if (participantRepository.existsByContributionDefinitionIdAndMemberId(definitionId, member.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce membre est déjà participant à cette cotisation");
        }
        ContributionParticipant participant = new ContributionParticipant(definition, member);
        return ParticipantResponse.from(participantRepository.save(participant));
    }

    public List<ParticipantResponse> listParticipants(UUID definitionId) {
        getVisibleOrThrow(definitionId);
        return participantRepository.findByContributionDefinitionId(definitionId).stream()
                .map(ParticipantResponse::from)
                .toList();
    }

    public BeneficiaryResponse addBeneficiary(UUID definitionId, MemberRefRequest request) {
        ContributionDefinition definition = getDefinitionOrThrow(definitionId);
        Member member = getMemberOrThrow(request.memberId());
        if (beneficiaryRepository.existsByContributionDefinitionIdAndMemberId(definitionId, member.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce membre est déjà bénéficiaire de cette cotisation");
        }
        ContributionBeneficiary beneficiary = new ContributionBeneficiary(definition, member);
        return BeneficiaryResponse.from(beneficiaryRepository.save(beneficiary));
    }

    public List<BeneficiaryResponse> listBeneficiaries(UUID definitionId) {
        getVisibleOrThrow(definitionId);
        return beneficiaryRepository.findByContributionDefinitionId(definitionId).stream()
                .map(BeneficiaryResponse::from)
                .toList();
    }

    public ContributionPeriodResponse addPeriod(UUID definitionId, ContributionPeriodRequest request) {
        ContributionDefinition definition = getDefinitionOrThrow(definitionId);
        Session session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        if (periodRepository.existsByContributionDefinitionIdAndSessionId(definitionId, session.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette cotisation est déjà rattachée à cette séance");
        }
        ContributionPeriod period = new ContributionPeriod(definition, session, request.dueDate());
        return ContributionPeriodResponse.from(periodRepository.save(period));
    }

    public List<ContributionPeriodResponse> listPeriods(UUID definitionId) {
        getVisibleOrThrow(definitionId);
        return periodRepository.findByContributionDefinitionId(definitionId).stream()
                .map(ContributionPeriodResponse::from)
                .toList();
    }

    /**
     * Situation de paiement de chaque participant pour une période (CLAUDE.md §13),
     * calculée comme ContributionPeriodStatusService (paiements partiels inclus).
     * Accessible à quiconque peut voir la cotisation (§11).
     * DÉCISION À VALIDER : un membre voit ainsi la situation (statut et montants,
     * sans détail de transaction) des autres participants d'une cotisation visible.
     */
    @Transactional(readOnly = true)
    public List<ParticipantPaymentStatusResponse> listParticipantStatuses(UUID definitionId, UUID periodId) {
        ContributionDefinition definition = getVisibleOrThrow(definitionId);
        ContributionPeriod period = periodRepository.findById(periodId)
                .filter(p -> p.getContributionDefinition().getId().equals(definitionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Période introuvable"));
        BigDecimal due = definition.getAmountMode() == AmountMode.FIXED ? definition.getAmount() : null;

        return participantRepository.findByContributionDefinitionId(definitionId).stream()
                .map(participant -> {
                    UUID memberId = participant.getMember().getId();
                    return new ParticipantPaymentStatusResponse(
                            memberId,
                            participant.getMember().getFullName(),
                            periodStatusService.resolve(period, memberId),
                            due,
                            periodStatusService.validatedAmount(period, memberId),
                            transactionRepository.sumAmountByMemberAndPeriodAndStatus(
                                    memberId, periodId, ContributionTransactionStatus.PENDING),
                            periodStatusService.remainingAmount(period, memberId));
                })
                .sorted(java.util.Comparator.comparing(ParticipantPaymentStatusResponse::memberFullName,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private ContributionDefinitionResponse toResponse(ContributionDefinition definition) {
        return ContributionDefinitionResponse.from(definition, hasPayments(definition.getId()));
    }

    private boolean hasPayments(UUID definitionId) {
        return transactionRepository.existsByContributionPeriod_ContributionDefinition_Id(definitionId);
    }

    private static void validateAmount(ContributionDefinitionRequest request) {
        if (request.amountMode() == AmountMode.FIXED
                && (request.amount() == null || request.amount().signum() <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un montant fixe strictement positif est requis pour une cotisation à montant fixe");
        }
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    private ContributionDefinition getDefinitionOrThrow(UUID id) {
        return definitionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cotisation introuvable"));
    }

    private ContributionDefinition getVisibleOrThrow(UUID id) {
        ContributionDefinition definition = getDefinitionOrThrow(id);
        if (!isVisibleToCurrentUser(definition)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cotisation introuvable");
        }
        return definition;
    }

    private Member getMemberOrThrow(UUID memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
    }

    private boolean isVisibleToCurrentUser(ContributionDefinition definition) {
        if (currentUserProvider.hasAnyRole("ADMIN", "TRESORIER")) {
            return true;
        }
        if (definition.getStatus() != ContributionStatus.ACTIVE) {
            return false;
        }
        if (definition.getVisibility() == Visibility.PUBLIC) {
            return true;
        }
        return currentUserProvider.currentMemberId()
                .map(memberId -> participantRepository.existsByContributionDefinitionIdAndMemberId(definition.getId(), memberId))
                .orElse(false);
    }
}
