package cm.aekd.tontine.member;

import cm.aekd.tontine.user.UserStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MemberResponse(
        UUID id,
        UUID userId,
        String email,
        String fullName,
        String phone,
        LocalDate joinDate,
        boolean active,
        Instant createdAt,
        // Statut du compte : un compte PENDING_VALIDATION ne peut pas être participant (décision n° 60).
        UserStatus accountStatus
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getFullName(),
                member.getPhone(),
                member.getJoinDate(),
                member.isActive(),
                member.getCreatedAt(),
                member.getUser().getStatus()
        );
    }
}
