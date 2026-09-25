package cm.aekd.tontine.member;

import cm.aekd.tontine.common.CurrentUserProvider;
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

    public MemberService(MemberRepository memberRepository, CurrentUserProvider currentUserProvider) {
        this.memberRepository = memberRepository;
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
        if (request.phone() != null) {
            member.setPhone(request.phone());
        }
        if (request.active() != null) {
            member.setActive(request.active());
        }
        return MemberResponse.from(memberRepository.save(member));
    }

    private Member getOrThrow(UUID id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
    }
}
