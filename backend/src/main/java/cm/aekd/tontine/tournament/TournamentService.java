package cm.aekd.tontine.tournament;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionDefinitionRepository;
import cm.aekd.tontine.contribution.ContributionStatus;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.notification.NotificationService;
import cm.aekd.tontine.notification.NotificationType;
import cm.aekd.tontine.session.SessionContributionRequest;
import cm.aekd.tontine.session.SessionRepository;
import cm.aekd.tontine.session.SessionRequest;
import cm.aekd.tontine.session.SessionResponse;
import cm.aekd.tontine.session.SessionService;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tournois : génération automatique des séances (docs/decisions.md n° 43).
 * <ul>
 *   <li>un seul tournoi actif à la fois (sinon deux séances seraient créées chaque mois) ;</li>
 *   <li>un passage par mois, à la date « n-ième jour de semaine du mois » ;</li>
 *   <li>la séance d'un passage est créée {@code leadDays} jours avant sa date, une seule
 *       séance à venir à la fois ; récepteur et bénéficiaire = membre du passage,
 *       lieu « Chez &lt;récepteur&gt; », libellé « Séance de &lt;mois&gt; &lt;année&gt; » ;</li>
 *   <li>quand tous les membres sont passés, le tournoi est terminé (pas de nouveau cycle).</li>
 * </ul>
 */
@Service
@Transactional
public class TournamentService {

    static final ZoneId ZONE = ZoneId.of("Africa/Douala");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TournamentRepository tournamentRepository;
    private final MemberRepository memberRepository;
    private final ContributionDefinitionRepository definitionRepository;
    private final SessionService sessionService;
    private final SessionRepository sessionRepository;
    private final AuditLogService auditLogService;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public TournamentService(TournamentRepository tournamentRepository,
                             MemberRepository memberRepository,
                             ContributionDefinitionRepository definitionRepository,
                             SessionService sessionService,
                             SessionRepository sessionRepository,
                             AuditLogService auditLogService,
                             CurrentUserProvider currentUserProvider,
                             UserRepository userRepository,
                             NotificationService notificationService) {
        this.notificationService = notificationService;
        this.tournamentRepository = tournamentRepository;
        this.memberRepository = memberRepository;
        this.definitionRepository = definitionRepository;
        this.sessionService = sessionService;
        this.sessionRepository = sessionRepository;
        this.auditLogService = auditLogService;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
    }

    public TournamentResponse create(TournamentRequest request) {
        return create(request, LocalDate.now(ZONE));
    }

    TournamentResponse create(TournamentRequest request, LocalDate today) {
        if (!TournamentCalendar.ALLOWED_WEEKS.contains(request.weekOfMonth())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rang du jour invalide : choisissez 1er, 2e, 3e, 4e ou dernier.");
        }
        if (tournamentRepository.existsByStatus(TournamentStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un tournoi est déjà en cours : arrêtez-le avant d'en créer un nouveau.");
        }
        LocalDate firstDate = TournamentCalendar.dateFor(request.startMonth(), request.weekOfMonth(),
                request.dayOfWeek());
        if (firstDate.isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La première séance (" + firstDate.format(DATE) + ") est déjà passée : "
                            + "choisissez un mois de départ ultérieur.");
        }

        List<Member> order = getOrderedMembers(request.memberIds());
        User creator = currentUserProvider.currentUserId().flatMap(userRepository::findById).orElse(null);
        Tournament tournament = new Tournament(request.name().trim(), request.startMonth(), request.weekOfMonth(),
                request.dayOfWeek(), request.leadDays(), creator);
        for (int position = 0; position < order.size(); position++) {
            tournament.addSlot(new TournamentSlot(tournament, position, order.get(position)));
        }
        tournament.getContributions().addAll(getActiveContributions(request.contributionDefinitionIds()));
        tournament = tournamentRepository.save(tournament);

        auditLogService.record(AuditAction.TOURNAMENT_CREATED, "Tournament", tournament.getId(),
                "Création du tournoi \"" + tournament.getName() + "\" : " + order.size() + " passage(s), "
                        + "première séance le " + firstDate.format(DATE));

        generateDueSessions(tournament, today);
        return TournamentResponse.from(tournament);
    }

