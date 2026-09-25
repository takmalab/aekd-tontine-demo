package cm.aekd.tontine.session;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * hostMember est null et beneficiaries vide pour les séances créées avant
 * la migration V12 (ces informations n'existaient pas).
 */
public record SessionResponse(
        UUID id,
        String label,
        LocalDate date,
        String location,
        SessionMemberResponse hostMember,
        List<SessionBeneficiaryResponse> beneficiaries
) {
    public static SessionResponse from(Session session, List<SessionBeneficiary> beneficiaries) {
        return new SessionResponse(
                session.getId(),
                session.getLabel(),
                session.getSessionDate(),
                session.getLocation(),
                session.getHostMember() != null ? SessionMemberResponse.from(session.getHostMember()) : null,
                beneficiaries.stream().map(SessionBeneficiaryResponse::from).toList()
        );
    }
}
