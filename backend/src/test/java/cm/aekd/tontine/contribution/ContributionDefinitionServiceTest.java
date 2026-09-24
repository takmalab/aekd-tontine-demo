package cm.aekd.tontine.contribution;

import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.session.Session;
import cm.aekd.tontine.session.SessionRepository;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ContributionDefinitionServiceTest {

    @Autowired
    private ContributionDefinitionService service;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Member createMember(RoleName roleName, String emailPrefix) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(emailPrefix + "@test.aekd.cm", passwordEncoder.encode("x"));
        user.addRole(role);
        userRepository.save(user);
        Member member = new Member(user, "Test " + emailPrefix, LocalDate.now());
        return memberRepository.save(member);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(RoleName roleName) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + roleName));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("current@test.aekd.cm", null, authorities));
    }

    private void loginAsMember(Member member) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_MEMBRE"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member.getUser().getEmail(), null, authorities));
    }

    @Test
    void treasurerCanCreateAddParticipantAndActivate() {
        loginAs(RoleName.TRESORIER);
        Member member = createMember(RoleName.MEMBRE, "part1");

        ContributionDefinitionResponse def = service.create(new ContributionDefinitionRequest(
                "Cotisation 50 000", null, new BigDecimal(50000), AmountMode.FIXED,
                ContributionFrequency.MONTHLY, true, Visibility.PUBLIC, FundDestination.TONTINE_FUND));

        assertThat(def.status()).isEqualTo(ContributionStatus.DRAFT);

        ParticipantResponse participant = service.addParticipant(def.id(), new MemberRefRequest(member.getId()));
        assertThat(participant.memberId()).isEqualTo(member.getId());

        ContributionDefinitionResponse activated = service.activate(def.id());
        assertThat(activated.status()).isEqualTo(ContributionStatus.ACTIVE);

        assertThatThrownBy(() -> service.activate(def.id()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void fixedAmountModeRequiresPositiveAmount() {
        loginAs(RoleName.TRESORIER);

        assertThatThrownBy(() -> service.create(new ContributionDefinitionRequest(
                "Invalide", null, null, AmountMode.FIXED,
                ContributionFrequency.MONTHLY, true, Visibility.PUBLIC, FundDestination.TONTINE_FUND)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void duplicateParticipantIsRejected() {
        loginAs(RoleName.TRESORIER);
        Member member = createMember(RoleName.MEMBRE, "part2");
        ContributionDefinitionResponse def = service.create(new ContributionDefinitionRequest(
                "Ration", null, new BigDecimal(2000), AmountMode.FIXED,
                ContributionFrequency.MONTHLY, true, Visibility.PUBLIC, FundDestination.TONTINE_FUND));

        service.addParticipant(def.id(), new MemberRefRequest(member.getId()));

        assertThatThrownBy(() -> service.addParticipant(def.id(), new MemberRefRequest(member.getId())))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void privateContributionIsHiddenFromNonParticipantMember() {
        loginAs(RoleName.TRESORIER);
        Member participant = createMember(RoleName.MEMBRE, "priv1");
        Member outsider = createMember(RoleName.MEMBRE, "priv2");

        ContributionDefinitionResponse def = service.create(new ContributionDefinitionRequest(
                "Cotisation privée", null, new BigDecimal(10000), AmountMode.FIXED,
                ContributionFrequency.MONTHLY, true, Visibility.PRIVATE, FundDestination.TONTINE_FUND));
        service.addParticipant(def.id(), new MemberRefRequest(participant.getId()));
        service.activate(def.id());

        loginAsMember(outsider);
        assertThatThrownBy(() -> service.findById(def.id()))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(service.findAll()).extracting(ContributionDefinitionResponse::id).doesNotContain(def.id());

        loginAsMember(participant);
        assertThat(service.findById(def.id()).id()).isEqualTo(def.id());
        assertThat(service.findAll()).extracting(ContributionDefinitionResponse::id).contains(def.id());
    }

    @Test
    void draftContributionIsHiddenFromMembersEvenIfPublic() {
        loginAs(RoleName.TRESORIER);
        Member member = createMember(RoleName.MEMBRE, "draft1");
        ContributionDefinitionResponse def = service.create(new ContributionDefinitionRequest(
                "Brouillon public", null, new BigDecimal(5000), AmountMode.FIXED,
                ContributionFrequency.MONTHLY, false, Visibility.PUBLIC, FundDestination.TONTINE_FUND));

        loginAsMember(member);
        assertThatThrownBy(() -> service.findById(def.id()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void addPeriodAttachesDefinitionToSessionAndRejectsDuplicate() {
        loginAs(RoleName.TRESORIER);
        ContributionDefinitionResponse def = service.create(new ContributionDefinitionRequest(
                "Cotisation 10 000", null, new BigDecimal(10000), AmountMode.FIXED,
                ContributionFrequency.MONTHLY, true, Visibility.PUBLIC, FundDestination.TONTINE_FUND));
        Session session = sessionRepository.save(
                new Session("Octobre 2026", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

        ContributionPeriodResponse period = service.addPeriod(def.id(),
                new ContributionPeriodRequest(session.getId(), LocalDate.of(2026, 10, 15)));
        assertThat(period.sessionId()).isEqualTo(session.getId());

        assertThatThrownBy(() -> service.addPeriod(def.id(),
                new ContributionPeriodRequest(session.getId(), null)))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(service.listPeriods(def.id())).hasSize(1);
    }

    @Test
    void voluntaryAmountModeAllowsNullAmount() {
        loginAs(RoleName.ADMIN);
        ContributionDefinitionResponse def = service.create(new ContributionDefinitionRequest(
                "Banque / Épargne", null, null, AmountMode.VOLUNTARY,
                ContributionFrequency.MONTHLY, false, Visibility.PUBLIC, FundDestination.INDIVIDUAL_SAVINGS));

        assertThat(def.amount()).isNull();
    }
}
