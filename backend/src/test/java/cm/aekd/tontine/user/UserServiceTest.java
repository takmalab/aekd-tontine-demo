package cm.aekd.tontine.user;

import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.security.RoleName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private MemberRepository memberRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsStaffAccountWithoutMemberProfile() {
        UserResponse created = userService.create(new CreateUserRequest(
                "staff1@test.aekd.cm", "Secret@123", List.of(RoleName.TRESORIER), null));

        assertThat(created.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.roles()).containsExactly(RoleName.TRESORIER);
        assertThat(created.memberId()).isNull();
    }

    @Test
    void createsMemberAccountWithLinkedProfile() {
        UserResponse created = userService.create(new CreateUserRequest(
                "member1@test.aekd.cm", "Secret@123", List.of(RoleName.MEMBRE),
                new MemberProfileRequest("Test Member", "699000000", LocalDate.of(2025, 1, 1))));

        assertThat(created.memberId()).isNotNull();
        assertThat(created.memberFullName()).isEqualTo("Test Member");

        Member member = memberRepository.findById(created.memberId()).orElseThrow();
        assertThat(member.getUser().getEmail()).isEqualTo("member1@test.aekd.cm");
    }

    @Test
    void cannotCreateDuplicateEmail() {
        userService.create(new CreateUserRequest("dup1@test.aekd.cm", "Secret@123",
                List.of(RoleName.TRESORIER), null));

        assertThatThrownBy(() -> userService.create(new CreateUserRequest(
                "dup1@test.aekd.cm", "Other@123", List.of(RoleName.ADMIN), null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateCanDisableAccountAndChangeRoles() {
        UserResponse created = userService.create(new CreateUserRequest(
                "update1@test.aekd.cm", "Secret@123", List.of(RoleName.MEMBRE), null));

        UserResponse disabled = userService.update(created.id(), new UpdateUserRequest(UserStatus.DISABLED, null));
        assertThat(disabled.status()).isEqualTo(UserStatus.DISABLED);
        assertThat(disabled.roles()).containsExactly(RoleName.MEMBRE);

        UserResponse reRoled = userService.update(created.id(),
                new UpdateUserRequest(null, List.of(RoleName.ADMIN, RoleName.TRESORIER)));
        assertThat(reRoled.status()).isEqualTo(UserStatus.DISABLED);
        assertThat(reRoled.roles()).containsExactlyInAnyOrder(RoleName.ADMIN, RoleName.TRESORIER);
    }

    @Test
    void updateRejectsEmptyRoleList() {
        UserResponse created = userService.create(new CreateUserRequest(
                "update2@test.aekd.cm", "Secret@123", List.of(RoleName.MEMBRE), null));

        assertThatThrownBy(() -> userService.update(created.id(), new UpdateUserRequest(null, List.of())))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void findAllIncludesCreatedUser() {
        userService.create(new CreateUserRequest("list1@test.aekd.cm", "Secret@123",
                List.of(RoleName.ADMIN), null));

        assertThat(userService.findAll()).extracting(UserResponse::email).contains("list1@test.aekd.cm");
    }

    @Test
    void selfRegistrationCreatesPendingMembreAccountWithLinkedMemberProfile() {
        RegisterResponse response = userService.registerSelf(
                "selfreg1@test.aekd.cm", "Secret@123", "Auto Inscrit", "699222333");

        assertThat(response.email()).isEqualTo("selfreg1@test.aekd.cm");

        UserResponse user = userService.findAll().stream()
                .filter(u -> u.email().equals("selfreg1@test.aekd.cm"))
                .findFirst().orElseThrow();
        assertThat(user.status()).isEqualTo(UserStatus.PENDING_VALIDATION);
        assertThat(user.roles()).containsExactly(RoleName.MEMBRE);
        assertThat(user.memberId()).isNotNull();
        assertThat(user.memberFullName()).isEqualTo("Auto Inscrit");
    }

    @Test
    void selfRegistrationNeverGrantsAdminRole() {
        // registerSelf n'accepte aucune liste de rôles en entrée : toujours MEMBRE, en dur.
        RegisterResponse response = userService.registerSelf(
                "selfreg2@test.aekd.cm", "Secret@123", "Autre Inscrit", null);
        assertThat(response.message()).contains("validé");

        UserResponse user = userService.findAll().stream()
                .filter(u -> u.email().equals("selfreg2@test.aekd.cm"))
                .findFirst().orElseThrow();
        assertThat(user.roles()).containsExactly(RoleName.MEMBRE);
    }

    @Test
    void findPendingListsOnlySelfRegisteredAccounts() {
        userService.create(new CreateUserRequest("adminmade1@test.aekd.cm", "Secret@123",
                List.of(RoleName.TRESORIER), null));
        userService.registerSelf("pending1@test.aekd.cm", "Secret@123", "En Attente", null);

        assertThat(userService.findPending()).extracting(UserResponse::email)
                .contains("pending1@test.aekd.cm")
                .doesNotContain("adminmade1@test.aekd.cm");
    }

    @Test
    void approveActivatesAPendingAccount() {
        userService.registerSelf("approve1@test.aekd.cm", "Secret@123", "A Valider", null);
        UUID pendingId = userService.findPending().stream()
                .filter(u -> u.email().equals("approve1@test.aekd.cm"))
                .findFirst().orElseThrow().id();

        UserResponse approved = userService.approve(pendingId);
        assertThat(approved.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(userService.findPending()).extracting(UserResponse::email)
                .doesNotContain("approve1@test.aekd.cm");
    }
}
