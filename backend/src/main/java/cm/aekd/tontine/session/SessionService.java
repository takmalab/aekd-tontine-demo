package cm.aekd.tontine.session;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.contribution.ContributionTransactionRepository;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.sanction.AppliedSanctionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class SessionService {

    private static final DateTimeFormatter AUDIT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SessionRepository sessionRepository;
    private final SessionBeneficiaryRepository beneficiaryRepository;
    private final MemberRepository memberRepository;
    private final AuditLogService auditLogService;
    private final ContributionTransactionRepository transactionRepository;
    private final AppliedSanctionRepository appliedSanctionRepository;

    public SessionService(SessionRepository sessionRepository,
                          SessionBeneficiaryRepository beneficiaryRepository,
                          MemberRepository memberRepository,
                          AuditLogService auditLogService,
                          ContributionTransactionRepository transactionRepository,
                          AppliedSanctionRepository appliedSanctionRepository) {
        this.sessionRepository = sessionRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.memberRepository = memberRepository;
        this.auditLogService = auditLogService;
        this.transactionRepository = transactionRepository;
        this.appliedSanctionRepository = appliedSanctionRepository;
    }

    public SessionResponse create(SessionRequest request) {
        Member host = getHostOrThrow(request.hostMemberId());
        List<Member> beneficiaries = getBeneficiariesOrThrow(request.beneficiaryMemberIds());

        Session session = sessionRepository.save(
                new Session(request.label().trim(), request.date(), request.location().trim(), host));

        List<SessionBeneficiary> savedBeneficiaries = beneficiaries.stream()
                .map(member -> beneficiaryRepository.save(new SessionBeneficiary(session, member)))
                .toList();

        auditLogService.record(AuditAction.SESSION_CREATED, "Session", session.getId(),
                "Création de la séance \"" + session.getLabel() + "\" du "
                        + session.getSessionDate().format(AUDIT_DATE) + " à " + session.getLocation()
                        + " (récepteur : " + host.getFullName() + ")");

        return SessionResponse.from(session, savedBeneficiaries);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> findAll() {
        List<Session> sessions = sessionRepository.findAllByOrderBySessionDateDesc();
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<SessionBeneficiary>> beneficiariesBySession = beneficiaryRepository
                .findBySessionIdInOrderByCreatedAtAsc(sessions.stream().map(Session::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(b -> b.getSession().getId()));

        return sessions.stream()
                .map(session -> SessionResponse.from(session,
                        beneficiariesBySession.getOrDefault(session.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionResponse findById(UUID id) {
        Session session = getSessionOrThrow(id);
        return SessionResponse.from(session, beneficiaryRepository.findBySessionIdOrderByCreatedAtAsc(id));
    }

    /**
     * Tous les champs d'une séance sont modifiables : aucun montant n'en dépend
     * (les échéances sont portées par les périodes de cotisation).
     */
    public SessionResponse update(UUID id, SessionRequest request) {
        Session session = getSessionOrThrow(id);
        Member host = getHostOrThrow(request.hostMemberId());
        List<Member> beneficiaries = getBeneficiariesOrThrow(request.beneficiaryMemberIds());

        session.setLabel(request.label().trim());
        session.setSessionDate(request.date());
        session.setLocation(request.location().trim());
        session.setHostMember(host);

        // Mise à jour par différence (et non suppression puis recréation) : Hibernate
        // exécute les INSERT avant les DELETE au flush, ce qui violerait l'unicité
        // (séance, membre) pour un bénéficiaire conservé.
        Set<UUID> wanted = beneficiaries.stream().map(Member::getId).collect(Collectors.toSet());
        List<SessionBeneficiary> current = beneficiaryRepository.findBySessionIdOrderByCreatedAtAsc(id);
        current.stream()
                .filter(b -> !wanted.contains(b.getMember().getId()))
                .forEach(beneficiaryRepository::delete);
        Set<UUID> kept = current.stream().map(b -> b.getMember().getId()).collect(Collectors.toSet());
        beneficiaries.stream()
                .filter(member -> !kept.contains(member.getId()))
                .forEach(member -> beneficiaryRepository.save(new SessionBeneficiary(session, member)));
        beneficiaryRepository.flush();

        auditLogService.record(AuditAction.SESSION_UPDATED, "Session", session.getId(),
                "Modification de la séance \"" + session.getLabel() + "\" du "
                        + session.getSessionDate().format(AUDIT_DATE) + " à " + session.getLocation()
                        + " (récepteur : " + host.getFullName() + ")");

        return SessionResponse.from(sessionRepository.save(session),
                beneficiaryRepository.findBySessionIdOrderByCreatedAtAsc(id));
    }

    /**
     * Décision validée le 03/10/2026 (docs/decisions.md n° 35) : suppression refusée
     * dès qu'un paiement (quel que soit son statut) ou une sanction appliquée existe
     * sur une cotisation de la séance, pour qu'aucune donnée financière ne
     * disparaisse. Sinon, les rattachements de cotisations (périodes vides) et les
     * bénéficiaires de la séance sont supprimés avec elle (ON DELETE CASCADE).
     */
    public void delete(UUID id) {
        Session session = getSessionOrThrow(id);
        if (transactionRepository.existsByContributionPeriod_Session_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer cette séance : des paiements ont déjà été déclarés "
                            + "pour ses cotisations.");
        }
        if (appliedSanctionRepository.existsByContributionPeriod_Session_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer cette séance : des sanctions ont été appliquées "
                            + "sur ses cotisations.");
        }

        String description = "Suppression de la séance \"" + session.getLabel() + "\" du "
                + session.getSessionDate().format(AUDIT_DATE);
        sessionRepository.deleteCascadeById(id);
        auditLogService.record(AuditAction.SESSION_DELETED, "Session", id, description);
    }

    private Session getSessionOrThrow(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
    }

    private Member getHostOrThrow(UUID hostMemberId) {
        return memberRepository.findById(hostMemberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Membre récepteur introuvable"));
    }

    /** Un même membre n'est enregistré qu'une fois comme bénéficiaire de la séance. */
    private List<Member> getBeneficiariesOrThrow(List<UUID> memberIds) {
        return new LinkedHashSet<>(memberIds).stream()
                .map(memberId -> memberRepository.findById(memberId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Membre bénéficiaire introuvable")))
                .toList();
    }
}
