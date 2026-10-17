package cm.aekd.tontine.session;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionDefinitionRepository;
import cm.aekd.tontine.contribution.ContributionParticipant;
import cm.aekd.tontine.contribution.ContributionParticipantRepository;
import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionPeriodRepository;
import cm.aekd.tontine.contribution.ContributionStatus;
import cm.aekd.tontine.contribution.ContributionTransactionRepository;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.notification.NotificationService;
import cm.aekd.tontine.notification.NotificationType;
import cm.aekd.tontine.payout.SessionPayoutRepository;
import cm.aekd.tontine.sanction.AppliedSanctionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final ContributionDefinitionRepository definitionRepository;
    private final ContributionPeriodRepository periodRepository;
    private final ContributionParticipantRepository participantRepository;
    private final NotificationService notificationService;
    private final SessionPayoutRepository payoutRepository;

    public SessionService(SessionRepository sessionRepository,
                          SessionBeneficiaryRepository beneficiaryRepository,
                          MemberRepository memberRepository,
                          AuditLogService auditLogService,
                          ContributionTransactionRepository transactionRepository,
                          AppliedSanctionRepository appliedSanctionRepository,
                          ContributionDefinitionRepository definitionRepository,
                          ContributionPeriodRepository periodRepository,
                          ContributionParticipantRepository participantRepository,
                          NotificationService notificationService,
                          SessionPayoutRepository payoutRepository) {
        this.payoutRepository = payoutRepository;
        this.participantRepository = participantRepository;
        this.notificationService = notificationService;
        this.sessionRepository = sessionRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.memberRepository = memberRepository;
        this.auditLogService = auditLogService;
        this.transactionRepository = transactionRepository;
        this.appliedSanctionRepository = appliedSanctionRepository;
        this.definitionRepository = definitionRepository;
        this.periodRepository = periodRepository;
    }

    public SessionResponse create(SessionRequest request) {
        checkSameMonth(request, null);
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

        if (request.contributions() != null) {
            notifyNewSession(session, syncContributions(session, request.contributions()));
        }

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
        checkSameMonth(request, id);
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
        List<SessionBeneficiary> removed = current.stream()
                .filter(b -> !wanted.contains(b.getMember().getId()))
                .toList();
        // Un bénéficiaire qui a déjà reçu une remise de la cagnotte reste attaché à
        // la séance (aucune donnée financière ne disparaît, n° 35 et 46).
        for (SessionBeneficiary beneficiary : removed) {
            if (payoutRepository.existsBySessionIdAndMemberId(id, beneficiary.getMember().getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Impossible de retirer " + beneficiary.getMember().getFullName()
                                + " des bénéficiaires : une remise de la cagnotte lui a déjà été faite.");
            }
        }
        removed.forEach(beneficiaryRepository::delete);
        Set<UUID> kept = current.stream().map(b -> b.getMember().getId()).collect(Collectors.toSet());
        beneficiaries.stream()
                .filter(member -> !kept.contains(member.getId()))
                .forEach(member -> beneficiaryRepository.save(new SessionBeneficiary(session, member)));
        beneficiaryRepository.flush();

        auditLogService.record(AuditAction.SESSION_UPDATED, "Session", session.getId(),
                "Modification de la séance \"" + session.getLabel() + "\" du "
                        + session.getSessionDate().format(AUDIT_DATE) + " à " + session.getLocation()
                        + " (récepteur : " + host.getFullName() + ")");

        if (request.contributions() != null) {
            for (ContributionPeriod added : syncContributions(session, request.contributions())) {
                ContributionDefinition definition = added.getContributionDefinition();
                notificationService.notifyMembers(participantsOf(added),NotificationType.SESSION_CONTRIBUTION_ADDED,
                        "Nouvelle cotisation : " + definition.getName(),
                        "Ajoutée à la séance \"" + session.getLabel() + "\" du "
                                + session.getSessionDate().format(AUDIT_DATE) + dueDateSuffix(added) + ".",
                        "/my-contributions");
            }
        }

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
        if (payoutRepository.existsBySessionId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer cette séance : la cagnotte a déjà été remise à un bénéficiaire.");
        }

        String description = "Suppression de la séance \"" + session.getLabel() + "\" du "
                + session.getSessionDate().format(AUDIT_DATE);
        sessionRepository.deleteCascadeById(id);
        auditLogService.record(AuditAction.SESSION_DELETED, "Session", id, description);
    }

    /**
     * Décision n° 42 (docs/decisions.md) : les cotisations d'une séance sont
     * choisies parmi les cotisations existantes, la liste reçue remplaçant les
     * rattachements actuels (une ContributionPeriod par cotisation).
     * <ul>
     *   <li>ajout : seule une cotisation ACTIVE peut être rattachée ;</li>
     *   <li>retrait : refusé dès qu'un paiement ou une sanction existe pour cette
     *       cotisation sur cette séance (même règle que la suppression, n° 35) ;</li>
     *   <li>cotisation conservée : sa date limite est mise à jour.</li>
     * </ul>
     */
    /**
     * Décision n° 44 : chaque participant d'au moins une cotisation de la séance est
     * notifié une fois, avec le nombre de cotisations qui le concernent.
     */
    private void notifyNewSession(Session session, List<ContributionPeriod> periods) {
        Map<UUID, Member> members = new LinkedHashMap<>();
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        for (ContributionPeriod period : periods) {
            for (Member member : participantsOf(period)) {
                members.putIfAbsent(member.getId(), member);
                counts.merge(member.getId(), 1, Integer::sum);
            }
        }
        members.values().forEach(member -> {
            int count = counts.get(member.getId());
            notificationService.notifyMember(member, NotificationType.SESSION_CREATED,
                    "Nouvelle séance : " + session.getLabel(),
                    "Le " + session.getSessionDate().format(AUDIT_DATE) + " – " + session.getLocation() + ". "
                            + count + " cotisation" + (count > 1 ? "s vous concernent." : " vous concerne."),
                    "/sessions/" + session.getId());
        });
    }

    private List<Member> participantsOf(ContributionPeriod period) {
        return participantRepository.findForPeriod(period).stream()
                .map(ContributionParticipant::getMember)
                .toList();
    }

    private static String dueDateSuffix(ContributionPeriod period) {
        return period.getDueDate() != null ? ", à payer avant le " + period.getDueDate().format(AUDIT_DATE) : "";
    }

    /** @return les périodes ajoutées (cotisations nouvellement rattachées). */
    private List<ContributionPeriod> syncContributions(Session session, List<SessionContributionRequest> requests) {
        Map<UUID, LocalDate> wanted = new LinkedHashMap<>();
        for (SessionContributionRequest request : requests) {
            if (wanted.containsKey(request.contributionDefinitionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Une même cotisation ne peut être rattachée qu'une fois à la séance");
            }
            wanted.put(request.contributionDefinitionId(), request.dueDate());
        }

        List<ContributionPeriod> current = periodRepository.findBySessionId(session.getId());
        for (ContributionPeriod period : current) {
            ContributionDefinition definition = period.getContributionDefinition();
            if (!wanted.containsKey(definition.getId())) {
                removePeriod(session, period);
            } else {
                LocalDate dueDate = wanted.get(definition.getId());
                if (!Objects.equals(dueDate, period.getDueDate())) {
                    period.setDueDate(dueDate);
                    periodRepository.save(period);
                    auditLogService.record(AuditAction.SESSION_CONTRIBUTION_DUE_DATE_CHANGED, "Session",
                            session.getId(), "Date limite de la cotisation \"" + definition.getName()
                                    + "\" pour la séance \"" + session.getLabel() + "\" : " + formatDueDate(dueDate));
                }
            }
        }

        Set<UUID> attached = current.stream()
                .map(period -> period.getContributionDefinition().getId())
                .collect(Collectors.toSet());
        List<ContributionPeriod> added = new ArrayList<>();
        wanted.forEach((definitionId, dueDate) -> {
            if (attached.contains(definitionId)) {
                return;
            }
            ContributionDefinition definition = definitionRepository.findById(definitionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cotisation introuvable"));
            if (definition.getStatus() != ContributionStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La cotisation \"" + definition.getName() + "\" n'est pas active : "
                                + "activez-la avant de la rattacher à une séance.");
            }
            added.add(periodRepository.save(new ContributionPeriod(definition, session, dueDate)));
            auditLogService.record(AuditAction.SESSION_CONTRIBUTION_ADDED, "Session", session.getId(),
                    "Rattachement de la cotisation \"" + definition.getName() + "\" à la séance \""
                            + session.getLabel() + "\" (date limite : " + formatDueDate(dueDate) + ")");
        });
        periodRepository.flush();
        return added;
    }

    private void removePeriod(Session session, ContributionPeriod period) {
        String name = period.getContributionDefinition().getName();
        if (transactionRepository.existsByContributionPeriod_Id(period.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de retirer la cotisation \"" + name + "\" de cette séance : "
                            + "des paiements ont déjà été déclarés.");
        }
        if (appliedSanctionRepository.existsByContributionPeriod_Id(period.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de retirer la cotisation \"" + name + "\" de cette séance : "
                            + "des sanctions ont été appliquées.");
        }
        periodRepository.delete(period);
        auditLogService.record(AuditAction.SESSION_CONTRIBUTION_REMOVED, "Session", session.getId(),
                "Retrait de la cotisation \"" + name + "\" de la séance \"" + session.getLabel() + "\"");
    }

    private static String formatDueDate(LocalDate dueDate) {
        return dueDate != null ? dueDate.format(AUDIT_DATE) : "aucune";
    }

    private Session getSessionOrThrow(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
    }

    /**
     * Décision n° 68 : une 2e séance dans le même mois est possible (séance exceptionnelle),
     * mais seulement après confirmation explicite. Sans elle : 428, avec les séances déjà prévues.
     */
    private void checkSameMonth(SessionRequest request, UUID excludedSessionId) {
        if (request.confirmSameMonth() || request.date() == null) {
            return;
        }
        YearMonth month = YearMonth.from(request.date());
        List<Session> sameMonth = sessionRepository
                .findBySessionDateBetweenOrderBySessionDate(month.atDay(1), month.atEndOfMonth()).stream()
                .filter(s -> !s.getId().equals(excludedSessionId))
                .toList();
        if (!sameMonth.isEmpty()) {
            String existing = sameMonth.stream()
                    .map(s -> "« " + s.getLabel() + " » du " + s.getSessionDate().format(AUDIT_DATE))
                    .collect(Collectors.joining(", "));
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "Une séance existe déjà ce mois-ci : " + existing + ". Confirmez pour en créer une autre.");
        }
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