    @Transactional(readOnly = true)
    public List<TournamentResponse> findAll() {
        return tournamentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(TournamentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TournamentResponse findById(UUID id) {
        return TournamentResponse.from(getOrThrow(id));
    }

    public TournamentResponse update(UUID id, TournamentUpdateRequest request) {
        return update(id, request, LocalDate.now(ZONE));
    }

    TournamentResponse update(UUID id, TournamentUpdateRequest request, LocalDate today) {
        Tournament tournament = getOrThrow(id);
        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seul un tournoi en cours peut être modifié.");
        }
        // Une cotisation désactivée depuis peut rester dans la liste : elle sera
        // simplement ignorée à la génération. Seules les nouvelles doivent être actives.
        Set<UUID> current = new HashSet<>();
        tournament.getContributions().forEach(c -> current.add(c.getId()));
        List<UUID> requested = Optional.ofNullable(request.contributionDefinitionIds()).orElse(List.of());
        List<ContributionDefinition> contributions = new ArrayList<>();
        for (UUID definitionId : new LinkedHashSet<>(requested)) {
            ContributionDefinition definition = getContributionOrThrow(definitionId);
            if (!current.contains(definitionId)) {
                requireActive(definition);
            }
            contributions.add(definition);
        }

        tournament.setName(request.name().trim());
        tournament.setLeadDays(request.leadDays());
        tournament.getContributions().clear();
        tournament.getContributions().addAll(contributions);
        tournament = tournamentRepository.save(tournament);

        auditLogService.record(AuditAction.TOURNAMENT_UPDATED, "Tournament", tournament.getId(),
                "Modification du tournoi \"" + tournament.getName() + "\" (création " + tournament.getLeadDays()
                        + " jour(s) avant, " + contributions.size() + " cotisation(s) par défaut)");

        // Un délai allongé peut rendre la prochaine séance immédiatement due.
        generateDueSessions(tournament, today);
        return TournamentResponse.from(tournament);
    }

    /** Arrêt définitif : plus aucune séance n'est générée, les séances déjà créées restent. */
    public TournamentResponse stop(UUID id) {
        Tournament tournament = getOrThrow(id);
        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce tournoi n'est pas en cours.");
        }
        tournament.setStatus(TournamentStatus.STOPPED);
        long generated = tournament.getSlots().stream().filter(TournamentSlot::isGenerated).count();
        auditLogService.record(AuditAction.TOURNAMENT_STOPPED, "Tournament", tournament.getId(),
                "Arrêt du tournoi \"" + tournament.getName() + "\" après " + generated + " séance(s) sur "
                        + tournament.getSlots().size());
        return TournamentResponse.from(tournamentRepository.save(tournament));
    }

    /** Suppression possible seulement si aucune séance n'a encore été générée. */
    public void delete(UUID id) {
        Tournament tournament = getOrThrow(id);
        if (tournament.getSlots().stream().anyMatch(TournamentSlot::isGenerated)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Des séances ont déjà été générées par ce tournoi : arrêtez-le plutôt que de le supprimer.");
        }
        String description = "Suppression du tournoi \"" + tournament.getName() + "\"";
        tournamentRepository.delete(tournament);
        auditLogService.record(AuditAction.TOURNAMENT_DELETED, "Tournament", id, description);
    }

    @Transactional(readOnly = true)
    public List<UUID> activeTournamentIds() {
        return tournamentRepository.findByStatus(TournamentStatus.ACTIVE).stream().map(Tournament::getId).toList();
    }

    /** Point d'entrée de la tâche planifiée : une transaction par tournoi. */
    public void generateDueSessions(UUID tournamentId, LocalDate today) {
        tournamentRepository.findById(tournamentId)
                .filter(t -> t.getStatus() == TournamentStatus.ACTIVE)
                .ifPresent(t -> generateDueSessions(t, today));
    }

