package cm.aekd.tontine.savings;

import cm.aekd.tontine.contribution.PaymentOperator;
import cm.aekd.tontine.fund.FundService;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.savings.SavingsDtos.DepositRequest;
import cm.aekd.tontine.savings.SavingsDtos.EntryResponse;
import cm.aekd.tontine.savings.SavingsDtos.MemberSavingsResponse;
import cm.aekd.tontine.savings.SavingsDtos.OverviewResponse;
import cm.aekd.tontine.savings.SavingsDtos.RateRequest;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SavingsServiceTest {

    private static final ZoneId DOUALA = ZoneId.of("Africa/Douala");

    @Autowired private SavingsService savingsService;
    @Autowired private SavingsDepositRepository depositRepository;
    @Autowired private SavingsInterestRateRepository rateRepository;
    @Autowired private FundService fundService;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Member saver;
    private Member treasurer;
    private Member other;

    @BeforeEach
    void setUp() {
        // Base de test partagée : on part sans taux pour maîtriser les intérêts.
        rateRepository.deleteAll();
        saver = createMember(RoleName.MEMBRE, "sav-saver");
        treasurer = createMember(RoleName.TRESORIER, "sav-treso");
        other = createMember(RoleName.MEMBRE, "sav-other");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Member createMember(RoleName roleName, String prefix) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(prefix + "@test.aekd.cm", passwordEncoder.encode("x"));
        user.addRole(role);
        userRepository.save(user);
        return memberRepository.save(new Member(user, "Test " + prefix, LocalDate.of(2024, 1, 1)));
    }

    private void loginAs(Member member, RoleName role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                member.getUser().getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private EntryResponse declare(long amount, String ref) {
        return savingsService.declare(new DepositRequest(new BigDecimal(amount), LocalDate.now(),
                PaymentOperator.MTN_MOMO, ref, null));
    }

    @Test
    void depositCountsOnlyOnceValidated() {
        loginAs(saver, RoleName.MEMBRE);
        EntryResponse deposit = declare(50000, "EP-1");

        assertThat(deposit.status()).isEqualTo("PENDING");
        MemberSavingsResponse pending = savingsService.mine();
        assertThat(pending.principal()).isEqualByComparingTo("0");
        assertThat(pending.pendingAmount()).isEqualByComparingTo("50000");
        assertThat(fundService.individualSavingsOf(saver.getId())).isEqualByComparingTo("0");

        loginAs(treasurer, RoleName.TRESORIER);
        assertThat(savingsService.pendingDeposits()).extracting(EntryResponse::id).contains(deposit.id());
        assertThat(savingsService.validate(deposit.id()).status()).isEqualTo("VALIDATED");

        loginAs(saver, RoleName.MEMBRE);
        MemberSavingsResponse after = savingsService.mine();
        assertThat(after.principal()).isEqualByComparingTo("50000");
        assertThat(after.validatedCount()).isEqualTo(1);
        assertThat(fundService.individualSavingsOf(saver.getId())).isEqualByComparingTo("50000");
    }

    @Test
    void interestIsNullUntilARateIsSetThenAccruesPerCompleteMonth() {
        loginAs(saver, RoleName.MEMBRE);
        EntryResponse declared = declare(100000, "EP-INT");
        loginAs(treasurer, RoleName.TRESORIER);
        savingsService.validate(declared.id());
        // Validation simulée trois mois plus tôt.
        SavingsDeposit deposit = depositRepository.findById(declared.id()).orElseThrow();
        deposit.setValidatedAt(LocalDate.now(DOUALA).minusMonths(3).atStartOfDay(DOUALA).toInstant());
        depositRepository.saveAndFlush(deposit);

        loginAs(saver, RoleName.MEMBRE);
        MemberSavingsResponse noRate = savingsService.mine();
        assertThat(noRate.rateConfigured()).isFalse();
        assertThat(noRate.interest()).isNull();
        assertThat(noRate.entries().get(0).interest()).isNull();

        loginAs(treasurer, RoleName.TRESORIER);
        savingsService.setRate(new RateRequest(new BigDecimal("1.000"), LocalDate.of(2000, 1, 1), "Taux test"));

        loginAs(saver, RoleName.MEMBRE);
        MemberSavingsResponse withRate = savingsService.mine();
        assertThat(withRate.currentMonthlyRate()).isEqualByComparingTo("1.000");
        assertThat(withRate.entries().get(0).completeMonths()).isEqualTo(3);
        assertThat(withRate.interest()).isEqualByComparingTo("3000");
        assertThat(withRate.total()).isEqualByComparingTo("103000");
    }

    @Test
    void rejectedDepositIsKeptInHistoryButNeverCounted() {
        loginAs(saver, RoleName.MEMBRE);
        EntryResponse declared = declare(20000, "EP-REJ");
        loginAs(treasurer, RoleName.TRESORIER);
        savingsService.reject(declared.id());

        loginAs(saver, RoleName.MEMBRE);
        MemberSavingsResponse mine = savingsService.mine();
        assertThat(mine.principal()).isEqualByComparingTo("0");
        assertThat(mine.entries()).extracting(EntryResponse::status).containsExactly("REJECTED");
    }

    @Test
    void nobodyDecidesOnTheirOwnDepositAndOnlyTheOwnerAttachesProof() {
        loginAs(saver, RoleName.MEMBRE);
        EntryResponse declared = declare(10000, "EP-SELF");
        MockMultipartFile proof = new MockMultipartFile("file", "momo.png", "image/png", new byte[]{1, 2, 3});
        assertThat(savingsService.uploadProof(declared.id(), proof).hasProof()).isTrue();

        loginAs(saver, RoleName.TRESORIER);
        assertThatThrownBy(() -> savingsService.validate(declared.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("propre versement");

        loginAs(other, RoleName.MEMBRE);
        assertThatThrownBy(() -> savingsService.uploadProof(declared.id(), proof))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> savingsService.downloadProof(declared.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    void overviewAggregatesSaversAndRejectsDuplicateRateDates() {
        loginAs(saver, RoleName.MEMBRE);
        EntryResponse a = declare(30000, "EP-A");
        loginAs(other, RoleName.MEMBRE);
        EntryResponse b = declare(10000, "EP-B");
        loginAs(treasurer, RoleName.TRESORIER);
        savingsService.validate(a.id());

        OverviewResponse overview = savingsService.overview();
        assertThat(overview.members()).anySatisfy(row -> {
            assertThat(row.memberId()).isEqualTo(saver.getId());
            assertThat(row.principal()).isEqualByComparingTo("30000");
        });
        assertThat(overview.members()).anySatisfy(row -> {
            assertThat(row.memberId()).isEqualTo(other.getId());
            assertThat(row.pendingAmount()).isEqualByComparingTo("10000");
        });
        assertThat(overview.monthly()).hasSize(12);
        assertThat(overview.validatedThisMonth()).isGreaterThanOrEqualTo(new BigDecimal(30000));
        assertThat(savingsService.allEntries()).extracting(EntryResponse::id).contains(a.id(), b.id());

        savingsService.setRate(new RateRequest(new BigDecimal("1.000"), LocalDate.of(2026, 11, 1), null));
        assertThatThrownBy(() -> savingsService.setRate(
                new RateRequest(new BigDecimal("2.000"), LocalDate.of(2026, 11, 1), null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("déjà défini");
    }
}
