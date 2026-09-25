package cm.aekd.tontine.user;

import cm.aekd.tontine.security.RoleName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        UserStatus status,
        List<RoleName> roles,
        UUID memberId,
        String memberFullName,
        Instant createdAt
) {
}
