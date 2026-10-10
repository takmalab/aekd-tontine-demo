package cm.aekd.tontine.savings;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.contribution.ContributionTransaction;
import cm.aekd.tontine.contribution.ContributionTransactionRepository;
import cm.aekd.tontine.contribution.ContributionTransactionStatus;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.contribution.StoredResource;
import cm.aekd.tontine.document.DocumentFormat;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.notification.NotificationService;
import cm.aekd.tontine.notification.NotificationType;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.savings.SavingsDtos.DepositRequest;
import cm.aekd.tontine.savings.SavingsDtos.EntryResponse;
import cm.aekd.tontine.savings.SavingsDtos.EntrySource;
import cm.aekd.tontine.savings.SavingsDtos.MemberRow;
import cm.aekd.tontine.savings.SavingsDtos.MemberSavingsResponse;
import cm.aekd.tontine.savings.SavingsDtos.MonthlyPoint;
import cm.aekd.tontine.savings.SavingsDtos.OverviewResponse;
import cm.aekd.tontine.savings.SavingsDtos.RateRequest;
import cm.aekd.tontine.savings.SavingsDtos.RateResponse;
import cm.aekd.tontine.savings.SavingsInterestCalculator.Accrual;
import cm.aekd.tontine.savings.SavingsInterestCalculator.Rate;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Épargne individuelle (CLAUDE.md §17, §24 ; docs/decisions.md n° 39).
 *
 * L'épargne d'un membre = versements libres VALIDÉS (bouton « Épargner »)
 * + paiements VALIDÉS des cotisations destinées à l'épargne individuelle
 * (ex. « Banque / Épargne »). Elle reste séparée du fonds commun.
 * Les intérêts sont calculés à la volée par {@link SavingsInterestCalculator}
 * à partir de la date de validation de chaque versement.
 */
@Service
@Transactional
public class SavingsService {

    /** Fuseau de l'association : les mois d'épargne sont comptés en dates locales de Douala. */
    private static final ZoneId ZONE = ZoneId.of("Africa/Douala");
    private static final int MONTHLY_HISTORY = 12;

