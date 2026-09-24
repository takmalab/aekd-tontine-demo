package cm.aekd.tontine.session;

import java.time.LocalDate;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String label,
        LocalDate startDate,
        LocalDate endDate
) {
    public static SessionResponse from(Session session) {
        return new SessionResponse(session.getId(), session.getLabel(), session.getStartDate(), session.getEndDate());
    }
}
