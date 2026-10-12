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

    public AuthController(AuthService authService, UserService userService, OtpService otpService) {
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

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = userService.registerSelf(
                request.email(), request.password(), request.fullName(), request.phone());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
