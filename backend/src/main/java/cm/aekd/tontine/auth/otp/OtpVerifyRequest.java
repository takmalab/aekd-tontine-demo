package cm.aekd.tontine.auth.otp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
        @NotBlank String phone,
        @NotBlank @Pattern(regexp = "\\d{4,8}", message = "Le code doit contenir uniquement des chiffres") String code
) {
}
