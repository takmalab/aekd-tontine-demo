package cm.aekd.tontine.dashboard;

import cm.aekd.tontine.session.Session;

import java.time.LocalDate;
import java.util.UUID;

public record SessionSummaryResponse(UUID id, String label, LocalDate date, String location) {
    public static SessionSummaryResponse from(Session session) {
        return new SessionSummaryResponse(session.getId(), session.getLabel(), session.getSessionDate(),
                session.getLocation());
    }
}
