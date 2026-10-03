package cm.aekd.tontine.savings;

import cm.aekd.tontine.contribution.PaymentOperator;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Objets d'échange de l'API Épargne (CLAUDE.md principe 10 : pas d'entité JPA exposée). */
public final class SavingsDtos {

    private SavingsDtos() {
    }

    /** Versement libre déclaré par le membre. */
    public record DepositRequest(
            @NotNull @Positive BigDecimal amount,
            @NotNull LocalDate paymentDate,
            @NotNull PaymentOperator operator,
            @NotBlank @Size(max = 150) String reference,
            @Size(max = 500) String observation
    ) {
    }

    /** Nouveau taux mensuel (en %), applicable à partir d'une date. */
    public record RateRequest(
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal monthlyRate,
            @NotNull LocalDate effectiveFrom,
            @Size(max = 255) String note
    ) {
    }

    public record RateResponse(UUID id, BigDecimal monthlyRate, LocalDate effectiveFrom, String note,
                               String createdByEmail, Instant createdAt) {
        static RateResponse from(SavingsInterestRate rate) {
            return new RateResponse(rate.getId(), rate.getMonthlyRate(), rate.getEffectiveFrom(), rate.getNote(),
                    rate.getCreatedBy().getEmail(), rate.getCreatedAt());
        }
    }

    /** Origine d'un mouvement d'épargne. */
    public enum EntrySource {
        /** Versement libre (bouton « Épargner »). */
        DEPOSIT,
        /** Paiement d'une cotisation destinée à l'épargne individuelle (ex. « Banque / Épargne »). */
        CONTRIBUTION
    }

    /**
     * Un mouvement d'épargne, quelle que soit son origine. {@code status} :
     * PENDING, VALIDATED ou REJECTED. {@code interest} : intérêts acquis à ce
     * jour (versement validé et taux configuré), sinon null.
     */
    public record EntryResponse(
            UUID id,
            EntrySource source,
            UUID memberId,
            String memberFullName,
            String label,
            BigDecimal amount,
            LocalDate paymentDate,
            PaymentOperator operator,
            String reference,
            String observation,
            String status,
            Instant declaredAt,
            Instant validatedAt,
            String validatedByEmail,
            long completeMonths,
            BigDecimal interest,
            boolean hasProof
    ) {
    }

    /** Épargne d'un membre : capital validé, intérêts, en attente et historique. */
    public record MemberSavingsResponse(
            UUID memberId,
            String memberFullName,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal total,
            BigDecimal pendingAmount,
            long pendingCount,
            long validatedCount,
            BigDecimal currentMonthlyRate,
            boolean rateConfigured,
            List<EntryResponse> entries
    ) {
    }

    /** Ligne du tableau des épargnants (vue ADMIN / TRESORIER). */
    public record MemberRow(
            UUID memberId,
            String memberFullName,
            boolean active,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal total,
            long validatedCount,
            BigDecimal pendingAmount,
            LocalDate lastDepositDate
    ) {
    }

    /** Montant d'épargne validé sur un mois (« 2026-10 »). */
    public record MonthlyPoint(String month, BigDecimal amount, long count) {
    }

    /** États généraux de l'épargne (vue ADMIN / TRESORIER). */
    public record OverviewResponse(
            BigDecimal totalPrincipal,
            BigDecimal totalInterest,
            BigDecimal total,
            long saversCount,
            long membersCount,
            BigDecimal averagePerSaver,
            long pendingCount,
            BigDecimal pendingAmount,
            BigDecimal validatedThisMonth,
            BigDecimal currentMonthlyRate,
            boolean rateConfigured,
            List<MemberRow> members,
            List<MonthlyPoint> monthly
    ) {
    }
}
