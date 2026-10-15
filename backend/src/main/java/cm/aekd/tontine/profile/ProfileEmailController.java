package cm.aekd.tontine.profile;

import cm.aekd.tontine.auth.emailcode.EmailCodeService;
import cm.aekd.tontine.auth.emailcode.EmailCodeService.CodeSent;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** « Mon profil » : vérifier son adresse e-mail par code (decisions.md n° 55). */
@RestController
@RequestMapping("/api/profile/email")
public class ProfileEmailController {

    private final EmailCodeService emailCodeService;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    public ProfileEmailController(EmailCodeService emailCodeService, CurrentUserProvider currentUserProvider,
                                  UserRepository userRepository) {
        this.emailCodeService = emailCodeService;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
    }

    public record EmailStatus(String email, boolean verified) {
    }

    public record CodeRequest(@NotBlank String code) {
    }

    @GetMapping
    public EmailStatus status() {
        User user = currentUserProvider.currentUserId().flatMap(userRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié"));
        return new EmailStatus(user.getEmail(), user.isEmailVerified());
    }

    @PostMapping("/code")
    public CodeSent sendCode() {
        return emailCodeService.sendVerificationToCurrentUser();
    }

    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@Valid @RequestBody CodeRequest request) {
        emailCodeService.verifyCurrentUser(request.code());
        return ResponseEntity.noContent().build();
    }
}
