package cm.aekd.tontine.audit;

import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.session.SessionRequest;
import cm.aekd.tontine.session.SessionService;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditLogServiceTest {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Member createMember(RoleName roleName, String emailPrefix) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(emailPrefix + "@test.aekd.cm", passwordEncoder.encode("x"));
        user.addRole(role);
        userRepository.save(user);
        Member member = new Member(user, "Test " + emailPrefix, LocalDate.now());
        return memberRepository.save(member);
    }

    private void loginAsMember(Member member, RoleName roleName) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + roleName));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member.getUser().getEmail(), null, authorities));
    }

    @Test
    void sessionCreationIsRecordedWithActorAndDescription() {
        Member treasurer = createMember(RoleName.TRESORIER, "audittreso1");
        loginAsMember(treasurer, RoleName.TRESORIER);

        var session = sessionService.create(
                new SessionRequest("Octobre 2026", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

        List<AuditLogResponse> logs = auditLogService.findByEntity("Session", session.id());
        assertThat(logs).hasSize(1);
        AuditLogResponse entry = logs.get(0);
        assertThat(entry.action()).isEqualTo(AuditAction.SESSION_CREATED);
        assertThat(entry.userEmail()).isEqualTo(treasurer.getUser().getEmail());
        assertThat(entry.description()).contains("Octobre 2026");
    }

    @Test
    void findByEntityIsEmptyForUnrelatedEntity() {
        List<AuditLogResponse> logs = auditLogService.findByEntity("Session", UUID.randomUUID());
        assertThat(logs).isEmpty();
    }

    @Test
    void manualRecordAppearsInFindAll() {
        auditLogService.record(AuditAction.LOAN_REQUESTED, "Loan", UUID.randomUUID(), "Test manuel");
        assertThat(auditLogService.findAll()).isNotEmpty();
    }
}