    private final SavingsDepositRepository depositRepository;
    private final SavingsInterestRateRepository rateRepository;
    private final ContributionTransactionRepository transactionRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public SavingsService(SavingsDepositRepository depositRepository,
                          SavingsInterestRateRepository rateRepository,
                          ContributionTransactionRepository transactionRepository,
                          MemberRepository memberRepository,
                          UserRepository userRepository,
                          CurrentUserProvider currentUserProvider,
                          FileStorageService fileStorageService,
                          AuditLogService auditLogService,
                          NotificationService notificationService) {
        this.notificationService = notificationService;
        this.depositRepository = depositRepository;
        this.rateRepository = rateRepository;
        this.transactionRepository = transactionRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.fileStorageService = fileStorageService;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ versements

    /** Le membre connecté déclare un versement libre : en attente jusqu'à validation. */
    public EntryResponse declare(DepositRequest request) {
        Member member = currentMemberOrThrow();
        SavingsDeposit deposit = depositRepository.save(new SavingsDeposit(member, request.amount(),
                request.paymentDate(), request.operator(), request.reference().trim(),
                blankToNull(request.observation()), currentUserOrThrow()));

        auditLogService.record(AuditAction.SAVINGS_DEPOSIT_DECLARED, "SavingsDeposit", deposit.getId(),
                "Versement d'épargne de " + request.amount() + " déclaré par " + member.getFullName()
                        + " (en attente de validation)");
        notificationService.notifyStaff(NotificationType.SAVINGS_DEPOSIT_DECLARED, "Versement d'épargne à valider",
                member.getFullName() + " a déclaré un versement d'épargne de " + DocumentFormat.fcfa(request.amount())
                        + ".",
                "/savings");
        return toEntry(deposit, LocalDate.now(ZONE), rates());
    }

    public EntryResponse validate(UUID depositId) {
        SavingsDeposit deposit = getPendingForDecision(depositId);
        deposit.setStatus(SavingsDepositStatus.VALIDATED);
        deposit.setValidatedBy(currentUserOrThrow());
        deposit.setValidatedAt(Instant.now());
        depositRepository.save(deposit);

        auditLogService.record(AuditAction.SAVINGS_DEPOSIT_VALIDATED, "SavingsDeposit", deposit.getId(),
                "Versement d'épargne de " + deposit.getAmount() + " validé pour " + deposit.getMember().getFullName());
        notificationService.notifyMember(deposit.getMember(), NotificationType.SAVINGS_DEPOSIT_VALIDATED,
                "Versement d'épargne validé",
                "Votre versement de " + DocumentFormat.fcfa(deposit.getAmount()) + " est ajouté à votre épargne.",
                "/my-savings");
        notificationService.notifyOtherMembers(deposit.getMember(), null, "Versement d'épargne",
                deposit.getMember().getFullName() + " a versé " + DocumentFormat.fcfa(deposit.getAmount())
                        + " sur son épargne.");
        return toEntry(deposit, LocalDate.now(ZONE), rates());
    }

    public EntryResponse reject(UUID depositId) {
        SavingsDeposit deposit = getPendingForDecision(depositId);
        deposit.setStatus(SavingsDepositStatus.REJECTED);
        deposit.setValidatedBy(currentUserOrThrow());
        deposit.setValidatedAt(Instant.now());
        depositRepository.save(deposit);

        auditLogService.record(AuditAction.SAVINGS_DEPOSIT_REJECTED, "SavingsDeposit", deposit.getId(),
                "Versement d'épargne de " + deposit.getAmount() + " rejeté pour " + deposit.getMember().getFullName());
        notificationService.notifyMember(deposit.getMember(), NotificationType.SAVINGS_DEPOSIT_REJECTED,
                "Versement d'épargne rejeté",
                "Votre versement de " + DocumentFormat.fcfa(deposit.getAmount())
                        + " a été rejeté. Contactez le trésorier pour en savoir plus.",
                "/my-savings");
        return toEntry(deposit, LocalDate.now(ZONE), rates());
    }

    @Transactional(readOnly = true)
    public List<EntryResponse> pendingDeposits() {
        LocalDate today = LocalDate.now(ZONE);
        List<Rate> rates = rates();
        return depositRepository.findByStatusOrderByCreatedAtAsc(SavingsDepositStatus.PENDING).stream()
                .map(d -> toEntry(d, today, rates))
                .toList();
    }

    /** Justificatif facultatif, transmis par le membre tant que le versement est en attente. */
    public EntryResponse uploadProof(UUID depositId, MultipartFile file) {
        SavingsDeposit deposit = getDepositOrThrow(depositId);
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId == null || !currentMemberId.equals(deposit.getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous ne pouvez transmettre un justificatif que pour votre propre versement");
        }
        if (deposit.getStatus() != SavingsDepositStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce versement a déjà été traité, le justificatif ne peut plus être modifié");
        }
        ProofFiles.validate(file);

        String previousKey = deposit.getProofStorageKey();
        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = fileStorageService.store("savings-proofs/" + depositId, file.getOriginalFilename(),
                    file.getContentType(), in);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Échec de l'enregistrement du justificatif");
        }
        deposit.attachProof(stored.storageKey(), stored.originalFilename(), stored.contentType(), stored.size());
        depositRepository.save(deposit);
        if (previousKey != null && !previousKey.equals(stored.storageKey())) {
            try {
                fileStorageService.delete(previousKey);
            } catch (IOException ignored) {
                // Suppression best-effort : ne doit pas bloquer le remplacement du justificatif.
            }
        }
        return toEntry(deposit, LocalDate.now(ZONE), rates());
    }

