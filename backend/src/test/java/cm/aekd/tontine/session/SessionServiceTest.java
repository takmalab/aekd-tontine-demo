package cm.aekd.tontine.session;

import cm.aekd.tontine.contribution.AmountMode;
import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionDefinitionRepository;
import cm.aekd.tontine.contribution.ContributionFrequency;
import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionPeriodRepository;
import cm.aekd.tontine.contribution.ContributionTransaction;
import cm.aekd.tontine.contribution.ContributionTransactionRepository;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.contribution.PaymentOperator;
import cm.aekd.tontine.contribution.Visibility;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SessionServiceTest {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ContributionDefinitionRepository definitionRepository;

    @Autowired
    private ContributionPeriodRepository periodRepository;

    @Autowired
    private ContributionTransactionRepository transactionRepository;

    private Member host;
    private Member beneficiaryA;
    private Member beneficiaryB;

    @BeforeEach
    void setUp() {
        host = createMember("sesshost");
        beneficiaryA = createMember("sessbenefa");
        beneficiaryB = createMember("sessbenefb");
    }

    private Member createMember(String emailPrefix) {
        Role role = roleRepository.findByName(RoleName.MEMBRE).orElseThrow();
        User user = new User(emailPrefix + "@test.aekd.cm", passwordEncoder.encode("x"));
        user.addRole(role);
        userRepository.save(user);
        return memberRepository.save(new Member(user, "Test " + emailPrefix, LocalDate.now()));
    }

    private SessionRequest request(String label, LocalDate date, UUID hostId, List<UUID> beneficiaryIds) {
        return new SessionRequest(label, date, "Douala – Bonamoussadi", hostId, beneficiaryIds);
    }

    @Test
    void createsSessionWithDateLocationHostAndBeneficiaries() {
        SessionResponse response = sessionService.create(request("Séance d'octobre 2026",
                LocalDate.of(2026, 10, 3), host.getId(), List.of(beneficiaryA.getId(), beneficiaryB.getId())));

        assertThat(response.id()).isNotNull();
        assertThat(response.date()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(response.location()).isEqualTo("Douala – Bonamoussadi");
        assertThat(response.hostMember().memberId()).isEqualTo(host.getId());
        assertThat(response.hostMember().memberFullName()).isEqualTo("Test sesshost");
        assertThat(response.beneficiaries())
                .extracting(SessionBeneficiaryResponse::memberId)
                .containsExactly(beneficiaryA.getId(), beneficiaryB.getId());

        SessionResponse fetched = sessionService.findById(response.id());
        assertThat(fetched.hostMember().memberId()).isEqualTo(host.getId());
        assertThat(fetched.beneficiaries()).hasSize(2);

        List<SessionResponse> all = sessionService.findAll();
        SessionResponse listed = all.stream().filter(s -> s.id().equals(response.id())).findFirst().orElseThrow();
        assertThat(listed.beneficiaries()).hasSize(2);
    }

    @Test
    void duplicateBeneficiaryIdsAreStoredOnce() {
        SessionResponse response = sessionService.create(request("Séance doublons", LocalDate.of(2026, 11, 7),
                host.getId(), List.of(beneficiaryA.getId(), beneficiaryA.getId())));

        assertThat(response.beneficiaries()).hasSize(1);
    }

    @Test
    void hostCanAlsoBeBeneficiary() {
        SessionResponse response = sessionService.create(request("Séance hôte bénéficiaire",
                LocalDate.of(2026, 12, 5), host.getId(), List.of(host.getId())));

        assertThat(response.hostMember().memberId()).isEqualTo(host.getId());
        assertThat(response.beneficiaries()).extracting(SessionBeneficiaryResponse::memberId)
                .containsExactly(host.getId());
    }

    @Test
    void rejectsUnknownHostMember() {
        assertThatThrownBy(() -> sessionService.create(request("Séance hôte inconnu", LocalDate.of(2026, 10, 3),
                UUID.randomUUID(), List.of(beneficiaryA.getId()))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("récepteur");
    }

    @Test
    void rejectsUnknownBeneficiaryMember() {
        assertThatThrownBy(() -> sessionService.create(request("Séance bénéficiaire inconnu",
                LocalDate.of(2026, 10, 3), host.getId(), List.of(UUID.randomUUID()))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("bénéficiaire");
    }

    @Test
    void findAllIsSortedByDateDescending() {
        SessionResponse older = sessionService.create(request("Séance A", LocalDate.of(2030, 1, 4),
                host.getId(), List.of(beneficiaryA.getId())));
        SessionResponse newer = sessionService.create(request("Séance B", LocalDate.of(2030, 2, 1),
                host.getId(), List.of(beneficiaryB.getId())));

        List<UUID> ids = sessionService.findAll().stream().map(SessionResponse::id).toList();
        assertThat(ids.indexOf(newer.id())).isLessThan(ids.indexOf(older.id()));
    }

    @Test
    void legacySessionWithoutHostOrBeneficiariesIsStillReadable() {
        // Séance telle qu'elle existe après la migration V12 pour les données antérieures.
        Session legacy = sessionRepository.save(new Session("Séance ancienne", LocalDate.of(2026, 9, 1), null, null));

        SessionResponse response = sessionService.findById(legacy.getId());
        assertThat(response.date()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(response.location()).isNull();
        assertThat(response.hostMember()).isNull();
        assertThat(response.beneficiaries()).isEmpty();
    }

    @Test
    void migrationV12ReplacedDateRangeWithSingleDateAndAddedBeneficiaryTable() {
        List<String> sessionColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'session'", String.class);
        assertThat(sessionColumns)
                .contains("session_date", "location", "host_member_id")
                .doesNotContain("start_date", "end_date");

        List<String> beneficiaryColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'session_beneficiary'",
                String.class);
        assertThat(beneficiaryColumns).contains("id", "session_id", "member_id", "created_at");
    }

    @Test
    void findByIdFailsForUnknownId() {
        assertThatThrownBy(() -> sessionService.findById(UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateChangesFieldsAndBeneficiaries() {
        SessionResponse created = sessionService.create(request("Séance à modifier", LocalDate.of(2026, 10, 3),
                host.getId(), List.of(beneficiaryA.getId())));

        SessionResponse updated = sessionService.update(created.id(), new SessionRequest("Séance modifiée",
                LocalDate.of(2026, 10, 10), "Douala – Akwa", beneficiaryB.getId(),
                List.of(beneficiaryA.getId(), beneficiaryB.getId())));

        assertThat(updated.label()).isEqualTo("Séance modifiée");
        assertThat(updated.date()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(updated.location()).isEqualTo("Douala – Akwa");
        assertThat(updated.hostMember().memberId()).isEqualTo(beneficiaryB.getId());
        assertThat(updated.beneficiaries()).extracting(SessionBeneficiaryResponse::memberId)
                .containsExactlyInAnyOrder(beneficiaryA.getId(), beneficiaryB.getId());

        SessionResponse reduced = sessionService.update(created.id(), request("Séance modifiée",
                LocalDate.of(2026, 10, 10), host.getId(), List.of(beneficiaryB.getId())));
        assertThat(reduced.beneficiaries()).extracting(SessionBeneficiaryResponse::memberId)
                .containsExactly(beneficiaryB.getId());
    }

    @Test
    void deleteRemovesSessionWithoutPayments() {
        SessionResponse created = sessionService.create(request("Séance vide", LocalDate.of(2026, 10, 3),
                host.getId(), List.of(beneficiaryA.getId())));
        ContributionPeriod period = attachContribution(created.id());

        sessionService.delete(created.id());

        assertThat(sessionRepository.findById(created.id())).isEmpty();
        assertThat(periodRepository.findById(period.getId())).isEmpty();
    }

    @Test
    void deleteIsRefusedWhenAPaymentExists() {
        SessionResponse created = sessionService.create(request("Séance payée", LocalDate.of(2026, 10, 3),
                host.getId(), List.of(beneficiaryA.getId())));
        ContributionPeriod period = attachContribution(created.id());
        transactionRepository.saveAndFlush(new ContributionTransaction(beneficiaryA, period, new BigDecimal(50000),
                PaymentOperator.CASH, "DEL-1", LocalDate.of(2026, 10, 3), null));

        assertThatThrownBy(() -> sessionService.delete(created.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("paiements");
        assertThat(sessionRepository.findById(created.id())).isPresent();
    }

    private ContributionPeriod attachContribution(UUID sessionId) {
        ContributionDefinition definition = definitionRepository.save(new ContributionDefinition(
                "Cotisation 50 000", null, new BigDecimal(50000), AmountMode.FIXED,
                ContributionFrequency.MONTHLY, true, Visibility.PUBLIC, FundDestination.TONTINE_FUND));
        Session session = sessionRepository.findById(sessionId).orElseThrow();
        return periodRepository.saveAndFlush(new ContributionPeriod(definition, session, null));
    }
}
