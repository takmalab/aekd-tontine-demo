package cm.aekd.tontine.user;

import cm.aekd.tontine.security.RoleName;

import java.util.List;

/**
 * Champs facultatifs : seuls ceux fournis (non nuls) sont appliqués.
 */
public record UpdateUserRequest(
        UserStatus status,
        List<RoleName> roles
) {
}
