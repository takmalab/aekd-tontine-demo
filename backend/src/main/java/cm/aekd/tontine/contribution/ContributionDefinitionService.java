package cm.aekd.tontine.contribution;

import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.session.Session;
import cm.aekd.tontine.session.SessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
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

    public ContributionDefinitionService(ContributionDefinitionRepository definitionRepository,
                                          ContributionParticipantRepository participantRepository,
                                          ContributionBeneficiaryRepository beneficiaryRepository,
                                          ContributionPeriodRepository periodRepository,
                                          MemberRepository memberRepository,
                                          SessionRepository sessionRepository,
                                          CurrentUserProvider currentUserProvider) {
        this.definitionRepository = definitionRepository;
        this.participantRepository = participantRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.periodRepository = periodRepository;
        this.memberRepository = memberRepository;
        this.sessionRepository = sessionRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public ContributionDefinitionResponse create(ContributionDefinitionRequest request) {
        if (request.amountMode() == AmountMode.FIXED
                && (request.amount() == null || request.amount().signum() <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un montant fixe strictement positif est requis pour une cotisation à montant fixe");
        }

        ContributionDefinition definition = new ContributionDefinition(
                request.name(), request.description(), request.amount(), request.amountMode(),
                request.frequency(), request.mandatory(), request.visibility(), request.fundDestination()
        );
        return ContributionDefinitionResponse.from(definitionRepository.save(definition));
    }

    public List<ContributionDefinitionResponse> findAll() {
        return definitionRepository.findAll().stream()
                .filter(this::isVisibleToCurrentUser)
                .map(ContributionDefinitionResponse::from)
                .toList();
    }

    public ContributionDefinitionResponse findById(UUID id) {
        return ContributionDefinitionResponse.from(getVisibleOrThrow(id));
    }

    public ContributionDefinitionResponse activate(UUID id) {
        ContributionDefinition definition = getDefinitionOrThrow(id);
        if (definition.getStatus() == ContributionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette cotisation est déjà active");
        }
        definition.setStatus(ContributionStatus.ACTIVE);
        return ContributionDefinitionResponse.from(definitionRepository.save(definition));
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
