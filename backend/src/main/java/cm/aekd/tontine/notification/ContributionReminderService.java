package cm.aekd.tontine.notification;

import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionParticipant;
import cm.aekd.tontine.contribution.ContributionParticipantRepository;
import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionPeriodRepository;
import cm.aekd.tontine.contribution.ContributionPeriodStatus;
import cm.aekd.tontine.contribution.ContributionPeriodStatusService;
import cm.aekd.tontine.contribution.ContributionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

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

    public ContributionReminderService(NotificationSettingsRepository settingsRepository,
                                       ContributionPeriodRepository periodRepository,
                                       ContributionParticipantRepository participantRepository,
                                       ContributionPeriodStatusService periodStatusService,
                                       NotificationService notificationService,
                                       JdbcTemplate jdbcTemplate) {
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

    /** Enregistre le rappel ; false s'il avait déjà été envoyé. */
    private boolean markSent(ContributionPeriod period, ContributionParticipant participant) {
        return jdbcTemplate.update("""
                INSERT INTO contribution_reminder (contribution_period_id, member_id, sent_at)
                VALUES (?, ?, now()) ON CONFLICT DO NOTHING""",
                period.getId(), participant.getMember().getId()) == 1;
    }
}
