package cm.aekd.tontine.member;

/**
 * Champs facultatifs : seuls ceux fournis (non nuls) sont appliqués.
 */
public record MemberUpdateRequest(
        String fullName,
        String phone,
        Boolean active
) {
}
