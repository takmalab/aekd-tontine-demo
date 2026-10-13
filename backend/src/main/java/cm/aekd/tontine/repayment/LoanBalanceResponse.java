package cm.aekd.tontine.repayment;

import java.math.BigDecimal;

/**
 * {@code totalAmount} : capital + intérêts à rembourser (docs/decisions.md
 * n° 19) ; {@code principalAmount} et {@code interestAmount} en donnent le
 * détail. {@code totalRepaid} et {@code remainingBalance} ne comptent que les
 * remboursements validés ; {@code pendingAmount} : déclarés par le membre,
 * en attente de validation (docs/decisions.md n° 38).
 */
public record LoanBalanceResponse(BigDecimal totalAmount, BigDecimal totalRepaid, BigDecimal remainingBalance,
                                  BigDecimal pendingAmount, BigDecimal principalAmount, BigDecimal interestAmount,
                                  BigDecimal interestRate) {
}
