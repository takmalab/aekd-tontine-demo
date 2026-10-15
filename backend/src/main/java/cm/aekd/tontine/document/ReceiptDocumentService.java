package cm.aekd.tontine.document;

import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionTransaction;
import cm.aekd.tontine.contribution.ContributionTransactionRepository;
import cm.aekd.tontine.contribution.ContributionTransactionStatus;
import cm.aekd.tontine.loan.Loan;
import cm.aekd.tontine.repayment.LoanRepayment;
import cm.aekd.tontine.repayment.LoanRepaymentRepository;
import cm.aekd.tontine.repayment.LoanRepaymentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static cm.aekd.tontine.document.DocumentFormat.date;
import static cm.aekd.tontine.document.DocumentFormat.dateTime;
import static cm.aekd.tontine.document.DocumentFormat.fcfa;
import static cm.aekd.tontine.document.DocumentFormat.shortRef;

/**
 * Reçus PDF : paiement de cotisation validé et remboursement de prêt.
 * Un reçu de cotisation n'existe que pour un paiement VALIDÉ : seul un paiement
 * validé est considéré comme encaissé (CLAUDE.md §14).
 */
@Service
@Transactional(readOnly = true)
public class ReceiptDocumentService {

    private final ContributionTransactionRepository transactionRepository;
    private final LoanRepaymentRepository repaymentRepository;
    private final DocumentAccess access;

    public ReceiptDocumentService(ContributionTransactionRepository transactionRepository,
                                  LoanRepaymentRepository repaymentRepository,
                                  DocumentAccess access) {
        this.transactionRepository = transactionRepository;
        this.repaymentRepository = repaymentRepository;
        this.access = access;
    }

    public GeneratedDocument paymentReceipt(UUID transactionId) {
        ContributionTransaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paiement introuvable"));
        access.checkStaffOrMember(tx.getMember().getId(), "Paiement introuvable");
        if (tx.getStatus() != ContributionTransactionStatus.VALIDATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un reçu n'est disponible que pour un paiement validé.");
        }

        ContributionPeriod period = tx.getContributionPeriod();
        ContributionDefinition definition = period.getContributionDefinition();
        String number = "PAI-" + shortRef(tx.getId());

        AekdPdf pdf = AekdPdf.portrait("Reçu de paiement n° " + number, access.currentAuthorName())
                .background(PdfPattern.MASQUES, AekdPdf.ORANGE);
        pdf.heading("Reçu de paiement", "Reçu n° " + number + " – cotisation « " + definition.getName() + " »");
        pdf.amountBox("Montant reçu", fcfa(tx.getAmount()),
                "Arrêté le présent reçu à la somme de " + AmountInWords.francsCfa(tx.getAmount()) + ".");

        pdf.section("Membre et cotisation");
        pdf.keyValues(List.of(
                new String[]{"Membre", tx.getMember().getFullName()},
                new String[]{"Cotisation", definition.getName()},
                new String[]{"Séance", period.getSession().getLabel() + " (" + date(period.getSession().getSessionDate()) + ")"},
                new String[]{"Destination", DocumentFormat.destination(definition.getFundDestination())}
        ));

        pdf.section("Paiement");
        List<String[]> payment = new ArrayList<>(List.of(
                new String[]{"Date de paiement", date(tx.getPaymentDate())},
                new String[]{"Opérateur", DocumentFormat.operator(tx.getOperator())},
                new String[]{"Référence", tx.getTransactionReference()},
                new String[]{"Déclaré le", dateTime(tx.getCreatedAt())},
                new String[]{"Validé le", dateTime(tx.getValidatedAt())},
                new String[]{"Validé par", access.displayName(tx.getValidatedBy())}
        ));
        pdf.keyValues(payment);
        if (tx.getObservation() != null && !tx.getObservation().isBlank()) {
            pdf.keyValuesFull(List.<String[]>of(new String[]{"Observation", tx.getObservation()}));
        }

        pdf.signatures("Le trésorier", "Le membre");
        pdf.note("Ce reçu atteste qu'un paiement déclaré par le membre a été vérifié et validé par la trésorerie de l'"
                + AekdPdf.ASSOCIATION_SHORT + ". Il est généré électroniquement et peut être reproduit à l'identique.");

        return new GeneratedDocument("recu-paiement-" + number.toLowerCase() + ".pdf", pdf.finish());
    }

