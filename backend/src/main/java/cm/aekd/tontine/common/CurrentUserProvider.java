package cm.aekd.tontine.common;

import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Résout l'utilisateur courant à partir du contexte de sécurité
 * (JwtAuthenticationFilter y place l'email comme principal). Utilisé
 * par les services pour appliquer les règles de visibilité par rôle
 * (CLAUDE.md §6, §11).
 */
@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;

    public CurrentUserProvider(UserRepository userRepository, MemberRepository memberRepository) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    public boolean hasAnyRole(String... roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        Set<String> wanted = Set.of(roles).stream()
                .map(role -> "ROLE_" + role)
                .collect(Collectors.toSet());
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> wanted.contains(authority.getAuthority()));
    }

    public Optional<UUID> currentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(User::getId)
                .flatMap(memberRepository::findByUserId)
                .map(Member::getId);
    }

    public Optional<UUID> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        return userRepository.findByEmail(authentication.getName()).map(User::getId);
    }
}
