package cm.aekd.tontine.auth;

import cm.aekd.tontine.auth.otp.OtpRequest;
import cm.aekd.tontine.auth.otp.OtpRequestResponse;
import cm.aekd.tontine.auth.otp.OtpService;
import cm.aekd.tontine.auth.otp.OtpVerifyRequest;
import cm.aekd.tontine.user.RegisterResponse;
import cm.aekd.tontine.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * L'auto-inscription (§/décision validée avec l'utilisateur) crée
 * toujours un compte MEMBRE en attente de validation par un
 * administrateur (voir UserService.registerSelf) : elle ne permet
 * jamais de s'attribuer un rôle ADMIN/TRESORIER ni de s'activer
 * soi-même.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final OtpService otpService;
    private final cm.aekd.tontine.auth.emailcode.EmailCodeService emailCodeService;

    public AuthController(AuthService authService, UserService userService, OtpService otpService,
                          cm.aekd.tontine.auth.emailcode.EmailCodeService emailCodeService) {
        this.emailCodeService = emailCodeService;
        this.authService = authService;
        this.otpService = otpService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Connexion par OTP, étape 1 : envoi d'un code par SMS (decisions.md n° 47). */
    @PostMapping("/otp/request")
    public OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequest request) {
        return otpService.requestCode(request);
    }

    /** Connexion par OTP, étape 2 : vérification du code, renvoie le même jeton que /login. */
    @PostMapping("/otp/verify")
    public LoginResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return otpService.verifyCode(request);
    }

    /** Renouvelle la session de l'appareil (decisions.md n° 54) : nouveau jeton d'accès et de renouvellement. */
    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** Déconnexion : le jeton de renouvellement de cet appareil est révoqué. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    public record RefreshRequest(@jakarta.validation.constraints.NotBlank String refreshToken) {
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = userService.registerSelf(
                request.email(), request.password(), request.fullName(), request.phone());
        // Code de confirmation de l'adresse (decisions.md n° 55). Un échec d'envoi
        // n'annule pas l'inscription : le membre peut redemander le code.
        try {
            emailCodeService.sendVerification(request.email());
        } catch (org.springframework.web.server.ResponseStatusException e) {
            org.slf4j.LoggerFactory.getLogger(AuthController.class)
                    .warn("Code de confirmation non envoyé à {} : {}", request.email(), e.getReason());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ---------- Codes par e-mail (decisions.md n° 55) ----------

    public record EmailRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email) {
    }

    public record EmailCodeRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email,
                                   @jakarta.validation.constraints.NotBlank String code) {
    }

    /** Renvoie le code de confirmation de l'adresse (page d'attente). */
    @PostMapping("/email/verification")
    public cm.aekd.tontine.auth.emailcode.EmailCodeService.CodeSent sendVerification(@Valid @RequestBody EmailRequest request) {
        return emailCodeService.sendVerification(request.email());
    }

    @PostMapping("/email/verify")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody EmailCodeRequest request) {
        emailCodeService.verifyEmail(request.email(), request.code());
        return ResponseEntity.noContent().build();
    }

    /** Connexion par code e-mail, étape 1. */
    @PostMapping("/email-otp/request")
    public cm.aekd.tontine.auth.emailcode.EmailCodeService.CodeSent requestEmailLoginCode(@Valid @RequestBody EmailRequest request) {
        return emailCodeService.requestLoginCode(request.email());
    }

    /** Connexion par code e-mail, étape 2 : même jeton que /login. */
    @PostMapping("/email-otp/verify")
    public LoginResponse verifyEmailLoginCode(@Valid @RequestBody EmailCodeRequest request) {
        return emailCodeService.verifyLoginCode(request.email(), request.code());
    }
}
