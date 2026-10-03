package cm.aekd.tontine.repayment;

import java.math.BigDecimal;

/**
 * {@code totalRepaid} et {@code remainingBalance} ne comptent que les
 * remboursements validés ; {@code pendingAmount} : déclarés par le membre,
 * en attente de validation (docs/decisions.md n° 38).
 */
public record LoanBalanceResponse(BigDecimal totalAmount, BigDecimal totalRepaid, BigDecimal remainingBalance,
                                  BigDecimal pendingAmount) {
}
