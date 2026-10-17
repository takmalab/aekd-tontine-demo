package cm.aekd.tontine.notification;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionParticipant;
import cm.aekd.tontine.contribution.ContributionParticipantRepository;
import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionPeriodRepository;
import cm.aekd.tontine.contribution.ContributionPeriodStatus;
import cm.aekd.tontine.contribution.ContributionPeriodStatusService;
import cm.aekd.tontine.contribution.ContributionStatus;
import cm.aekd.tontine.document.DocumentFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * Rappels avant la date limite (docs/decisions.md n° 44) : pour chaque cotisation
 * OBLIGATOIRE et active d'une séance dont la date limite tombe dans les
 * {@code reminderDaysBefore} prochains jours, un rappel unique est envoyé aux
 * participants qui n'ont pas encore payé (NOT_PAID) ou n'ont payé qu'une partie
 * (PARTIAL). Un paiement en attente de validation ne déclenche pas de rappel.
 * Les cotisations facultatives ne sont jamais rappelées (CLAUDE.md §18).
 */
@Service
@Transactional
public class ContributionReminderService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<ContributionPeriodStatus> TO_REMIND =
            Set.of(ContributionPeriodStatus.NOT_PAID, ContributionPeriodStatus.PARTIAL);

    private final NotificationSettingsRepository settingsRepository;
    private final ContributionPeriodRepository periodRepository;
    private final ContributionParticipantRepository participantRepository;
    private final ContributionPeriodStatusService periodStatusService;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public ContributionReminderService(NotificationSettingsRepository settingsRepository,
                                       ContributionPeriodRepository periodRepository,
                                       ContributionParticipantRepository participantRepository,
                                       ContributionPeriodStatusService periodStatusService,
                                       NotificationService notificationService,
                                       JdbcTemplate jdbcTemplate,
                                       AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
        this.settingsRepository = settingsRepository;
        this.periodRepository = periodRepository;
        this.participantRepository = participantRepository;
        this.periodStatusService = periodStatusService;
        this.notificationService = notificationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @return nombre de rappels envoyés */
    public int sendDueReminders(LocalDate today) {
        Integer days = settingsRepository.getSettings().getReminderDaysBefore();
        if (days == null) {
            return 0;
        }
        int sent = 0;
        for (ContributionPeriod period : periodRepository.findByDueDateBetween(today, today.plusDays(days))) {
            ContributionDefinition definition = period.getContributionDefinition();
            if (!definition.isMandatory() || definition.getStatus() != ContributionStatus.ACTIVE) {
                continue;
            }
            for (ContributionParticipant participant : participantRepository.findForPeriod(period)) {
                if (!TO_REMIND.contains(periodStatusService.resolve(period, participant.getMember().getId()))
                        || !markSent(period, participant)) {
                    continue;
                }
                notificationService.notifyMember(participant.getMember(), NotificationType.CONTRIBUTION_REMINDER,
                        "Rappel : " + definition.getName(),
                        "À payer avant le " + period.getDueDate().format(DATE) + " pour la séance \""
                                + period.getSession().getLabel() + "\".",
                        "/my-contributions");
                sent++;
            }
        }
        return sent;
    }

    /**
     * Relance manuelle par le trésorier depuis la page d'une cotisation (test de bout en bout du
     * 2026-10-17, U2) : un participant qui n'a pas tout payé (en retard compris) reçoit un rappel,
     * avec le reste dû et l'échéance. Indépendante des rappels automatiques et tracée à l'audit.
     */
    public void remindNow(UUID contributionDefinitionId, UUID periodId, UUID memberId) {
        ContributionPeriod period = periodRepository.findById(periodId)
                .filter(p -> p.getContributionDefinition().getId().equals(contributionDefinitionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable pour cette cotisation"));
        ContributionParticipant participant = participantRepository
                .findByContributionDefinitionIdAndMemberId(contributionDefinitionId, memberId)
                .filter(p -> p.appliesTo(period))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ce membre ne participe pas à cette cotisation pour cette séance"));
        ContributionPeriodStatus status = periodStatusService.resolve(period, memberId);
        if (status == ContributionPeriodStatus.PAID || status == ContributionPeriodStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    status == ContributionPeriodStatus.PAID ? "Ce membre a déjà tout payé"
                            : "Un paiement de ce membre attend déjà la validation");
        }
        ContributionDefinition definition = period.getContributionDefinition();
        BigDecimal remaining = periodStatusService.remainingAmount(period, memberId);
        String due = period.getDueDate() != null
                ? (period.getDueDate().isBefore(LocalDate.now()) ? "échue depuis le " : "à payer avant le ")
                        + period.getDueDate().format(DATE)
                : "sans échéance";
        notificationService.notifyMember(participant.getMember(), NotificationType.CONTRIBUTION_REMINDER,
                "Rappel du trésorier : " + definition.getName(),
                (remaining != null ? "Reste à payer : " + DocumentFormat.fcfa(remaining) + ", " : "")
                        + due + " (séance \"" + period.getSession().getLabel() + "\").",
                "/my-contributions");
        auditLogService.record(AuditAction.CONTRIBUTION_REMINDER_SENT, "ContributionDefinition", definition.getId(),
                "Relance de " + participant.getMember().getFullName() + " pour \"" + definition.getName()
                        + "\" (" + period.getSession().getLabel() + ")");
    }

    /** Enregistre le rappel ; false s'il avait déjà été envoyé. */
    private boolean markSent(ContributionPeriod period, ContributionParticipant participant) {
        return jdbcTemplate.update("""
                INSERT INTO contribution_reminder (contribution_period_id, member_id, sent_at)
                VALUES (?, ?, now()) ON CONFLICT DO NOTHING""",
                period.getId(), participant.getMember().getId()) == 1;
    }
}
