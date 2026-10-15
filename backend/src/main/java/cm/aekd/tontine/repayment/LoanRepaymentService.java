package cm.aekd.tontine.repayment;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.contribution.StoredResource;
import cm.aekd.tontine.document.DocumentFormat;
import cm.aekd.tontine.notification.NotificationService;
import cm.aekd.tontine.notification.NotificationType;
import cm.aekd.tontine.loan.Loan;
import cm.aekd.tontine.member.Member;
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
import java.time.LocalDate;
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
    private final NotificationService notificationService;
    private final cm.aekd.tontine.fund.FundService fundService;

    public LoanRepaymentService(LoanRepository loanRepository, LoanRepaymentRepository repaymentRepository,
                                 CurrentUserProvider currentUserProvider, UserRepository userRepository,
                                 AuditLogService auditLogService, FileStorageService fileStorageService,
                                 NotificationService notificationService,
                                 cm.aekd.tontine.fund.FundService fundService) {
        this.fundService = fundService;
        this.notificationService = notificationService;
        this.loanRepository = loanRepository;
        this.repaymentRepository = repaymentRepository;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Remboursement reçu et saisi par le trésorier : comptabilisé immédiatement.
     * Comme pour la validation (docs/decisions.md n° 40), le trésorier ne saisit
     * pas ainsi un remboursement de son propre prêt : il le déclare et un autre
     * membre du bureau le valide.
     */
    public LoanRepaymentResponse record(UUID loanId, LoanRepaymentRequest request) {
        Loan loan = getLoanOrThrow(loanId);
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId != null && currentMemberId.equals(loan.getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous ne pouvez pas enregistrer directement un remboursement de votre propre prêt : déclarez-le");
        }
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
        notificationService.notifyMember(loan.getMember(), NotificationType.LOAN_REPAYMENT_RECORDED,
                "Remboursement enregistré",
                "Le trésorier a enregistré votre remboursement de " + DocumentFormat.fcfa(request.amount())
                        + ". Reste à rembourser : " + DocumentFormat.fcfa(remaining.subtract(request.amount())) + ".",
                "/my-loans");
        notifyOtherMembers(loan, request.amount());

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
        notificationService.notifyStaff(NotificationType.LOAN_REPAYMENT_DECLARED, "Remboursement à valider",
                loan.getMember().getFullName() + " a déclaré un remboursement de "
                        + DocumentFormat.fcfa(request.amount()) + ".",
                "/payments");

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
        notificationService.notifyMember(loan.getMember(), NotificationType.LOAN_REPAYMENT_VALIDATED,
                "Remboursement validé",
                "Votre remboursement de " + DocumentFormat.fcfa(repayment.getAmount()) + " a été validé. "
                        + "Reste à rembourser : " + DocumentFormat.fcfa(remaining.subtract(repayment.getAmount())) + ".",
                "/my-loans");
        notifyOtherMembers(loan, repayment.getAmount());

        return LoanRepaymentResponse.from(repayment);
    }

    private void notifyOtherMembers(Loan loan, BigDecimal amount) {
        notificationService.notifyOtherMembers(loan.getMember(), null, "Remboursement de prêt",
                loan.getMember().getFullName() + " a remboursé " + DocumentFormat.fcfa(amount) + " sur son prêt.");
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
        notificationService.notifyMember(repayment.getLoan().getMember(), NotificationType.LOAN_REPAYMENT_REJECTED,
                "Remboursement rejeté",
                "Votre remboursement de " + DocumentFormat.fcfa(repayment.getAmount())
                        + " a été rejeté. Contactez le trésorier pour en savoir plus.",
                "/my-loans");

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
        BigDecimal total = loan.getTotalDue() != null ? loan.getTotalDue() : BigDecimal.ZERO;
        BigDecimal remaining = remainingBalance(loan);
        return new LoanBalanceResponse(total, total.subtract(remaining), remaining,
                repaymentRepository.sumPendingAmountByLoanId(loanId),
                loan.getApprovedAmount() != null ? loan.getApprovedAmount() : BigDecimal.ZERO,
                loan.getInterestAmount(), loan.getInterestRate());
    }

    // ---------- Recouvrement (docs/decisions.md n° 53) ----------

    /** Ce qui peut encore être recouvré : solde restant moins les remboursements en attente. */
    @Transactional(readOnly = true)
    public BigDecimal outstanding(Loan loan) {
        if (!REPAYABLE.contains(loan.getStatus())) {
            return BigDecimal.ZERO;
        }
        return remainingBalance(loan).subtract(repaymentRepository.sumPendingAmountByLoanId(loan.getId()))
                .max(BigDecimal.ZERO);
    }

    /**
     * L'avaliste peut-il être mis à contribution ? « L'emprunteur d'abord » : il faut
     * que l'avaliste ait accepté et que l'épargne de l'emprunteur soit épuisée.
     * Renvoie la raison du refus, ou null si c'est possible.
     */
    @Transactional(readOnly = true)
    public String guarantorBlockReason(Loan loan) {
        if (loan.getGuarantor() == null || loan.getGuarantorStatus() != cm.aekd.tontine.loan.GuarantorStatus.ACCEPTED) {
            return "Ce prêt n'a pas d'avaliste qui a accepté";
        }
        BigDecimal borrowerSavings = fundService.individualSavingsOf(loan.getMember().getId());
        if (borrowerSavings.signum() > 0) {
            return "L'emprunteur a encore " + DocumentFormat.fcfa(borrowerSavings)
                    + " d'épargne : prélevez-la d'abord (l'emprunteur d'abord)";
        }
        return null;
    }

    /**
     * Enregistre une retenue sur cagnotte ou un prélèvement sur épargne comme un
     * remboursement validé, puis met le prêt à jour (statut, solde).
     */
    public LoanRepayment applyDeduction(Loan loan, BigDecimal amount, LocalDate date, RepaymentSource source,
                                        Member payer, cm.aekd.tontine.payout.SessionPayout payout, String reference) {
        checkRepayable(loan);
        BigDecimal available = outstanding(loan);
        if (amount.signum() <= 0 || amount.compareTo(available) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La retenue (" + DocumentFormat.fcfa(amount)
                    + ") dépasse ce qui reste à recouvrer sur le prêt de " + loan.getMember().getFullName()
                    + " (" + DocumentFormat.fcfa(available) + ")");
        }
        BigDecimal remaining = remainingBalance(loan);
        User recordedBy = currentUserOrThrow();
        LoanRepayment repayment = new LoanRepayment(loan, amount, date, null, reference, recordedBy);
        repayment.setValidatedBy(recordedBy);
        repayment.setValidatedAt(Instant.now());
        repayment.markAsDeduction(source, payer, payout);
        repayment = repaymentRepository.save(repayment);
        BigDecimal newRemaining = remaining.subtract(amount);
        applyToLoan(loan, newRemaining);

        boolean byGuarantor = !payer.getId().equals(loan.getMember().getId());
        String how = source == RepaymentSource.PAYOUT_DEDUCTION ? "retenus sur la cagnotte" : "prélevés sur l'épargne";
        auditLogService.record(AuditAction.LOAN_DEDUCTION_RECORDED, "LoanRepayment", repayment.getId(),
                DocumentFormat.fcfa(amount) + " " + how + " de " + payer.getFullName()
                        + (byGuarantor ? " (avaliste)" : "") + " pour le prêt de " + loan.getMember().getFullName());
        notificationService.notifyMember(loan.getMember(), NotificationType.LOAN_DEDUCTION_RECORDED,
                "Retenue pour votre prêt",
                DocumentFormat.fcfa(amount) + " " + how + (byGuarantor ? " de votre avaliste " + payer.getFullName() : "")
                        + " pour rembourser votre prêt. Reste à rembourser : " + DocumentFormat.fcfa(newRemaining) + ".",
                "/my-loans");
        if (byGuarantor) {
            notificationService.notifyMember(payer, NotificationType.LOAN_DEDUCTION_RECORDED,
                    "Retenue en tant qu'avaliste",
                    DocumentFormat.fcfa(amount) + " " + how + " pour le prêt de " + loan.getMember().getFullName()
                            + " que vous garantissez.",
                    "/my-loans");
        }
        return repayment;
    }

    /**
     * Prélèvement sur l'épargne de l'emprunteur, ou de l'avaliste si l'épargne de
     * l'emprunteur est épuisée (decisions.md n° 53). Seulement pour un prêt dont
     * l'échéance est dépassée (« s'il ne rembourse pas »).
     */
    public LoanRepaymentResponse deductFromSavings(UUID loanId, SavingsDeductionRequest request) {
        Loan loan = getLoanOrThrow(loanId);
        checkRepayable(loan);
        if (!loan.isOverdue(LocalDate.now(DocumentFormat.ZONE))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le prêt n'est pas en retard : l'épargne ne peut être prélevée qu'après l'échéance");
        }
        Member payer;
        if (request.payer() == SavingsDeductionRequest.Payer.GUARANTOR) {
            String reason = guarantorBlockReason(loan);
            if (reason != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
            }
            payer = loan.getGuarantor();
        } else {
            payer = loan.getMember();
        }
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId != null && (currentMemberId.equals(loan.getMember().getId())
                || currentMemberId.equals(payer.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous ne pouvez pas faire ce prélèvement : il vous concerne (un autre membre du bureau doit le faire)");
        }
        BigDecimal savings = fundService.individualSavingsOf(payer.getId());
        if (request.amount().compareTo(savings) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Épargne insuffisante : "
                    + payer.getFullName() + " a " + DocumentFormat.fcfa(savings.max(BigDecimal.ZERO)) + " d'épargne");
        }
        LoanRepayment repayment = applyDeduction(loan, request.amount(), LocalDate.now(DocumentFormat.ZONE),
                RepaymentSource.SAVINGS_DEDUCTION, payer, null, "Prélèvement sur l'épargne de " + payer.getFullName());
        return LoanRepaymentResponse.from(repayment);
    }

    /** Capital + intérêts (CLAUDE.md §23, decisions.md n° 19) moins les remboursements validés. */
    private BigDecimal remainingBalance(Loan loan) {
        if (loan.getTotalDue() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalRepaid = repaymentRepository.sumAmountByLoanId(loan.getId());
        return loan.getTotalDue().subtract(totalRepaid);
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
        // L'avaliste suit aussi le prêt qu'il garantit (docs/decisions.md n° 52).
        boolean isGuarantor = currentMemberId != null && loan.getGuarantor() != null
                && currentMemberId.equals(loan.getGuarantor().getId());
        if (!isGuarantor && (currentMemberId == null || !currentMemberId.equals(loan.getMember().getId()))) {
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