    public GeneratedDocument repaymentReceipt(UUID repaymentId) {
        LoanRepayment repayment = repaymentRepository.findById(repaymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Remboursement introuvable"));
        Loan loan = repayment.getLoan();
        access.checkStaffOrMember(loan.getMember().getId(), "Remboursement introuvable");
        if (repayment.getStatus() != LoanRepaymentStatus.VALIDATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un reçu n'est disponible que pour un remboursement validé.");
        }

        // Situation du prêt juste après ce remboursement (remboursements validés antérieurs ou égaux).
        List<LoanRepayment> history = repaymentRepository.findByLoanId(loan.getId()).stream()
                .filter(r -> r.getStatus() == LoanRepaymentStatus.VALIDATED)
                .sorted(Comparator.comparing(LoanRepayment::getCreatedAt))
                .toList();
        BigDecimal repaidSoFar = BigDecimal.ZERO;
        for (LoanRepayment r : history) {
            repaidSoFar = repaidSoFar.add(r.getAmount());
            if (r.getId().equals(repayment.getId())) {
                break;
            }
        }
        BigDecimal due = loan.getTotalDue() != null ? loan.getTotalDue() : BigDecimal.ZERO;
        BigDecimal remaining = due.subtract(repaidSoFar);
        String number = "REM-" + shortRef(repayment.getId());

        AekdPdf pdf = AekdPdf.portrait("Reçu de remboursement n° " + number, access.currentAuthorName())
                .background(PdfPattern.MASQUES, AekdPdf.BROWN);
        pdf.heading("Reçu de remboursement de prêt", "Reçu n° " + number + " – prêt n° PRE-" + shortRef(loan.getId()));
        pdf.amountBox("Montant remboursé", fcfa(repayment.getAmount()),
                "Arrêté le présent reçu à la somme de " + AmountInWords.francsCfa(repayment.getAmount()) + ".");

        pdf.section("Remboursement");
        pdf.keyValues(List.of(
                new String[]{"Membre", loan.getMember().getFullName()},
                new String[]{"Date", date(repayment.getPaymentDate())},
                new String[]{"Opérateur", DocumentFormat.operator(repayment.getOperator())},
                new String[]{"Référence", repayment.getReference()},
                new String[]{"Enregistré par", access.displayName(repayment.getRecordedBy())},
                new String[]{"Enregistré le", dateTime(repayment.getCreatedAt())}
        ));

        pdf.section("Situation du prêt après ce remboursement");
        pdf.statCards(List.of(
                new String[]{"À rembourser", fcfa(due)},
                new String[]{"Total remboursé", fcfa(repaidSoFar)},
                new String[]{"Solde restant", fcfa(remaining)}
        ));
        pdf.note("À rembourser : capital de " + fcfa(loan.getApprovedAmount() != null ? loan.getApprovedAmount() : BigDecimal.ZERO)
                + " + intérêts de " + fcfa(loan.getInterestAmount())
                + (loan.getInterestRate() != null
                        ? " (taux de " + loan.getInterestRate().stripTrailingZeros().toPlainString().replace('.', ',')
                                + " % " + cm.aekd.tontine.loan.LoanInterest.periodLabel(loan.getInterestPeriodMonths())
                                + " sur " + loan.getApprovedDurationMonths() + " mois)."
                        : "."));

        pdf.signatures("Le trésorier", "Le membre");
        return new GeneratedDocument("recu-remboursement-" + number.toLowerCase() + ".pdf", pdf.finish());
    }
}
