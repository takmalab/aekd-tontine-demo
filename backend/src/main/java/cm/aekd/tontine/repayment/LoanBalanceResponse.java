package cm.aekd.tontine.repayment;

import java.math.BigDecimal;

public record LoanBalanceResponse(BigDecimal totalAmount, BigDecimal totalRepaid, BigDecimal remainingBalance) {
}
