package cm.aekd.tontine.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record MemberProfileRequest(
        @NotBlank String fullName,
        String phone,
        @NotNull LocalDate joinDate
) {
}
