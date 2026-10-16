package cm.aekd.tontine.auth.emailcode;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.auth.AuthService;
import cm.aekd.tontine.auth.LoginResponse;
import cm.aekd.tontine.auth.emailcode.EmailCode.Purpose;
import cm.aekd.tontine.auth.otp.OtpProperties;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.mail.EmailSendException;
import cm.aekd.tontine.mail.EmailSender;
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
import java.util.Locale;
import java.util.Optional;

/**
 * Codes envoyés par e-mail (docs/decisions.md n° 55), mêmes règles que les codes
 * SMS (n° 47, {@link OtpProperties}) : 6 chiffres, 5 minutes, 5 essais, 60 s
 * avant d'en redemander un, 5 demandes par heure et par adresse.
 * <ul>
 *   <li>VERIFY : confirme que l'adresse appartient au membre (inscription, ou
 *   plus tard depuis « Mon profil ») ;</li>
 *   <li>LOGIN : connexion par code e-mail, seulement pour un compte actif dont
 *   l'adresse est vérifiée. Réponse identique sinon, pour ne pas révéler
 *   quelles adresses ont un compte.</li>
 * </ul>
 */
@Service
@Transactional(noRollbackFor = ResponseStatusException.class)
public class EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);
    private static final String INVALID_CODE = "Code invalide ou expiré";

    private final EmailCodeRepository repository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final AuthService authService;
    private final AuditLogService auditLogService;
    private final CurrentUserProvider currentUserProvider;
    private final OtpProperties properties;
    /** Règles réglables par le super administrateur (decisions.md n° 57), sinon OtpProperties. */
    private final cm.aekd.tontine.settings.SystemSettingsService settings;
    private final SecureRandom random = new SecureRandom();

    public EmailCodeService(EmailCodeRepository repository, UserRepository userRepository,
                            PasswordEncoder passwordEncoder, EmailSender emailSender, AuthService authService,
                            AuditLogService auditLogService, CurrentUserProvider currentUserProvider,
                            OtpProperties properties, cm.aekd.tontine.settings.SystemSettingsService settings) {
        this.settings = settings;
        this.repository = repository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.authService = authService;
        this.auditLogService = auditLogService;
        this.currentUserProvider = currentUserProvider;
        this.properties = properties;
    }

    public record CodeSent(String email, String message, long expiresInSeconds, long resendAfterSeconds) {
    }

    // ---------------------------------------------------------------- vérification de l'adresse

    /**
     * Envoie un code de vérification à un compte dont l'adresse n'est pas encore
     * vérifiée (inscription, page d'attente). Réponse identique dans tous les cas.
     */
    public CodeSent sendVerification(String rawEmail) {
        String email = normalize(rawEmail);
        Optional<User> user = findUser(email).filter(u -> !u.isEmailVerified() && u.getStatus() != UserStatus.DISABLED);
        checkLimits(email);
        user.ifPresent(u -> issue(u, email, Purpose.VERIFY));
        return sent(email, "Si cette adresse doit être confirmée, un code vient de lui être envoyé par e-mail.");
    }

    /** Depuis « Mon profil » : vérifier l'adresse du compte connecté. */
    public CodeSent sendVerificationToCurrentUser() {
        User user = currentUser();
        if (user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Votre adresse e-mail est déjà vérifiée");
        }
        String email = normalize(user.getEmail());
        checkLimits(email);
        issue(user, email, Purpose.VERIFY);
        return sent(email, "Un code vient d'être envoyé à " + user.getEmail() + ".");
    }

    public void verifyEmail(String rawEmail, String code) {
        User user = consume(normalize(rawEmail), Purpose.VERIFY, code);
        markVerified(user);
    }

    public void verifyCurrentUser(String code) {
        User user = currentUser();
        User owner = consume(normalize(user.getEmail()), Purpose.VERIFY, code);
        if (!owner.getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CODE);
        }
        markVerified(owner);
    }

    // ---------------------------------------------------------------- connexion par code e-mail

    public CodeSent requestLoginCode(String rawEmail) {
        String email = normalize(rawEmail);
        Optional<User> owner = findUser(email);
        // Compte inscrit pas encore validé : page d'attente (decisions.md n° 54).
        if (owner.isPresent() && owner.get().getStatus() == UserStatus.PENDING_VALIDATION) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthService.PENDING_MESSAGE);
        }
        checkLimits(email);
        owner.filter(u -> u.getStatus() == UserStatus.ACTIVE && u.isEmailVerified())
                .ifPresent(u -> issue(u, email, Purpose.LOGIN));
        return sent(email, "Si cette adresse correspond à un compte actif dont l'e-mail est vérifié, "
                + "un code de connexion vient de lui être envoyé.");
    }

    public LoginResponse verifyLoginCode(String rawEmail, String code) {
        String email = normalize(rawEmail);
        User user = consume(email, Purpose.LOGIN, code);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CODE);
        }
        auditLogService.record(AuditAction.USER_LOGIN_EMAIL_CODE, "User", user.getId(),
                "Connexion de " + user.getEmail() + " par code e-mail");
        return authService.issueToken(user);
    }

    // ---------------------------------------------------------------- interne

    private void issue(User user, String email, Purpose purpose) {
        String code = generateCode();
        Instant now = Instant.now();
        repository.consumeAllActive(email, purpose, now);
        repository.save(new EmailCode(user, email, purpose, passwordEncoder.encode(code),
                now.plus(settings.otpTtl())));
        long minutes = settings.otpTtl().toMinutes();
        String subject = purpose == Purpose.VERIFY
                ? "AEKD Tontine – confirmez votre adresse : " + code
                : "AEKD Tontine – votre code de connexion : " + code;
        String intro = purpose == Purpose.VERIFY
                ? "Voici le code pour confirmer votre adresse e-mail sur AEKD Tontine :"
                : "Voici votre code de connexion à AEKD Tontine :";
        String text = intro + "\n\n" + code + "\n\nIl est valable " + minutes + " minutes. "
                + "Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.";
        String html = "<div style=\"font-family:Arial,sans-serif;color:#3a2a1f;max-width:480px\">"
                + "<h2 style=\"color:#e86e09;margin:0 0 12px\">AEKD Tontine</h2>"
                + "<p>" + intro + "</p>"
                + "<p style=\"font-size:32px;font-weight:bold;letter-spacing:6px;margin:16px 0\">" + code + "</p>"
                + "<p>Il est valable " + minutes + " minutes.</p>"
                + "<p style=\"color:#8a7a6f;font-size:13px\">Si vous n'êtes pas à l'origine de cette demande, "
                + "ignorez cet e-mail.</p></div>";
        try {
            emailSender.send(user.getEmail(), subject, text, html);
        } catch (EmailSendException e) {
            log.error("Envoi du code par e-mail impossible vers {} : {}", user.getEmail(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "L'envoi de l'e-mail a échoué. Réessayez dans un instant");
        }
    }

    private User consume(String email, Purpose purpose, String code) {
        Instant now = Instant.now();
        EmailCode challenge = repository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .filter(c -> c.isUsable(now))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CODE));
        if (code == null || !passwordEncoder.matches(code, challenge.getCodeHash())) {
            challenge.registerFailedAttempt();
            int remaining = settings.otpMaxAttempts() - challenge.getAttempts();
            if (remaining <= 0) {
                challenge.consume();
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Code incorrect. Nombre d'essais dépassé : demandez un nouveau code");
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Code incorrect. Il vous reste " + remaining + " essai(s)");
        }
        challenge.consume();
        return challenge.getUser();
    }

    private void markVerified(User user) {
        if (!user.isEmailVerified()) {
            user.markEmailVerified();
            auditLogService.record(AuditAction.USER_EMAIL_VERIFIED, "User", user.getId(),
                    "Adresse e-mail vérifiée : " + user.getEmail());
        }
    }

    private void checkLimits(String email) {
        Instant now = Instant.now();
        repository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, Purpose.VERIFY)
                .map(EmailCode::getCreatedAt)
                .ifPresent(last -> cooldown(last, now));
        repository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, Purpose.LOGIN)
                .map(EmailCode::getCreatedAt)
                .ifPresent(last -> cooldown(last, now));
        if (repository.countByEmailAndCreatedAtAfter(email, now.minus(Duration.ofHours(1)))
                >= settings.otpMaxRequestsPerHour()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de demandes de code pour cette adresse. Réessayez plus tard");
        }
    }

    private void cooldown(Instant last, Instant now) {
        Duration elapsed = Duration.between(last, now);
        if (elapsed.compareTo(settings.otpResendCooldown()) < 0) {
            long wait = settings.otpResendCooldown().minus(elapsed).toSeconds() + 1;
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Patientez " + wait + " seconde(s) avant de demander un nouveau code");
        }
    }

    private CodeSent sent(String email, String message) {
        return new CodeSent(email, message, settings.otpTtl().toSeconds(), settings.otpResendCooldown().toSeconds());
    }

    private Optional<User> findUser(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    private User currentUser() {
        return currentUserProvider.currentUserId().flatMap(userRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié"));
    }

    private static String normalize(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'adresse e-mail est requise");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, properties.getCodeLength());
        return String.format("%0" + properties.getCodeLength() + "d", random.nextInt(bound));
    }
}
