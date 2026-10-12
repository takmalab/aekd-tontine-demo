package cm.aekd.tontine.user;

import cm.aekd.tontine.security.RoleName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * {@code phone} : numéro de connexion par OTP (facultatif) ; à défaut,
 * celui du profil membre est utilisé.
 */
public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotEmpty List<RoleName> roles,
        @Valid MemberProfileRequest member,
        String phone
) {

    /** Compte sans numéro de connexion propre. */
    public CreateUserRequest(String email, String password, List<RoleName> roles, MemberProfileRequest member) {
        this(email, password, roles, member, null);
    }
}
