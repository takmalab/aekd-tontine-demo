package cm.aekd.tontine.auth;

import java.util.List;

public record LoginResponse(
        String token,
        String email,
        List<String> roles,
        /** Jeton de renouvellement de la session (decisions.md n° 54). */
        String refreshToken
) {
}
