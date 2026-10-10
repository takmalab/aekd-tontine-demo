package cm.aekd.tontine.tournament;

import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionStatus;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record TournamentResponse(
        UUID id,
        String name,
        TournamentStatus status,
        YearMonth startMonth,
        int weekOfMonth,
        DayOfWeek dayOfWeek,
        int leadDays,
        List<Slot> slots,
        List<Contribution> contributions,
        String createdByEmail,
        Instant createdAt
) {

    /**
     * Un passage de l'ordre. {@code sessionId} est null tant que la séance n'est
     * pas générée, ou si elle a été supprimée après génération ({@code generated}
     * reste alors vrai).
     */
    public record Slot(
            int position,
            UUID memberId,
            String memberFullName,
            LocalDate plannedDate,
            boolean generated,
            UUID sessionId,
            String sessionLabel
    ) {
    }

    public record Contribution(UUID id, String name, ContributionStatus status) {
    }

    public static TournamentResponse from(Tournament tournament) {
        return new TournamentResponse(
                tournament.getId(),
                tournament.getName(),
                tournament.getStatus(),
                tournament.getStartMonth(),
                tournament.getWeekOfMonth(),
                tournament.getDayOfWeek(),
                tournament.getLeadDays(),
                tournament.getSlots().stream()
                        .sorted(Comparator.comparingInt(TournamentSlot::getPosition))
                        .map(slot -> new Slot(
                                slot.getPosition(),
                                slot.getMember().getId(),
                                slot.getMember().getFullName(),
                                tournament.plannedDate(slot),
                                slot.isGenerated(),
                                slot.getSession() != null ? slot.getSession().getId() : null,
                                slot.getSession() != null ? slot.getSession().getLabel() : null))
                        .toList(),
                tournament.getContributions().stream()
                        .sorted(Comparator.comparing(ContributionDefinition::getName, String.CASE_INSENSITIVE_ORDER))
                        .map(c -> new Contribution(c.getId(), c.getName(), c.getStatus()))
                        .toList(),
                tournament.getCreatedBy() != null ? tournament.getCreatedBy().getEmail() : null,
                tournament.getCreatedAt()
        );
    }
}
