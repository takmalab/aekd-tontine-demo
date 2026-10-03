package cm.aekd.tontine.dashboard;

import java.util.List;

public record MembersOverviewResponse(
        SessionSummaryResponse currentSession,
        List<MemberSummaryResponse> members
) {
}
