package cm.aekd.tontine.auth.otp;

import jakarta.validation.constraints.NotBlank;

public record OtpRequest(
        @NotBlank String phone
) {
}
