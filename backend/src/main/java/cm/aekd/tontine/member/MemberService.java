package cm.aekd.tontine.member;

import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.common.PhoneNumbers;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * CLAUDE.md §6 : "gérer les membres" (modification) est réservé à
 * l'ADMIN. La consultation (liste/détail) est ouverte à ADMIN/TRESORIER,
 * qui en ont besoin au quotidien (sélection de participants/bénéficiaires,
 * application de sanctions...).
 */
@Service
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    public MemberService(MemberRepository memberRepository, CurrentUserProvider currentUserProvider,
                         UserRepository userRepository) {
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public List<MemberResponse> findAll() {
        return memberRepository.findAll().stream()
                .map(MemberResponse::from)
                .toList();
    }

    public MemberResponse findById(UUID id) {
        return MemberResponse.from(getOrThrow(id));
    }

    public MemberResponse findMine() {
        UUID memberId = currentUserProvider.currentMemberId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun profil membre associé à ce compte"));
        return MemberResponse.from(getOrThrow(memberId));
    }

    public MemberResponse update(UUID id, MemberUpdateRequest request) {
        Member member = getOrThrow(id);
        if (request.fullName() != null) {
            member.setFullName(request.fullName());
        }
        // Seul un numéro modifié est contrôlé : un ancien numéro saisi librement ne
        // bloque pas la modification des autres champs.
        if (request.phone() != null && !request.phone().equals(member.getPhone())) {
            updatePhone(member, PhoneNumbers.normalizeOrNull(request.phone()));
        }
        if (request.active() != null) {
            member.setActive(request.active());
        }
        return MemberResponse.from(memberRepository.save(member));
    }

    /**
     * Le téléphone de la fiche est aussi le numéro de connexion par OTP du
     * compte lié (decisions.md n° 47) : les deux restent identiques.
     */
    private void updatePhone(Member member, String phone) {
        User owner = member.getUser();
        if (phone != null) {
            userRepository.findByPhone(phone)
                    .filter(existing -> !existing.equals(owner))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Ce numéro de téléphone est déjà utilisé par un autre compte");
                    });
        }
        member.setPhone(phone);
        if (owner != null) {
            owner.setPhone(phone);
        }
    }

    private Member getOrThrow(UUID id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
    }
}
