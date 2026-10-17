package cm.aekd.tontine.tournament;

import cm.aekd.tontine.contribution.AmountMode;
import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionDefinitionRepository;
import cm.aekd.tontine.contribution.ContributionFrequency;
import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionPeriodRepository;
import cm.aekd.tontine.contribution.ContributionStatus;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.contribution.Visibility;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.session.SessionBeneficiaryResponse;
import cm.aekd.tontine.session.SessionResponse;
import cm.aekd.tontine.session.SessionService;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TournamentServiceTest {

    /** Novembre 2026, 1er samedi : 7/11/2026, puis 5/12/2026 et 2/01/2027. */
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private cm.aekd.tontine.share.WhatsappShareService shareService;

    @Autowired
    private cm.aekd.tontine.document.TournamentDocumentService documentService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ContributionDefinitionRepository definitionRepository;

    @Autowired
    private ContributionPeriodRepository periodRepository;

    private Member alice;
    private Member bruno;
    private Member chantal;
    private ContributionDefinition cotisation;

    @BeforeEach
    void setUp() {
        alice = createMember("tournalice", "Alice Tournoi");
        bruno = createMember("tournbruno", "Bruno Tournoi");
        chantal = createMember("tournchantal", "Chantal Tournoi");
        cotisation = activeDefinition("Cotisation 50 000");
    }

    @Test
    void createComputesPlannedDatesWithoutGeneratingBeforeTheWindowOpens() {
        TournamentResponse created = tournamentService.create(request(30, List.of(cotisation.getId())), TODAY);

        assertThat(created.status()).isEqualTo(TournamentStatus.ACTIVE);
        assertThat(created.slots()).extracting(TournamentResponse.Slot::plannedDate).containsExactly(
                LocalDate.of(2026, 11, 7), LocalDate.of(2026, 12, 5), LocalDate.of(2027, 1, 2));
        assertThat(created.slots()).extracting(TournamentResponse.Slot::memberId)
                .containsExactly(alice.getId(), bruno.getId(), chantal.getId());
        assertThat(created.slots()).noneMatch(TournamentResponse.Slot::generated);
        assertThat(created.contributions()).extracting(TournamentResponse.Contribution::id)
                .containsExactly(cotisation.getId());
    }

    @Test
    void orderOfPassageCanBeSharedOnWhatsappAndExportedAsPdf() {
        // decisions.md n° 59.
        UUID id = tournamentService.create(request(30, List.of(cotisation.getId())), TODAY).id();

        String message = shareService.tournamentMessage(id);
        assertThat(message).contains("*Ordre de passage – ", "Le 1er samedi du mois",
                "1. Alice Tournoi – 07/11/2026", "3. Chantal Tournoi – 02/01/2027");

        byte[] pdf = documentService.order(id).content();
        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    @Test
    void generatesTheNextSessionWhenItsWindowOpensWithHostBeneficiaryAndContributions() {
        UUID id = tournamentService.create(request(30, List.of(cotisation.getId())), TODAY).id();

        tournamentService.generateDueSessions(id, LocalDate.of(2026, 10, 7));
        assertThat(tournamentService.findById(id).slots()).noneMatch(TournamentResponse.Slot::generated);

        tournamentService.generateDueSessions(id, LocalDate.of(2026, 10, 8));
        TournamentResponse.Slot first = tournamentService.findById(id).slots().get(0);
        assertThat(first.generated()).isTrue();
        assertThat(first.sessionLabel()).isEqualTo("Séance du 7 novembre 2026");

        SessionResponse session = sessionService.findById(first.sessionId());
        assertThat(session.date()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(session.location()).isEqualTo("Chez Alice Tournoi");
        assertThat(session.hostMember().memberId()).isEqualTo(alice.getId());
        assertThat(session.beneficiaries()).extracting(SessionBeneficiaryResponse::memberId)
                .containsExactly(alice.getId());
        assertThat(periodRepository.findBySessionId(session.id()))
                .extracting(p -> p.getContributionDefinition().getId())
                .containsExactly(cotisation.getId());
        // Décision n° 67 : échéance par défaut = date de la séance.
        assertThat(periodRepository.findBySessionId(session.id()))
                .extracting(p -> p.getDueDate())
                .containsExactly(LocalDate.of(2026, 11, 7));

        // Nouveau passage le même jour : rien de plus.
        tournamentService.generateDueSessions(id, LocalDate.of(2026, 10, 8));
        assertThat(tournamentService.findById(id).slots()).filteredOn(TournamentResponse.Slot::generated).hasSize(1);
    }

    @Test
    void keepsOnlyOneUpcomingSessionAtATime() {
        // Délai de 40 jours : la séance de décembre (5/12) serait due dès le 26/10.
        UUID id = tournamentService.create(request(40, List.of()), TODAY).id();
        assertThat(tournamentService.findById(id).slots().get(0).generated()).isTrue();

        tournamentService.generateDueSessions(id, LocalDate.of(2026, 11, 1));
        assertThat(tournamentService.findById(id).slots().get(1).generated()).isFalse();

        tournamentService.generateDueSessions(id, LocalDate.of(2026, 11, 8));
        assertThat(tournamentService.findById(id).slots().get(1).generated()).isTrue();
    }

    @Test
    void completesAfterTheLastPassageAndCatchesUpMissedSessions() {
        UUID id = tournamentService.create(request(0, List.of()), TODAY).id();

        // Serveur arrêté pendant des mois : les passages manqués sont rattrapés.
        tournamentService.generateDueSessions(id, LocalDate.of(2027, 1, 10));

        TournamentResponse tournament = tournamentService.findById(id);
        assertThat(tournament.slots()).allMatch(TournamentResponse.Slot::generated);
        assertThat(tournament.status()).isEqualTo(TournamentStatus.COMPLETED);
    }

    @Test
    void skipsContributionsDeactivatedSinceCreation() {
        UUID id = tournamentService.create(request(30, List.of(cotisation.getId())), TODAY).id();
        cotisation.setStatus(ContributionStatus.INACTIVE);
        definitionRepository.saveAndFlush(cotisation);

        tournamentService.generateDueSessions(id, LocalDate.of(2026, 10, 8));

        UUID sessionId = tournamentService.findById(id).slots().get(0).sessionId();
        assertThat(periodRepository.findBySessionId(sessionId)).isEmpty();
    }

    @Test
    void onlyOneTournamentCanRunAtATime() {
        tournamentService.create(request(30, List.of()), TODAY);

        assertThatThrownBy(() -> tournamentService.create(request(30, List.of()), TODAY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("déjà en cours");
    }

    @Test
    void rejectsInvalidRequests() {
        // Première séance (3/10/2026) déjà passée.
        assertThatThrownBy(() -> tournamentService.create(new TournamentRequest("Passé", YearMonth.of(2026, 10), 1,
                DayOfWeek.SATURDAY, 30, List.of(alice.getId()), List.of()), TODAY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("déjà passée");

        assertThatThrownBy(() -> tournamentService.create(new TournamentRequest("Doublon", NOVEMBER, 1,
                DayOfWeek.SATURDAY, 30, List.of(alice.getId(), alice.getId()), List.of()), TODAY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("qu'une fois");

        assertThatThrownBy(() -> tournamentService.create(new TournamentRequest("Rang", NOVEMBER, 5,
                DayOfWeek.SATURDAY, 30, List.of(alice.getId()), List.of()), TODAY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Rang");

        ContributionDefinition draft = definitionRepository.save(new ContributionDefinition("Voir bébé", null, null,
                AmountMode.VOLUNTARY, ContributionFrequency.MONTHLY, false, Visibility.PUBLIC,
                FundDestination.TONTINE_FUND));
        assertThatThrownBy(() -> tournamentService.create(request(30, List.of(draft.getId())), TODAY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("n'est pas active");
    }

    @Test
    void stoppedTournamentNoLongerGeneratesSessions() {
        UUID id = tournamentService.create(request(30, List.of()), TODAY).id();

        assertThat(tournamentService.stop(id).status()).isEqualTo(TournamentStatus.STOPPED);
        tournamentService.generateDueSessions(id, LocalDate.of(2026, 10, 8));

        assertThat(tournamentService.findById(id).slots()).noneMatch(TournamentResponse.Slot::generated);
        // Un nouveau tournoi peut alors être créé.
        assertThat(tournamentService.create(request(30, List.of()), TODAY).status())
                .isEqualTo(TournamentStatus.ACTIVE);
    }

    @Test
    void deleteIsRefusedOnceASessionWasGenerated() {
        UUID untouched = tournamentService.create(request(30, List.of()), TODAY).id();
        tournamentService.delete(untouched);
        assertThatThrownBy(() -> tournamentService.findById(untouched)).isInstanceOf(ResponseStatusException.class);

        UUID used = tournamentService.create(request(40, List.of()), TODAY).id();
        assertThatThrownBy(() -> tournamentService.delete(used))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("arrêtez-le");
    }

    @Test
    void updateChangesNameLeadDaysAndDefaultContributionsThenGeneratesIfDue() {
        UUID id = tournamentService.create(request(30, List.of()), TODAY).id();

        TournamentResponse updated = tournamentService.update(id,
                new TournamentUpdateRequest("Tournoi 2026-2027", 40, List.of(cotisation.getId())), TODAY);

        assertThat(updated.name()).isEqualTo("Tournoi 2026-2027");
        assertThat(updated.leadDays()).isEqualTo(40);
        assertThat(updated.contributions()).extracting(TournamentResponse.Contribution::id)
                .containsExactly(cotisation.getId());
        // Avec 40 jours, la séance du 7/11 est due dès le 28/09 : générée immédiatement.
        UUID sessionId = updated.slots().get(0).sessionId();
        assertThat(sessionId).isNotNull();
        assertThat(periodRepository.findBySessionId(sessionId)).extracting(ContributionPeriod::getContributionDefinition)
                .extracting(ContributionDefinition::getId)
                .containsExactly(cotisation.getId());
    }

    private TournamentRequest request(int leadDays, List<UUID> contributionIds) {
        return new TournamentRequest("Tournoi test", NOVEMBER, 1, DayOfWeek.SATURDAY, leadDays,
                List.of(alice.getId(), bruno.getId(), chantal.getId()), contributionIds);
    }

    private Member createMember(String emailPrefix, String fullName) {
        Role role = roleRepository.findByName(RoleName.MEMBRE).orElseThrow();
        User user = new User(emailPrefix + "@test.aekd.cm", passwordEncoder.encode("x"));
        user.addRole(role);
        userRepository.save(user);
        return memberRepository.save(new Member(user, fullName, LocalDate.now()));
    }

    private ContributionDefinition activeDefinition(String name) {
        ContributionDefinition definition = new ContributionDefinition(name, null, new BigDecimal(50000),
                AmountMode.FIXED, ContributionFrequency.MONTHLY, true, Visibility.PUBLIC, FundDestination.TONTINE_FUND);
        definition.setStatus(ContributionStatus.ACTIVE);
        return definitionRepository.save(definition);
    }
}
