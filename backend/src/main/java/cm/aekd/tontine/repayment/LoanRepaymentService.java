package cm.aekd.tontine.repayment;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.loan.Loan;
import cm.aekd.tontine.loan.LoanRepository;
import cm.aekd.tontine.loan.LoanStatus;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * CLAUDE.md §23. Empêche tout remboursement supérieur au solde restant
 * (docs/decisions.md §21, DÉCIDÉ) et autorise les remboursements partiels
 * successifs (§20, DÉCIDÉ). Le solde se calcule contre le montant
 * approuvé (principal) : le mode de calcul des intérêts reste "à
 * définir" (§14), donc aucun montant majoré n'est inventé ici.
 */
@Service
@Transactional
public class LoanRepaymentService {

    private final LoanRepository loanRepository;
    private final LoanRepaymentRepository repaymentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public LoanRepaymentService(LoanRepository loanRepository, LoanRepaymentRepository repaymentRepository,
                                 CurrentUserProvider currentUserProvider, UserRepository userRepository,
                                 AuditLogService auditLogService) {
        this.loanRepository = loanRepository;
        this.repaymentRepository = repaymentRepository;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    public LoanRepaymentResponse record(UUID loanId, LoanRepaymentRequest request) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prêt introuvable"));

        if (loan.getStatus() != LoanStatus.APPROVED && loan.getStatus() != LoanStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce prêt n'est pas dans un état permettant un remboursement");
        }

        BigDecimal remaining = remainingBalance(loan);
        if (request.amount().compareTo(remaining) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le remboursement (" + request.amount() + ") dépasse le solde restant (" + remaining + ")");
        }

        User recordedBy = currentUserProvider.currentUserId()
                .flatMap(userRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));

        LoanRepayment repayment = new LoanRepayment(loan, request.amount(), request.paymentDate(),
                request.operator(), request.reference(), recordedBy);
        repayment = repaymentRepository.save(repayment);

        BigDecimal newRemaining = remaining.subtract(request.amount());
        if (newRemaining.signum() == 0) {
            loan.setStatus(LoanStatus.REPAID);
        } else if (loan.getStatus() == LoanStatus.APPROVED) {
            loan.setStatus(LoanStatus.IN_PROGRESS);
        }
        loanRepository.save(loan);

        auditLogService.record(AuditAction.LOAN_REPAYMENT_RECORDED, "LoanRepayment", repayment.getId(),
                "Remboursement de " + request.amount() + " enregistré pour le prêt de " + loan.getMember().getFullName());

        return LoanRepaymentResponse.from(repayment);
    }

    public List<LoanRepaymentResponse> findByLoan(UUID loanId) {
        Loan loan = getLoanOrThrow(loanId);
        checkCanView(loan);
        return repaymentRepository.findByLoanId(loanId).stream()
                .map(LoanRepaymentResponse::from)
                .toList();
    }

    public LoanBalanceResponse balance(UUID loanId) {
        Loan loan = getLoanOrThrow(loanId);
        checkCanView(loan);
        BigDecimal total = loan.getApprovedAmount() != null ? loan.getApprovedAmount() : BigDecimal.ZERO;
        BigDecimal remaining = remainingBalance(loan);
        return new LoanBalanceResponse(total, total.subtract(remaining), remaining);
    }

    private BigDecimal remainingBalance(Loan loan) {
        if (loan.getApprovedAmount() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalRepaid = repaymentRepository.sumAmountByLoanId(loan.getId());
        return loan.getApprovedAmount().subtract(totalRepaid);
    }

    private void checkCanView(Loan loan) {
        if (currentUserProvider.hasAnyRole("ADMIN", "TRESORIER")) {
            return;
        }
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId == null || !currentMemberId.equals(loan.getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Prêt introuvable");
        }
    }

    private Loan getLoanOrThrow(UUID loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prêt introuvable"));
    }
}
