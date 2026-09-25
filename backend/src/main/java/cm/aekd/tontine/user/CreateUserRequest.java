package cm.aekd.tontine.user;

import cm.aekd.tontine.security.RoleName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotEmpty List<RoleName> roles,
        @Valid MemberProfileRequest member
) {
}
