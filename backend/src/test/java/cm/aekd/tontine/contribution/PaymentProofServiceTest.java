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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentProofServiceTest {

    @Autowired
    private ContributionDefinitionService definitionService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ContributionTransactionService transactionService;

    @Autowired
    private PaymentProofService proofService;

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

    private ContributionTransactionResponse declarePayment(Member member) {
        loginAsMember(member, RoleName.TRESORIER);
        ContributionDefinitionResponse def = definitionService.create(new ContributionDefinitionRequest(
                "Cotisation 50 000", null, new BigDecimal(50000), AmountMode.FIXED,
                ContributionFrequency.MONTHLY, true, Visibility.PUBLIC, FundDestination.TONTINE_FUND));
        definitionService.addParticipant(def.id(), new MemberRefRequest(member.getId()));
        definitionService.activate(def.id());
        Session session = sessionRepository.save(
                new Session("Octobre 2026", LocalDate.of(2026, 10, 1), null, null));
        ContributionPeriodResponse period = definitionService.addPeriod(def.id(),
                new ContributionPeriodRequest(session.getId(), null));

        loginAsMember(member, RoleName.MEMBRE);
        return paymentService.declare(def.id(), new ContributionTransactionRequest(
                period.id(), new BigDecimal(50000), PaymentOperator.MTN_MOMO, "REF-001",
                LocalDate.of(2026, 10, 5), null));
    }

    @Test
    void ownerCanUploadAndDownloadProof() throws IOException {
        Member member = createMember(RoleName.MEMBRE, "proof1");
        ContributionTransactionResponse tx = declarePayment(member);

        loginAsMember(member, RoleName.MEMBRE);
        MockMultipartFile file = new MockMultipartFile("file", "recu.jpg", "image/jpeg", "fake-image-bytes".getBytes());
        PaymentProofResponse uploaded = proofService.upload(tx.id(), file);
        assertThat(uploaded.originalFilename()).isEqualTo("recu.jpg");
        assertThat(uploaded.fileSize()).isEqualTo("fake-image-bytes".getBytes().length);

        StoredResource downloaded = proofService.download(tx.id());
        assertThat(downloaded.contentType()).isEqualTo("image/jpeg");
        downloaded.content().close();
    }

    @Test
    void treasurerCanDownloadButNotUpload() throws IOException {
        Member member = createMember(RoleName.MEMBRE, "proof2");
        ContributionTransactionResponse tx = declarePayment(member);
        Member treasurer = createMember(RoleName.TRESORIER, "treso2");

        loginAsMember(member, RoleName.MEMBRE);
        MockMultipartFile file = new MockMultipartFile("file", "recu.pdf", "application/pdf", "fake-pdf-bytes".getBytes());
        proofService.upload(tx.id(), file);

        loginAsMember(treasurer, RoleName.TRESORIER);
        StoredResource downloaded = proofService.download(tx.id());
        assertThat(downloaded.filename()).isEqualTo("recu.pdf");
        downloaded.content().close();

        MockMultipartFile otherFile = new MockMultipartFile("file", "x.pdf", "application/pdf", "x".getBytes());
        assertThatThrownBy(() -> proofService.upload(tx.id(), otherFile))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void outsiderCannotDownload() {
        Member member = createMember(RoleName.MEMBRE, "proof3");
        ContributionTransactionResponse tx = declarePayment(member);
        Member outsider = createMember(RoleName.MEMBRE, "outsider3");

        loginAsMember(member, RoleName.MEMBRE);
        MockMultipartFile file = new MockMultipartFile("file", "recu.png", "image/png", "bytes".getBytes());
        proofService.upload(tx.id(), file);

        loginAsMember(outsider, RoleName.MEMBRE);
        assertThatThrownBy(() -> proofService.download(tx.id()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void disallowedFormatIsRejected() {
        Member member = createMember(RoleName.MEMBRE, "proof4");
        ContributionTransactionResponse tx = declarePayment(member);

        loginAsMember(member, RoleName.MEMBRE);
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/x-msdownload", "bytes".getBytes());
        assertThatThrownBy(() -> proofService.upload(tx.id(), file))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void cannotUploadAfterPaymentIsDecided() {
        Member member = createMember(RoleName.MEMBRE, "proof5");
        ContributionTransactionResponse tx = declarePayment(member);
        Member treasurer = createMember(RoleName.TRESORIER, "treso5");

        loginAsMember(treasurer, RoleName.TRESORIER);
        transactionService.validate(tx.id());

        loginAsMember(member, RoleName.MEMBRE);
        MockMultipartFile file = new MockMultipartFile("file", "recu.jpg", "image/jpeg", "bytes".getBytes());
        assertThatThrownBy(() -> proofService.upload(tx.id(), file))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void reuploadReplacesPreviousProof() throws IOException {
        Member member = createMember(RoleName.MEMBRE, "proof6");
        ContributionTransactionResponse tx = declarePayment(member);

        loginAsMember(member, RoleName.MEMBRE);
        MockMultipartFile first = new MockMultipartFile("file", "first.jpg", "image/jpeg", "first-bytes".getBytes());
        proofService.upload(tx.id(), first);

        MockMultipartFile second = new MockMultipartFile("file", "second.jpg", "image/jpeg", "second-bytes".getBytes());
        PaymentProofResponse replaced = proofService.upload(tx.id(), second);
        assertThat(replaced.originalFilename()).isEqualTo("second.jpg");

        StoredResource downloaded = proofService.download(tx.id());
        assertThat(downloaded.filename()).isEqualTo("second.jpg");
        downloaded.content().close();
    }
}
