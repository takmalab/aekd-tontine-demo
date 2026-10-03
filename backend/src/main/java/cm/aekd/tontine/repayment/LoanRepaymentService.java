package cm.aekd.tontine.repayment;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.contribution.StoredResource;
import cm.aekd.tontine.loan.Loan;
import cm.aekd.tontine.loan.LoanRepository;
import cm.aekd.tontine.loan.LoanStatus;
import cm.aekd.tontine.storage.FileStorageService;
import cm.aekd.tontine.storage.ProofFiles;
import cm.aekd.tontine.storage.StoredFile;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CLAUDE.md §23. Empêche tout remboursement supérieur au solde restant
 * (docs/decisions.md §21, DÉCIDÉ) et autorise les remboursements partiels
 * successifs (§20, DÉCIDÉ). Le solde se calcule contre le montant
 * approuvé (principal) : le mode de calcul des intérêts reste "à
 * définir" (§14), donc aucun montant majoré n'est inventé ici.
 *
 * Deux voies (docs/decisions.md n° 38) :
 * - le trésorier enregistre un remboursement reçu : VALIDATED immédiatement ;
 * - le membre déclare son remboursement (justificatif facultatif) : PENDING,
 *   puis le trésorier le valide ou le rejette. Seuls les remboursements
 *   VALIDATED réduisent le solde.
 */
@Service
@Transactional
public class LoanRepaymentService {

    private static final List<LoanStatus> REPAYABLE = List.of(LoanStatus.APPROVED, LoanStatus.IN_PROGRESS);

    private final LoanRepository loanRepository;
    private final LoanRepaymentRepository repaymentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final FileStorageService fileStorageService;

    public LoanRepaymentService(LoanRepository loanRepository, LoanRepaymentRepository repaymentRepository,
                                 CurrentUserProvider currentUserProvider, UserRepository userRepository,
                                 AuditLogService auditLogService, FileStorageService fileStorageService) {
        this.loanRepository = loanRepository;
        this.repaymentRepository = repaymentRepository;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.fileStorageService = fileStorageService;
    }

    /** Remboursement reçu et saisi par le trésorier : comptabilisé immédiatement. */
    public LoanRepaymentResponse record(UUID loanId, LoanRepaymentRequest request) {
        Loan loan = getLoanOrThrow(loanId);
        checkRepayable(loan);

        BigDecimal remaining = remainingBalance(loan);
        if (request.amount().compareTo(remaining) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le remboursement (" + request.amount() + ") dépasse le solde restant (" + remaining + ")");
        }

        User recordedBy = currentUserOrThrow();
        LoanRepayment repayment = new LoanRepayment(loan, request.amount(), request.paymentDate(),
                request.operator(), request.reference(), recordedBy);
        repayment.setValidatedBy(recordedBy);
        repayment.setValidatedAt(Instant.now());
        repayment = repaymentRepository.save(repayment);
        applyToLoan(loan, remaining.subtract(request.amount()));

        auditLogService.record(AuditAction.LOAN_REPAYMENT_RECORDED, "LoanRepayment", repayment.getId(),
                "Remboursement de " + request.amount() + " enregistré pour le prêt de " + loan.getMember().getFullName());

        return LoanRepaymentResponse.from(repayment);
    }

    /**
     * Le membre déclare un remboursement de son propre prêt, partiel ou total.
     * Le montant ne peut pas dépasser le solde restant diminué des
     * remboursements déjà en attente de validation.
     */
    public LoanRepaymentResponse declare(UUID loanId, LoanRepaymentRequest request) {
        Loan loan = getLoanOrThrow(loanId);
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId == null || !currentMemberId.equals(loan.getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Prêt introuvable");
        }
        checkRepayable(loan);

        BigDecimal available = remainingBalance(loan).subtract(repaymentRepository.sumPendingAmountByLoanId(loanId));
        if (request.amount().compareTo(available) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, available.signum() <= 0
                    ? "Des remboursements en attente de validation couvrent déjà tout le solde restant."
                    : "Le remboursement (" + request.amount() + ") dépasse le montant restant à rembourser ("
                    + available + ", remboursements en attente déduits).");
        }

        LoanRepayment repayment = repaymentRepository.save(new LoanRepayment(loan, request.amount(),
                request.paymentDate(), request.operator(), request.reference(), currentUserOrThrow(),
                LoanRepaymentStatus.PENDING));

        auditLogService.record(AuditAction.LOAN_REPAYMENT_DECLARED, "LoanRepayment", repayment.getId(),
                "Remboursement de " + request.amount() + " déclaré par " + loan.getMember().getFullName()
                        + " (en attente de validation)");