    /**
     * Crée les séances dont la fenêtre de création est ouverte. Les passages dont la
     * date est déjà dépassée (serveur arrêté plusieurs jours) sont rattrapés ; au plus
     * une séance à venir existe à la fois.
     */
    private void generateDueSessions(Tournament tournament, LocalDate today) {
        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            return;
        }
        for (TournamentSlot slot : List.copyOf(tournament.getSlots())) {
            if (slot.isGenerated()) {
                if (!tournament.plannedDate(slot).isBefore(today)) {
                    break; // une séance à venir existe déjà
                }
                continue;
            }
            LocalDate date = tournament.plannedDate(slot);
            if (date.minusDays(tournament.getLeadDays()).isAfter(today)) {
                break; // fenêtre de création pas encore ouverte
            }
            generateSession(tournament, slot, date);
            if (!date.isBefore(today)) {
                break;
            }
        }
        // Terminé dès que la séance du dernier passage est créée : il n'y a plus rien à générer.
        if (tournament.getSlots().stream().allMatch(TournamentSlot::isGenerated)) {
            tournament.setStatus(TournamentStatus.COMPLETED);
            tournamentRepository.save(tournament);
            auditLogService.record(AuditAction.TOURNAMENT_COMPLETED, "Tournament", tournament.getId(),
                    "Tournoi \"" + tournament.getName() + "\" terminé : tous les membres de l'ordre de passage "
                            + "ont eu leur séance");
            notificationService.notifyStaff(NotificationType.TOURNAMENT_COMPLETED, "Tournoi terminé",
                    "Tous les membres du tournoi \"" + tournament.getName() + "\" ont eu leur séance. "
                            + "Créez un nouveau tournoi pour continuer la génération automatique.",
                    "/tournaments");
        }
    }

    private void generateSession(Tournament tournament, TournamentSlot slot, LocalDate date) {
        Member host = slot.getMember();
        List<SessionContributionRequest> contributions = tournament.getContributions().stream()
                .filter(c -> c.getStatus() == ContributionStatus.ACTIVE)
                // Décision n° 67 : échéance par défaut = date de la séance (modifiable ensuite sur la séance).
                .map(c -> new SessionContributionRequest(c.getId(), date))
                .toList();
        // La séance du passage est toujours créée, même si le mois en compte déjà une (décision n° 68) :
        // sinon le membre perdrait son tour.
        SessionResponse session = sessionService.create(new SessionRequest(
                TournamentCalendar.sessionLabel(date), date, "Chez " + host.getFullName(), host.getId(),
                List.of(host.getId()), contributions, true));

        // Entité gérée : la modification est enregistrée à la fin de la transaction.
        slot.markGenerated(sessionRepository.getReferenceById(session.id()));
        auditLogService.record(AuditAction.TOURNAMENT_SESSION_GENERATED, "Tournament", tournament.getId(),
                "Séance \"" + session.label() + "\" du " + date.format(DATE) + " générée par le tournoi \""
                        + tournament.getName() + "\" (passage " + (slot.getPosition() + 1) + " : "
                        + host.getFullName() + ", " + contributions.size() + " cotisation(s))");
        notificationService.notifyStaff(NotificationType.TOURNAMENT_SESSION_GENERATED, "Séance générée",
                "\"" + session.label() + "\" du " + date.format(DATE) + " chez " + host.getFullName()
                        + " a été créée par le tournoi \"" + tournament.getName() + "\".",
                "/sessions/" + session.id());
    }

    private List<Member> getOrderedMembers(List<UUID> memberIds) {
        if (new HashSet<>(memberIds).size() != memberIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un membre ne peut figurer qu'une fois dans l'ordre de passage.");
        }
        return memberIds.stream()
                .map(memberId -> memberRepository.findById(memberId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Membre introuvable")))
                .toList();
    }

    private List<ContributionDefinition> getActiveContributions(List<UUID> ids) {
        if (ids == null) {
            return List.of();
        }
        return new LinkedHashSet<>(ids).stream()
                .map(this::getContributionOrThrow)
                .peek(this::requireActive)
                .toList();
    }

    private ContributionDefinition getContributionOrThrow(UUID id) {
        return definitionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cotisation introuvable"));
    }

    private void requireActive(ContributionDefinition definition) {
        if (definition.getStatus() != ContributionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cotisation \"" + definition.getName() + "\" n'est pas active.");
        }
    }

    private Tournament getOrThrow(UUID id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournoi introuvable"));
    }
}
