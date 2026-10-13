package cm.aekd.tontine.auth.otp;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.auth.AuthService;
import cm.aekd.tontine.auth.LoginResponse;
import cm.aekd.tontine.common.PhoneNumbers;
import cm.aekd.tontine.sms.SmsSendException;
import cm.aekd.tontine.sms.SmsSender;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import cm.aekd.tontine.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Connexion par code à usage unique envoyé par SMS (docs/decisions.md
 * n° 47), en plus de la connexion email + mot de passe.
 *
 * <ul>
 *   <li>Réponse identique que le numéro soit inscrit ou non : aucun SMS
 *   n'est envoyé à un numéro sans compte actif, mais la demande compte
 *   dans les mêmes limites.</li>
 *   <li>Seul le hash du code est conservé ; un nouveau code annule les
 *   précédents ; un code n'est utilisable qu'une fois.</li>
 *   <li>Délai avant de redemander un code, nombre de demandes par heure
 *   et nombre d'essais limités ({@link OtpProperties}).</li>
 * </ul>
 */
@Service
@Transactional(noRollbackFor = ResponseStatusException.class)
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final String INVALID_CODE = "Code invalide ou expiré";

    private final OtpChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsSender smsSender;
    private final AuthService authService;
    private final AuditLogService auditLogService;
    private final OtpProperties properties;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpChallengeRepository challengeRepository, UserRepository userRepository,
                      PasswordEncoder passwordEncoder, SmsSender smsSender, AuthService authService,
                      AuditLogService auditLogService, OtpProperties properties) {
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.smsSender = smsSender;
        this.authService = authService;
        this.auditLogService = auditLogService;
        this.properties = properties;
    }

    @Transactional
    public OtpRequestResponse requestCode(OtpRequest request) {
        String phone = PhoneNumbers.normalize(request.phone());
        Instant now = Instant.now();

        challengeRepository.findFirstByPhoneOrderByCreatedAtDesc(phone).ifPresent(last -> {
            Duration elapsed = Duration.between(last.getCreatedAt(), now);
            if (elapsed.compareTo(properties.getResendCooldown()) < 0) {
                long wait = properties.getResendCooldown().minus(elapsed).toSeconds() + 1;
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Patientez " + wait + " seconde(s) avant de demander un nouveau code");
            }
        });
        if (challengeRepository.countByPhoneAndCreatedAtAfter(phone, now.minus(Duration.ofHours(1)))
                >= properties.getMaxRequestsPerHour()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de demandes de code pour ce numéro. Réessayez plus tard ou connectez-vous avec votre email");
        }

        User user = userRepository.findByPhone(phone)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElse(null);
        String code = generateCode();

        challengeRepository.consumeAllActive(phone, now);
        OtpChallenge challenge = challengeRepository.save(new OtpChallenge(phone, user,
                passwordEncoder.encode(code), now.plus(properties.getTtl())));

        if (user != null) {
            try {
                smsSender.sendLoginCode(phone, code, properties.getTtl(), challenge.getId().toString());
            } catch (SmsSendException e) {
                log.error("Envoi du code OTP impossible vers {} : {}", phone, e.getMessage());
                // Exception => rollback : le code non reçu n'est pas conservé (il ne bloque pas une
                // nouvelle demande) et les codes précédents restent valables.
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "L'envoi du SMS a échoué. Réessayez dans un instant ou connectez-vous avec votre email");
            }
        }

        return new OtpRequestResponse(phone,
                "Si ce numéro est associé à un compte actif, un code de connexion vient de lui être envoyé par SMS.",
                properties.getTtl().toSeconds(), properties.getResendCooldown().toSeconds());
    }

    public LoginResponse verifyCode(OtpVerifyRequest request) {
        String phone = PhoneNumbers.normalize(request.phone());
        Instant now = Instant.now();

        OtpChallenge challenge = challengeRepository.findFirstByPhoneOrderByCreatedAtDesc(phone)
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CODE));

        if (!passwordEncoder.matches(request.code(), challenge.getCodeHash())) {
            challenge.registerFailedAttempt();
            int remaining = properties.getMaxAttempts() - challenge.getAttempts();
            if (remaining <= 0) {
                challenge.consume();
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Code incorrect. Nombre d'essais dépassé : demandez un nouveau code");
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Code incorrect. Il vous reste " + remaining + " essai(s)");
        }

        challenge.consume();
        User user = challenge.getUser();
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CODE);
        }

        auditLogService.record(AuditAction.USER_LOGIN_OTP, "User", user.getId(),
                "Connexion de " + user.getEmail() + " par code SMS (" + phone + ")");
        return authService.issueToken(user);
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, properties.getCodeLength());
        return String.format("%0" + properties.getCodeLength() + "d", random.nextInt(bound));
    }
}