    @Transactional(readOnly = true)
    public StoredResource downloadProof(UUID depositId) {
        SavingsDeposit deposit = getDepositOrThrow(depositId);
        if (!currentUserProvider.hasAnyRole("ADMIN", "TRESORIER")) {
            UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
            if (currentMemberId == null || !currentMemberId.equals(deposit.getMember().getId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Versement introuvable");
            }
        }
        if (!deposit.hasProof()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun justificatif pour ce versement");
        }
        try {
            return new StoredResource(fileStorageService.retrieve(deposit.getProofStorageKey()),
                    deposit.getProofContentType(), deposit.getProofOriginalFilename());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Justificatif introuvable sur le stockage");
        }
    }

    // ------------------------------------------------------------------ consultation

    @Transactional(readOnly = true)
    public MemberSavingsResponse mine() {
        return memberSavings(currentMemberOrThrow());
    }

    @Transactional(readOnly = true)
    public MemberSavingsResponse ofMember(UUID memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
        return memberSavings(member);
    }

    /** Historique de tous les mouvements d'épargne, du plus récent au plus ancien (ADMIN / TRESORIER). */
    @Transactional(readOnly = true)
    public List<EntryResponse> allEntries() {
        LocalDate today = LocalDate.now(ZONE);
        List<Rate> rates = rates();
        List<EntryResponse> entries = new ArrayList<>();
        depositRepository.findAllByOrderByCreatedAtDesc().forEach(d -> entries.add(toEntry(d, today, rates)));
        transactionRepository.findByContributionPeriod_ContributionDefinition_FundDestination(
                FundDestination.INDIVIDUAL_SAVINGS).forEach(t -> entries.add(toEntry(t, today, rates)));
        entries.sort(ENTRY_ORDER);
        return entries;
    }

    /** États généraux : totaux, épargnants, évolution mensuelle (ADMIN / TRESORIER). */
    @Transactional(readOnly = true)
    public OverviewResponse overview() {
        List<EntryResponse> entries = allEntries();
        List<Rate> rates = rates();
        LocalDate today = LocalDate.now(ZONE);

        Map<UUID, List<EntryResponse>> byMember = entries.stream()
                .collect(Collectors.groupingBy(EntryResponse::memberId));
        List<MemberRow> rows = new ArrayList<>();
        for (Member member : memberRepository.findAll()) {
            List<EntryResponse> own = byMember.getOrDefault(member.getId(), List.of());
            if (!member.isActive() && own.isEmpty()) {
                continue;
            }
            List<EntryResponse> validated = own.stream().filter(e -> "VALIDATED".equals(e.status())).toList();
            BigDecimal principal = sum(validated, EntryResponse::amount);
            BigDecimal interest = rates.isEmpty() ? BigDecimal.ZERO : sum(validated, EntryResponse::interest);
            rows.add(new MemberRow(member.getId(), member.getFullName(), member.isActive(), principal, interest,
                    principal.add(interest), validated.size(),
                    sum(own.stream().filter(e -> "PENDING".equals(e.status())).toList(), EntryResponse::amount),
                    validated.stream().map(EntryResponse::paymentDate).max(Comparator.naturalOrder()).orElse(null)));
        }
        rows.sort(Comparator.comparing(MemberRow::total).reversed()
                .thenComparing(MemberRow::memberFullName, String.CASE_INSENSITIVE_ORDER));

        List<EntryResponse> validated = entries.stream().filter(e -> "VALIDATED".equals(e.status())).toList();
        List<EntryResponse> pending = entries.stream().filter(e -> "PENDING".equals(e.status())).toList();
        BigDecimal totalPrincipal = sum(validated, EntryResponse::amount);
        BigDecimal totalInterest = rates.isEmpty() ? BigDecimal.ZERO : sum(validated, EntryResponse::interest);
        long savers = rows.stream().filter(r -> r.principal().signum() > 0).count();
        long activeMembers = memberRepository.findAll().stream().filter(Member::isActive).count();

        // Évolution : montants validés par mois de validation, sur les 12 derniers mois.
        Map<YearMonth, List<EntryResponse>> byMonth = validated.stream()
                .filter(e -> e.validatedAt() != null)
                .collect(Collectors.groupingBy(e -> YearMonth.from(e.validatedAt().atZone(ZONE))));
        Map<YearMonth, MonthlyPoint> monthly = new LinkedHashMap<>();
        YearMonth current = YearMonth.from(today);
        for (int i = MONTHLY_HISTORY - 1; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            List<EntryResponse> inMonth = byMonth.getOrDefault(month, List.of());
            monthly.put(month, new MonthlyPoint(month.toString(), sum(inMonth, EntryResponse::amount), inMonth.size()));
        }

        return new OverviewResponse(
                totalPrincipal,
                totalInterest,
                totalPrincipal.add(totalInterest),
                savers,
                activeMembers,
                savers == 0 ? BigDecimal.ZERO : totalPrincipal.divide(BigDecimal.valueOf(savers), 0, RoundingMode.HALF_UP),
                pending.size(),
                sum(pending, EntryResponse::amount),
                monthly.get(current).amount(),
                SavingsInterestCalculator.rateOn(today, rates),
                !rates.isEmpty(),
                rows,
                List.copyOf(monthly.values()));
    }

    // ------------------------------------------------------------------ taux

    @Transactional(readOnly = true)
    public List<RateResponse> rateHistory() {
        return rateRepository.findAllByOrderByEffectiveFromAsc().stream().map(RateResponse::from).toList();
    }

    public RateResponse setRate(RateRequest request) {
        if (rateRepository.existsByEffectiveFrom(request.effectiveFrom())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un taux est déjà défini à partir de cette date. Choisissez une autre date d'effet.");
        }
        SavingsInterestRate rate = rateRepository.save(new SavingsInterestRate(request.monthlyRate(),
                request.effectiveFrom(), blankToNull(request.note()), currentUserOrThrow()));
        auditLogService.record(AuditAction.SAVINGS_RATE_SET, "SavingsInterestRate", rate.getId(),
                "Taux d'épargne fixé à " + request.monthlyRate() + " % par mois à partir du " + request.effectiveFrom());
        return RateResponse.from(rate);
    }

    // ------------------------------------------------------------------ calculs

    private MemberSavingsResponse memberSavings(Member member) {
        LocalDate today = LocalDate.now(ZONE);
        List<Rate> rates = rates();
        List<EntryResponse> entries = new ArrayList<>();
        depositRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())
                .forEach(d -> entries.add(toEntry(d, today, rates)));
        transactionRepository.findByMemberIdAndContributionPeriod_ContributionDefinition_FundDestination(
                member.getId(), FundDestination.INDIVIDUAL_SAVINGS).forEach(t -> entries.add(toEntry(t, today, rates)));
        entries.sort(ENTRY_ORDER);

        List<EntryResponse> validated = entries.stream().filter(e -> "VALIDATED".equals(e.status())).toList();
        List<EntryResponse> pending = entries.stream().filter(e -> "PENDING".equals(e.status())).toList();
        BigDecimal principal = sum(validated, EntryResponse::amount);
        BigDecimal interest = rates.isEmpty() ? null : sum(validated, EntryResponse::interest);
        return new MemberSavingsResponse(member.getId(), member.getFullName(), principal, interest,
                interest == null ? principal : principal.add(interest), sum(pending, EntryResponse::amount),
                pending.size(), validated.size(), SavingsInterestCalculator.rateOn(today, rates), !rates.isEmpty(),
                entries);
    }

