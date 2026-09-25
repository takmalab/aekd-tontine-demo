package cm.aekd.tontine.member;

import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.user.CreateUserRequest;
import cm.aekd.tontine.user.MemberProfileRequest;
import cm.aekd.tontine.user.UserResponse;
import cm.aekd.tontine.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private MemberService memberService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String email, RoleName roleName) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + roleName));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, authorities));
    }

    @Test
    void canUpdateFullNamePhoneAndActiveStatus() {
        UserResponse created = userService.create(new CreateUserRequest(
                "memberupd1@test.aekd.cm", "Secret@123", List.of(RoleName.MEMBRE),
                new MemberProfileRequest("Ancien Nom", null, LocalDate.of(2025, 1, 1))));

        MemberResponse updated = memberService.update(created.memberId(),
                new MemberUpdateRequest("Nouveau Nom", "699111222", false));

        assertThat(updated.fullName()).isEqualTo("Nouveau Nom");
        assertThat(updated.phone()).isEqualTo("699111222");
        assertThat(updated.active()).isFalse();
    }

    @Test
    void findMineResolvesCurrentMemberProfile() {
        UserResponse created = userService.create(new CreateUserRequest(
                "memberself1@test.aekd.cm", "Secret@123", List.of(RoleName.MEMBRE),
                new MemberProfileRequest("Moi Meme", null, LocalDate.of(2025, 1, 1))));

        loginAs("memberself1@test.aekd.cm", RoleName.MEMBRE);

        MemberResponse mine = memberService.findMine();
        assertThat(mine.id()).isEqualTo(created.memberId());
    }

    @Test
    void findMineFailsWithoutMemberProfile() {
        userService.create(new CreateUserRequest("staffonly1@test.aekd.cm", "Secret@123",
                List.of(RoleName.TRESORIER), null));
        loginAs("staffonly1@test.aekd.cm", RoleName.TRESORIER);

        assertThatThrownBy(() -> memberService.findMine())
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void findAllIncludesCreatedMember() {
        userService.create(new CreateUserRequest("memberlist1@test.aekd.cm", "Secret@123",
                List.of(RoleName.MEMBRE),
                new MemberProfileRequest("Liste Membre", null, LocalDate.of(2025, 1, 1))));

        assertThat(memberService.findAll()).extracting(MemberResponse::email)
                .contains("memberlist1@test.aekd.cm");
    }
}
