package cm.aekd.tontine.document;

import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Règles d'accès communes aux documents et nom de l'auteur affiché en pied de page.
 * Un membre n'accède qu'à ses propres documents ; ADMIN et TRESORIER à tous.
 * Un document inaccessible est traité comme inexistant (404), comme ailleurs dans l'API.
 */
@Component
public class DocumentAccess {

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;

    public DocumentAccess(CurrentUserProvider currentUserProvider, UserRepository userRepository,
                          MemberRepository memberRepository) {
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    public boolean isStaff() {
        return currentUserProvider.hasAnyRole("ADMIN", "TRESORIER");
    }

    /** Vérifie que l'utilisateur courant est du personnel ou le membre concerné. */
    public void checkStaffOrMember(UUID memberId, String notFoundMessage) {
        if (isStaff()) {
            return;
        }
        UUID current = currentUserProvider.currentMemberId().orElse(null);
        if (current == null || !current.equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage);
        }
    }

    public UUID currentMemberIdOrThrow() {
        return currentUserProvider.currentMemberId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun profil membre associé à ce compte"));
    }

    /** Nom complet du membre connecté, sinon son adresse e-mail. */
    public String currentAuthorName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .flatMap(user -> memberRepository.findByUserId(user.getId()))
                .map(Member::getFullName)
                .orElse(email);
    }

    /** Nom affiché pour un compte utilisateur (membre associé, sinon e-mail). */
    public String displayName(User user) {
        if (user == null) {
            return null;
        }
        return memberRepository.findByUserId(user.getId())
                .map(Member::getFullName)
                .orElse(user.getEmail());
    }
}