    private EntryResponse toEntry(SavingsDeposit d, LocalDate today, List<Rate> rates) {
        Accrual accrual = accrual(d.getStatus() == SavingsDepositStatus.VALIDATED, d.getAmount(), d.getValidatedAt(),
                today, rates);
        return new EntryResponse(d.getId(), EntrySource.DEPOSIT, d.getMember().getId(), d.getMember().getFullName(),
                "Versement d'épargne", d.getAmount(), d.getPaymentDate(), d.getOperator(), d.getReference(),
                d.getObservation(), d.getStatus().name(), d.getCreatedAt(), d.getValidatedAt(),
                d.getValidatedBy() != null ? d.getValidatedBy().getEmail() : null,
                accrual.completeMonths(), interestOrNull(d.getStatus() == SavingsDepositStatus.VALIDATED, accrual, rates),
                d.hasProof());
    }

    private EntryResponse toEntry(ContributionTransaction t, LocalDate today, List<Rate> rates) {
        Accrual accrual = accrual(t.getStatus() == ContributionTransactionStatus.VALIDATED, t.getAmount(),
                t.getValidatedAt(), today, rates);
        var period = t.getContributionPeriod();
        return new EntryResponse(t.getId(), EntrySource.CONTRIBUTION, t.getMember().getId(), t.getMember().getFullName(),
                period.getContributionDefinition().getName() + " – " + period.getSession().getLabel(),
                t.getAmount(), t.getPaymentDate(), t.getOperator(), t.getTransactionReference(), t.getObservation(),
                t.getStatus().name(), t.getCreatedAt(), t.getValidatedAt(),
                t.getValidatedBy() != null ? t.getValidatedBy().getEmail() : null,
                accrual.completeMonths(),
                interestOrNull(t.getStatus() == ContributionTransactionStatus.VALIDATED, accrual, rates), false);
    }

