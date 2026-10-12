package cm.aekd.tontine.user;

import cm.aekd.tontine.security.RoleName;

import java.util.List;

/**
 * Champs facultatifs : seuls ceux fournis (non nuls) sont appliqués.
 * {@code phone} : numéro de connexion par OTP ; une chaîne vide le retire.
 */
public record UpdateUserRequest(
        UserStatus status,
        List<RoleName> roles,
        String phone
) {

    /** Mise à jour sans toucher au numéro. */
    public UpdateUserRequest(UserStatus status, List<RoleName> roles) {
        this(status, roles, null);
    }
}
