package cm.aekd.tontine.savings;

import cm.aekd.tontine.contribution.PaymentOperator;
import cm.aekd.tontine.member.Member;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Épargne validée pour les tests qui approuvent un prêt : l'approbation est
 * bloquée au-delà de l'argent d'épargne disponible (docs/decisions.md n° 46).
 */
public final class TestSavings {

    private TestSavings() {
    }

    public static void seedValidated(SavingsDepositRepository repository, Member member, BigDecimal amount) {
        SavingsDeposit deposit = new SavingsDeposit(member, amount, LocalDate.now(), PaymentOperator.CASH,
                "TEST-EPARGNE", null, member.getUser());
        deposit.setStatus(SavingsDepositStatus.VALIDATED);
        deposit.setValidatedAt(Instant.now());
        repository.save(deposit);
    }
}
