package cm.aekd.tontine.session;

import cm.aekd.tontine.member.Member;

import java.util.UUID;

/** Résumé d'un membre (id + nom), même forme que les participants/bénéficiaires de cotisation. */
public record SessionMemberResponse(UUID memberId, String memberFullName) {
    public static SessionMemberResponse from(Member member) {
        return new SessionMemberResponse(member.getId(), member.getFullName());
    }
}
