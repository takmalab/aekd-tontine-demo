package cm.aekd.tontine.member;

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
        Instant createdAt
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
                member.getCreatedAt()
        );
    }
}
