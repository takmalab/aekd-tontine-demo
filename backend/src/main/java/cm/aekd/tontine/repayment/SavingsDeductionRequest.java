package cm.aekd.tontine.repayment;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Prélèvement sur l'épargne pour rembourser un prêt en retard (decisions.md n° 53). */
public record SavingsDeductionRequest(
        @NotNull Payer payer,
        @NotNull @Positive @Digits(integer = 14, fraction = 0) BigDecimal amount
) {
    public enum Payer { BORROWER, GUARANTOR }
}