    /** Seul un versement validé produit des intérêts, à partir de sa date de validation. */
    private static Accrual accrual(boolean validated, BigDecimal amount, Instant validatedAt, LocalDate today,
                                   List<Rate> rates) {
        if (!validated || validatedAt == null) {
            return new Accrual(0, BigDecimal.ZERO);
        }
        return SavingsInterestCalculator.accrue(amount, validatedAt.atZone(ZONE).toLocalDate(), today, rates);
    }

    /** Intérêts affichés : seulement pour un versement validé, et si un taux a été fixé. */
    private static BigDecimal interestOrNull(boolean validated, Accrual accrual, List<Rate> rates) {
        return validated && !rates.isEmpty() ? accrual.interest() : null;
    }

    private List<Rate> rates() {
        return rateRepository.findAllByOrderByEffectiveFromAsc().stream()
                .map(r -> new Rate(r.getEffectiveFrom(), r.getMonthlyRate()))
                .toList();
    }

    private static final Comparator<EntryResponse> ENTRY_ORDER = Comparator
            .comparing(EntryResponse::paymentDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(EntryResponse::declaredAt, Comparator.nullsLast(Comparator.reverseOrder()));

    private static BigDecimal sum(List<EntryResponse> entries, Function<EntryResponse, BigDecimal> field) {
        return entries.stream().map(field).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    /** Comme pour les paiements (docs/decisions.md n° 23) : personne ne décide de son propre versement. */
    private SavingsDeposit getPendingForDecision(UUID depositId) {
        SavingsDeposit deposit = getDepositOrThrow(depositId);
        if (deposit.getStatus() != SavingsDepositStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce versement a déjà été traité");
        }
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId != null && currentMemberId.equals(deposit.getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous ne pouvez pas valider ou rejeter votre propre versement d'épargne");
        }
        return deposit;
    }

    private SavingsDeposit getDepositOrThrow(UUID depositId) {
        return depositRepository.findById(depositId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Versement introuvable"));
    }

    private Member currentMemberOrThrow() {
        UUID memberId = currentUserProvider.currentMemberId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun profil membre associé à ce compte"));
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun profil membre associé à ce compte"));
    }

    private User currentUserOrThrow() {
        return currentUserProvider.currentUserId()
                .flatMap(userRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
    }
}
