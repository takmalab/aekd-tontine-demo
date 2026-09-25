package cm.aekd.tontine.auth;

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

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = userService.registerSelf(
                request.email(), request.password(), request.fullName(), request.phone());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