        return LoanRepaymentResponse.from(repayment);
    }

    public LoanRepaymentResponse validate(UUID repaymentId) {
        LoanRepayment repayment = getPendingForDecision(repaymentId);
        Loan loan = repayment.getLoan();
        checkRepayable(loan);

        BigDecimal remaining = remainingBalance(loan);
        if (repayment.getAmount().compareTo(remaining) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce remboursement (" + repayment.getAmount() + ") dépasse le solde restant (" + remaining
                            + ") : rejetez-le.");
        }

        repayment.setStatus(LoanRepaymentStatus.VALIDATED);
        repayment.setValidatedBy(currentUserOrThrow());
        repayment.setValidatedAt(Instant.now());
        repaymentRepository.save(repayment);
        applyToLoan(loan, remaining.subtract(repayment.getAmount()));

        auditLogService.record(AuditAction.LOAN_REPAYMENT_VALIDATED, "LoanRepayment", repayment.getId(),
                "Remboursement de " + repayment.getAmount() + " validé pour le prêt de " + loan.getMember().getFullName());

        return LoanRepaymentResponse.from(repayment);
    }

    public LoanRepaymentResponse reject(UUID repaymentId) {
        LoanRepayment repayment = getPendingForDecision(repaymentId);
        repayment.setStatus(LoanRepaymentStatus.REJECTED);
        repayment.setValidatedBy(currentUserOrThrow());
        repayment.setValidatedAt(Instant.now());
        repaymentRepository.save(repayment);

        auditLogService.record(AuditAction.LOAN_REPAYMENT_REJECTED, "LoanRepayment", repayment.getId(),
                "Remboursement de " + repayment.getAmount() + " rejeté pour le prêt de "
                        + repayment.getLoan().getMember().getFullName());

        return LoanRepaymentResponse.from(repayment);
    }

    /** Remboursements déclarés par les membres, du plus ancien au plus récent. */
    @Transactional(readOnly = true)
    public List<LoanRepaymentResponse> findPending() {
        return repaymentRepository.findByStatusOrderByCreatedAtAsc(LoanRepaymentStatus.PENDING).stream()
                .map(LoanRepaymentResponse::from)
                .toList();
    }

    /** Justificatif facultatif, transmis par le membre tant que le remboursement est en attente. */
    public LoanRepaymentResponse uploadProof(UUID repaymentId, MultipartFile file) {
        LoanRepayment repayment = getRepaymentOrThrow(repaymentId);
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId == null || !currentMemberId.equals(repayment.getLoan().getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous ne pouvez transmettre un justificatif que pour votre propre remboursement");
        }
        if (repayment.getStatus() != LoanRepaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce remboursement a déjà été traité, le justificatif ne peut plus être modifié");
        }
        ProofFiles.validate(file);

        String previousKey = repayment.getProofStorageKey();
        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = fileStorageService.store("repayment-proofs/" + repaymentId, file.getOriginalFilename(),
                    file.getContentType(), in);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Échec de l'enregistrement du justificatif");
        }
        repayment.attachProof(stored.storageKey(), stored.originalFilename(), stored.contentType(), stored.size());
        repaymentRepository.save(repayment);
        if (previousKey != null && !previousKey.equals(stored.storageKey())) {
            try {
                fileStorageService.delete(previousKey);
            } catch (IOException ignored) {
                // Suppression best-effort : ne doit pas bloquer le remplacement du justificatif.
            }
        }
        return LoanRepaymentResponse.from(repayment);
    }

    @Transactional(readOnly = true)
    public StoredResource downloadProof(UUID repaymentId) {
        LoanRepayment repayment = getRepaymentOrThrow(repaymentId);
        checkCanView(repayment.getLoan());
        if (!repayment.hasProof()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun justificatif pour ce remboursement");
        }
        try {
            return new StoredResource(fileStorageService.retrieve(repayment.getProofStorageKey()),
                    repayment.getProofContentType(), repayment.getProofOriginalFilename());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Justificatif introuvable sur le stockage");
        }
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
        return new LoanBalanceResponse(total, total.subtract(remaining), remaining,
                repaymentRepository.sumPendingAmountByLoanId(loanId));
    }

    private BigDecimal remainingBalance(Loan loan) {
        if (loan.getApprovedAmount() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalRepaid = repaymentRepository.sumAmountByLoanId(loan.getId());
        return loan.getApprovedAmount().subtract(totalRepaid);
    }

    /** Statut du prêt après un remboursement comptabilisé (CLAUDE.md §22, §23). */
    private void applyToLoan(Loan loan, BigDecimal newRemaining) {
        if (newRemaining.signum() == 0) {
            loan.setStatus(LoanStatus.REPAID);
        } else if (loan.getStatus() == LoanStatus.APPROVED) {
            loan.setStatus(LoanStatus.IN_PROGRESS);
        }
        loanRepository.save(loan);
    }

    private void checkRepayable(Loan loan) {
        if (!REPAYABLE.contains(loan.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce prêt n'est pas dans un état permettant un remboursement");
        }
    }

    /**
     * Remboursement en attente, décidé par le trésorier. Comme pour les paiements
     * de cotisations (docs/decisions.md n° 23), personne ne valide ni ne rejette
     * un remboursement de son propre prêt.
     */
    private LoanRepayment getPendingForDecision(UUID repaymentId) {
        LoanRepayment repayment = getRepaymentOrThrow(repaymentId);
        if (repayment.getStatus() != LoanRepaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce remboursement a déjà été traité");
        }
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId != null && currentMemberId.equals(repayment.getLoan().getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous ne pouvez pas valider ou rejeter le remboursement de votre propre prêt");
        }
        return repayment;
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

    private User currentUserOrThrow() {
        return currentUserProvider.currentUserId()
                .flatMap(userRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
    }

    private Loan getLoanOrThrow(UUID loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prêt introuvable"));
    }

    private LoanRepayment getRepaymentOrThrow(UUID repaymentId) {
        return repaymentRepository.findById(repaymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Remboursement introuvable"));
    }
}
