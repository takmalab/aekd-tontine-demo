package cm.aekd.tontine.auth;

import cm.aekd.tontine.security.JwtService;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import cm.aekd.tontine.user.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@org.springframework.transaction.annotation.Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Message renvoyé (403) à un compte inscrit pas encore validé (decisions.md n° 54). */
    public static final String PENDING_MESSAGE = "Votre compte est en attente de validation par l'administrateur";

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect"));
        // Mot de passe juste mais compte pas encore validé : l'interface affiche la page d'attente.
        if (user.getStatus() == UserStatus.PENDING_VALIDATION) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, PENDING_MESSAGE);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect");
        }

        return issueToken(user);
    }

    /** Renouvelle la session avec le jeton de l'appareil, sans ressaisir code ni mot de passe. */
    public LoginResponse refresh(String refreshToken) {
        return issueToken(refreshTokenService.consume(refreshToken));
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    /**
     * Jeton de session, identique quelle que soit la méthode de connexion
     * (email + mot de passe ou OTP) : le reste de l'application ne
     * distingue pas les deux.
     */
    public LoginResponse issueToken(User user) {
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .toList();

        String token = jwtService.generateToken(user.getEmail(), roles);

        return new LoginResponse(token, user.getEmail(), roles, refreshTokenService.issue(user));
    }
}
