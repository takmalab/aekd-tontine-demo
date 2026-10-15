package cm.aekd.tontine.sanction;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.contribution.StoredResource;
import cm.aekd.tontine.document.DocumentFormat;
import cm.aekd.tontine.notification.NotificationService;
import cm.aekd.tontine.notification.NotificationType;
import cm.aekd.tontine.sanction.SanctionPaymentDtos.DeclareRequest;
import cm.aekd.tontine.sanction.SanctionPaymentDtos.PaymentResponse;
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
import java.util.List;
import java.util.UUID;

/**
 * Paiement des sanctions monétaires (decisions.md n° 50) : le membre déclare,
 * éventuellement en plusieurs fois, sans dépasser le reste à payer ; le
 * trésorier valide ou rejette. Seuls les paiements validés alimentent le
 * compte Sanctions ; la sanction est payée quand leur total atteint son montant.
 */
@Service
@Transactional
public class SanctionPaymentService {

    private final SanctionPaymentRepository paymentRepository;
    private final AppliedSanctionRepository sanctionRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public SanctionPaymentService(SanctionPaymentRepository paymentRepository, AppliedSanctionRepository sanctionRepository,
                                  UserRepository userRepository, CurrentUserProvider currentUserProvider,
                                  FileStorageService fileStorageService, AuditLogService auditLogService,
                                  NotificationService notificationService) {
        this.paymentRepository = paymentRepository;
        this.sanctionRepository = sanctionRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.fileStorageService = fileStorageService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    /** Montants payés (validés) et en attente d'une sanction, pour l'affichage. */
    @Transactional(readOnly = true)
    public PaymentTotals totalsOf(UUID sanctionId) {
        return new PaymentTotals(
                paymentRepository.sumBySanctionAndStatus(sanctionId, SanctionPaymentStatus.VALIDATED),
                paymentRepository.sumBySanctionAndStatus(sanctionId, SanctionPaymentStatus.PENDING));
    }

    public record PaymentTotals(BigDecimal paid, BigDecimal pending) {
    }

    public PaymentResponse declare(UUID sanctionId, DeclareRequest request) {
        AppliedSanction sanction = sanctionRepository.findById(sanctionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sanction introuvable"));
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId == null || !currentMemberId.equals(sanction.getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous ne pouvez payer que vos propres sanctions");
        }
        if (sanction.getStatus() != AppliedSanctionStatus.APPLIED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette sanction a été annulée");
        }
        if (sanction.getAmount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Une sanction en nature ne se paie pas en argent");
        }
        PaymentTotals totals = totalsOf(sanctionId);
        BigDecimal remaining = sanction.getAmount().subtract(totals.paid()).subtract(totals.pending());
        if (request.amount().compareTo(remaining) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, remaining.signum() <= 0
                    ? "Cette sanction est déjà payée ou en attente de validation pour la totalité"
                    : "Le paiement dépasse le reste à payer (" + DocumentFormat.fcfa(remaining) + ")");
        }

        SanctionPayment payment = paymentRepository.save(new SanctionPayment(sanction, request.amount(),
                request.paymentDate(), request.operator(), request.reference().trim(),
                request.observation() == null || request.observation().isBlank() ? null : request.observation().trim(),
                currentUserOrThrow()));

        auditLogService.record(AuditAction.SANCTION_PAYMENT_DECLARED, "SanctionPayment", payment.getId(),
                "Paiement de sanction de " + request.amount() + " déclaré par " + sanction.getMember().getFullName()
                        + " (en attente de validation)");
        notificationService.notifyStaff(NotificationType.SANCTION_PAYMENT_DECLARED, "Paiement de sanction à valider",
                sanction.getMember().getFullName() + " a déclaré le paiement de " + DocumentFormat.fcfa(request.amount())
                        + " pour une sanction.",
                "/sanctions");
        return PaymentResponse.from(payment);
    }

    public PaymentResponse validate(UUID paymentId) {
        SanctionPayment payment = pendingForDecision(paymentId);
        AppliedSanction sanction = payment.getAppliedSanction();
        BigDecimal paid = paymentRepository.sumBySanctionAndStatus(sanction.getId(), SanctionPaymentStatus.VALIDATED);
        if (payment.getAmount().compareTo(sanction.getAmount().subtract(paid)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce paiement dépasse le reste à payer de la sanction : rejetez-le");
        }
        payment.decide(SanctionPaymentStatus.VALIDATED, currentUserOrThrow());

        auditLogService.record(AuditAction.SANCTION_PAYMENT_VALIDATED, "SanctionPayment", payment.getId(),
                "Paiement de sanction de " + payment.getAmount() + " validé pour " + payment.getMember().getFullName());
        notificationService.notifyMember(payment.getMember(), NotificationType.SANCTION_PAYMENT_VALIDATED,
                "Paiement de sanction validé",
                "Votre paiement de " + DocumentFormat.fcfa(payment.getAmount()) + " pour votre sanction est validé.",
                "/sanctions");
        return PaymentResponse.from(payment);
    }

    public PaymentResponse reject(UUID paymentId) {
        SanctionPayment payment = pendingForDecision(paymentId);
        payment.decide(SanctionPaymentStatus.REJECTED, currentUserOrThrow());

        auditLogService.record(AuditAction.SANCTION_PAYMENT_REJECTED, "SanctionPayment", payment.getId(),
                "Paiement de sanction de " + payment.getAmount() + " rejeté pour " + payment.getMember().getFullName());
        notificationService.notifyMember(payment.getMember(), NotificationType.SANCTION_PAYMENT_REJECTED,
                "Paiement de sanction rejeté",
                "Votre paiement de " + DocumentFormat.fcfa(payment.getAmount())
                        + " a été rejeté. Contactez le trésorier pour en savoir plus.",
                "/sanctions");
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> pending() {
        return paymentRepository.findByStatusOrderByCreatedAtAsc(SanctionPaymentStatus.PENDING).stream()
                .map(PaymentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> mine() {
        UUID memberId = currentUserProvider.currentMemberId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Aucun profil membre associé à ce compte"));
        return paymentRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(PaymentResponse::from).toList();
    }

    /** Justificatif facultatif, transmis par le membre tant que le paiement est en attente. */
    public PaymentResponse uploadProof(UUID paymentId, MultipartFile file) {
        SanctionPayment payment = getOrThrow(paymentId);
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId == null || !currentMemberId.equals(payment.getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous ne pouvez transmettre un justificatif que pour votre propre paiement");
        }
        if (payment.getStatus() != SanctionPaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce paiement a déjà été traité, le justificatif ne peut plus être modifié");
        }
        ProofFiles.validate(file);

        String previousKey = payment.getProofStorageKey();
        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = fileStorageService.store("sanction-proofs/" + paymentId, file.getOriginalFilename(),
                    file.getContentType(), in);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Échec de l'enregistrement du justificatif");
        }
        payment.attachProof(stored.storageKey(), stored.originalFilename(), stored.contentType(), stored.size());
        if (previousKey != null && !previousKey.equals(stored.storageKey())) {
            try {
                fileStorageService.delete(previousKey);
            } catch (IOException ignored) {
                // Suppression best-effort : ne doit pas bloquer le remplacement du justificatif.
            }
        }
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public StoredResource downloadProof(UUID paymentId) {
        SanctionPayment payment = getOrThrow(paymentId);
        if (!currentUserProvider.hasAnyRole("ADMIN", "TRESORIER")) {
            UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
            if (currentMemberId == null || !currentMemberId.equals(payment.getMember().getId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paiement introuvable");
            }
        }
        if (!payment.hasProof()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun justificatif pour ce paiement");
        }
        try {
            return new StoredResource(fileStorageService.retrieve(payment.getProofStorageKey()),
                    payment.getProofContentType(), payment.getProofOriginalFilename());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Justificatif introuvable sur le stockage");
        }
    }

    /** Le trésorier ne statue pas sur le paiement de sa propre sanction (decisions.md n° 40). */
    private SanctionPayment pendingForDecision(UUID paymentId) {
        SanctionPayment payment = getOrThrow(paymentId);
        if (payment.getStatus() != SanctionPaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce paiement a déjà été traité");
        }
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId != null && currentMemberId.equals(payment.getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous ne pouvez pas valider ou rejeter le paiement de votre propre sanction");
        }
        return payment;
    }

    private SanctionPayment getOrThrow(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paiement introuvable"));
    }

    private User currentUserOrThrow() {
        return currentUserProvider.currentUserId().flatMap(userRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié"));
    }
}
