package cm.aekd.tontine.fund;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class AccountOperationDtos {

    private AccountOperationDtos() {
    }

    /** targetAccount : requis pour un virement, absent pour une dépense. */
    public record OperationRequest(
            @NotNull AccountOperationType type,
            @NotNull FundAccount sourceAccount,
            FundAccount targetAccount,
            @NotNull @DecimalMin("1") @Digits(integer = 14, fraction = 0) BigDecimal amount,
            @NotNull @PastOrPresent LocalDate operationDate,
            @NotBlank @Size(max = 200) String label,
            @Size(max = 500) String observation
    ) {
    }

    public record OperationResponse(
            UUID id,
            AccountOperationType type,
            FundAccount sourceAccount,
            FundAccount targetAccount,
            BigDecimal amount,
            LocalDate operationDate,
            String label,
            String observation,
            String recordedByEmail,
            Instant createdAt
    ) {
        static OperationResponse from(AccountOperation o) {
            return new OperationResponse(o.getId(), o.getType(), o.getSourceAccount(), o.getTargetAccount(),
                    o.getAmount(), o.getOperationDate(), o.getLabel(), o.getObservation(),
                    o.getRecordedBy() == null ? null : o.getRecordedBy().getEmail(), o.getCreatedAt());
        }
    }
}
