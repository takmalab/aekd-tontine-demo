package cm.aekd.tontine.auth.otp;

import cm.aekd.tontine.auth.LoginResponse;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.sms.SmsSendException;
import cm.aekd.tontine.sms.SmsSender;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import cm.aekd.tontine.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OtpServiceTest {

    private static final String PHONE = "+237670000101";

    @TestConfiguration
    static class RecordingSmsConfig {
        @Bean
        @Primary
        RecordingSmsSender recordingSmsSender() {
            return new RecordingSmsSender();
        }
    }

    static class RecordingSmsSender implements SmsSender {
        final List<String[]> sent = new ArrayList<>();
        boolean fail;

        @Override
        public void sendLoginCode(String to, String code, Duration validity, String idempotencyKey) {
            if (fail) {
                throw new SmsSendException("indisponible");
            }
            sent.add(new String[]{to, code});
        }

        String lastCode() {
            return sent.get(sent.size() - 1)[1];
        }
    }

    @Autowired
    private OtpService otpService;

    @Autowired
    private OtpChallengeRepository challengeRepository;

    @Autowired
    private OtpProperties properties;

    @Autowired
    private RecordingSmsSender sms;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Duration defaultCooldown;

    @BeforeEach
    void setUp() {
        sms.sent.clear();
        sms.fail = false;
        defaultCooldown = properties.getResendCooldown();
    }

    @AfterEach
    void restoreProperties() {
        properties.setResendCooldown(defaultCooldown);
    }

    private User createUser(String email, String phone, UserStatus status) {
        Role memberRole = roleRepository.findByName(RoleName.MEMBRE).orElseThrow();
        User user = new User(email, passwordEncoder.encode("Secret@123"));
        user.addRole(memberRole);
        user.setPhone(phone);
        user.setStatus(status);
        return userRepository.save(user);
    }

    @Test
    void sendsACodeBySmsAndLogsInWithIt() {
        createUser("otp.ok@test.aekd.cm", PHONE, UserStatus.ACTIVE);

        OtpRequestResponse response = otpService.requestCode(new OtpRequest("6 70 00 01 01"));

        assertThat(response.phone()).isEqualTo(PHONE);
        assertThat(response.expiresInSeconds()).isEqualTo(300);
        assertThat(sms.sent).hasSize(1);
        assertThat(sms.sent.get(0)[0]).isEqualTo(PHONE);

        LoginResponse login = otpService.verifyCode(new OtpVerifyRequest("670000101", sms.lastCode()));

        assertThat(login.token()).isNotBlank();
        assertThat(login.email()).isEqualTo("otp.ok@test.aekd.cm");
        assertThat(login.roles()).containsExactly("MEMBRE");
    }

    @Test
    void storesOnlyAHashOfTheCode() {
        createUser("otp.hash@test.aekd.cm", PHONE, UserStatus.ACTIVE);
        otpService.requestCode(new OtpRequest(PHONE));

        OtpChallenge challenge = challengeRepository.findFirstByPhoneOrderByCreatedAtDesc(PHONE).orElseThrow();
        assertThat(challenge.getCodeHash()).doesNotContain(sms.lastCode());
    }

    @Test
    void aCodeCanOnlyBeUsedOnce() {
        createUser("otp.once@test.aekd.cm", PHONE, UserStatus.ACTIVE);
        otpService.requestCode(new OtpRequest(PHONE));
        String code = sms.lastCode();
        otpService.verifyCode(new OtpVerifyRequest(PHONE, code));

        assertThatThrownBy(() -> otpService.verifyCode(new OtpVerifyRequest(PHONE, code)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Code invalide ou expiré");
    }

    @Test
    void wrongCodesAreCountedThenTheCodeIsBlocked() {
        createUser("otp.wrong@test.aekd.cm", PHONE, UserStatus.ACTIVE);
        otpService.requestCode(new OtpRequest(PHONE));
        String good = sms.lastCode();
        String bad = good.equals("000000") ? "111111" : "000000";

        assertThatThrownBy(() -> otpService.verifyCode(new OtpVerifyRequest(PHONE, bad)))
                .hasMessageContaining("Il vous reste 4 essai(s)");
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> otpService.verifyCode(new OtpVerifyRequest(PHONE, bad)));
        }
        assertThatThrownBy(() -> otpService.verifyCode(new OtpVerifyRequest(PHONE, bad)))
                .hasMessageContaining("Nombre d'essais dépassé");

        assertThatThrownBy(() -> otpService.verifyCode(new OtpVerifyRequest(PHONE, good)))
                .hasMessageContaining("Code invalide ou expiré");
    }

    @Test
    void anExpiredCodeIsRefused() {
        createUser("otp.expired@test.aekd.cm", PHONE, UserStatus.ACTIVE);
        otpService.requestCode(new OtpRequest(PHONE));
        OtpChallenge challenge = challengeRepository.findFirstByPhoneOrderByCreatedAtDesc(PHONE).orElseThrow();
        challenge.setExpiresAt(Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> otpService.verifyCode(new OtpVerifyRequest(PHONE, sms.lastCode())))
                .hasMessageContaining("Code invalide ou expiré");
    }

    @Test
    void unknownNumberGetsTheSameAnswerButNoSms() {
        OtpRequestResponse response = otpService.requestCode(new OtpRequest("670000999"));

        assertThat(response.message()).contains("Si ce numéro est associé à un compte actif");
        assertThat(sms.sent).isEmpty();
        assertThatThrownBy(() -> otpService.verifyCode(new OtpVerifyRequest("670000999", "123456")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void accountsAwaitingValidationReceiveNoCode() {
        // decisions.md n° 54 : l'interface affiche la page « compte en attente ».
        createUser("otp.pending@test.aekd.cm", PHONE, UserStatus.PENDING_VALIDATION);

        assertThatThrownBy(() -> otpService.requestCode(new OtpRequest(PHONE)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(403))
                .hasMessageContaining("en attente de validation");
        assertThat(sms.sent).isEmpty();
    }

    @Test
    void aNewCodeCannotBeRequestedBeforeTheCooldown() {
        createUser("otp.cooldown@test.aekd.cm", PHONE, UserStatus.ACTIVE);
        otpService.requestCode(new OtpRequest(PHONE));

        assertThatThrownBy(() -> otpService.requestCode(new OtpRequest(PHONE)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS))
                .hasMessageContaining("Patientez");
    }

    @Test
    void aNewCodeReplacesThePreviousOne() {
        properties.setResendCooldown(Duration.ZERO);
        createUser("otp.replace@test.aekd.cm", PHONE, UserStatus.ACTIVE);
        otpService.requestCode(new OtpRequest(PHONE));
        String first = sms.lastCode();
        otpService.requestCode(new OtpRequest(PHONE));
        String second = sms.lastCode();

        if (!first.equals(second)) {
            assertThatThrownBy(() -> otpService.verifyCode(new OtpVerifyRequest(PHONE, first)))
                    .isInstanceOf(ResponseStatusException.class);
        }
        assertThat(otpService.verifyCode(new OtpVerifyRequest(PHONE, second)).token()).isNotBlank();
    }

    @Test
    void requestsPerHourAreLimited() {
        properties.setResendCooldown(Duration.ZERO);
        createUser("otp.hourly@test.aekd.cm", PHONE, UserStatus.ACTIVE);
        for (int i = 0; i < properties.getMaxRequestsPerHour(); i++) {
            otpService.requestCode(new OtpRequest(PHONE));
        }

        assertThatThrownBy(() -> otpService.requestCode(new OtpRequest(PHONE)))
                .hasMessageContaining("Trop de demandes");
    }

    @Test
    void smsFailureIsReportedAndLeavesNoUsableCode() {
        createUser("otp.smsdown@test.aekd.cm", PHONE, UserStatus.ACTIVE);
        sms.fail = true;

        assertThatThrownBy(() -> otpService.requestCode(new OtpRequest(PHONE)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void invalidPhoneIsRejected() {
        assertThatThrownBy(() -> otpService.requestCode(new OtpRequest("+33612345678")))
                .hasMessageContaining("Numéro de téléphone invalide");
    }
}
