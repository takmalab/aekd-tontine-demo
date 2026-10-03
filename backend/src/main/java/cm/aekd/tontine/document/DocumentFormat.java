package cm.aekd.tontine.document;

import cm.aekd.tontine.contribution.ContributionPeriodStatus;
import cm.aekd.tontine.contribution.ContributionTransactionStatus;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.contribution.PaymentOperator;
import cm.aekd.tontine.loan.LoanStatus;
import cm.aekd.tontine.repayment.LoanRepaymentStatus;
import cm.aekd.tontine.sanction.AppliedSanctionStatus;
import cm.aekd.tontine.sanction.SanctionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Mise en forme commune des documents PDF : montants, dates et libellés
 * français (identiques à ceux de l'interface web).
 */
public final class DocumentFormat {

    /** Fuseau de l'association (Douala). */
    public static final ZoneId ZONE = ZoneId.of("Africa/Douala");

    private static final Locale FR = Locale.FRANCE;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", FR);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", FR);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", FR);

    private DocumentFormat() {
    }

    /** « 50 000 FCFA » (espace insécable comme séparateur de milliers, police WinAnsi). */
    public static String fcfa(BigDecimal amount) {
        if (amount == null) {
            return "–";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(FR);
        symbols.setGroupingSeparator('\u00A0');
        DecimalFormat format = new DecimalFormat("#,##0", symbols);
        return format.format(amount.setScale(0, RoundingMode.HALF_UP)) + "\u00A0FCFA";
    }

    public static String date(LocalDate date) {
        return date == null ? "–" : date.format(DATE);
    }

    public static String longDate(LocalDate date) {
        if (date == null) {
            return "–";
        }
        String text = date.format(LONG_DATE);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    public static String date(Instant instant) {
        return instant == null ? "–" : instant.atZone(ZONE).toLocalDate().format(DATE);
    }

    public static String dateTime(Instant instant) {
        return instant == null ? "–" : instant.atZone(ZONE).format(DATE_TIME);
    }

    /** Numéro court lisible dérivé de l'identifiant (ex. « 3F2A9C1B »). */
    public static String shortRef(UUID id) {
        return id.toString().substring(0, 8).toUpperCase(FR);
    }

    public static String orDash(String value) {
        return value == null || value.isBlank() ? "–" : value;
    }

    public static String operator(PaymentOperator operator) {
        if (operator == null) {
            return "–";
        }
        return switch (operator) {
            case MTN_MOMO -> "MTN Mobile Money";
            case ORANGE_MONEY -> "Orange Money";
            case BANK_TRANSFER -> "Virement bancaire";
            case CASH -> "Espèces";
        };
    }

    public static String transactionStatus(ContributionTransactionStatus status) {
        return switch (status) {
            case PENDING -> "En attente";
            case VALIDATED -> "Validé";
            case REJECTED -> "Rejeté";
        };
    }

    /**
     * Situation d'un participant, comme sur l'écran de détail d'une cotisation :
     * une cotisation facultative non payée n'est jamais « en retard ».
     */
    public static String participantStatus(ContributionPeriodStatus status, boolean mandatory) {
        return switch (status) {
            case PAID -> "Payé";
            case PENDING -> "En attente";
            case PARTIAL -> "Partiel";
            case LATE -> mandatory ? "En retard" : "Non payé";
            case NOT_PAID -> "Non payé";
        };
    }

    public static String destination(FundDestination destination) {
        return switch (destination) {
            case TONTINE_FUND -> "Fonds commun";
            case INDIVIDUAL_SAVINGS -> "Épargne individuelle";
        };
    }

    public static String loanStatus(LoanStatus status) {
        return switch (status) {
            case REQUESTED -> "Demandé";
            case APPROVED -> "Approuvé";
            case IN_PROGRESS -> "En remboursement";
            case REJECTED -> "Rejeté";
            case REPAID -> "Remboursé";
        };
    }

    public static String repaymentStatus(LoanRepaymentStatus status) {
        return switch (status) {
            case PENDING -> "En attente";
            case VALIDATED -> "Validé";
            case REJECTED -> "Rejeté";
        };
    }

    public static String sanctionType(SanctionType type) {
        return switch (type) {
            case MONETARY -> "Monétaire";
            case IN_KIND -> "En nature";
        };
    }

    public static String sanctionStatus(AppliedSanctionStatus status) {
        return switch (status) {
            case APPLIED -> "Appliquée";
            case CANCELLED -> "Annulée";
        };
    }

    /** Nom de fichier sûr : minuscules, sans accents ni espaces. */
    public static String slug(String text) {
        String normalized = java.text.Normalizer.normalize(text == null ? "" : text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(FR)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isEmpty() ? "document" : normalized;
    }
}
