package cm.aekd.tontine.member;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public List<MemberResponse> findAll() {
        return memberService.findAll();
    }

    @GetMapping("/mine")
    public MemberResponse mine() {
        return memberService.findMine();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public MemberResponse findById(@PathVariable UUID id) {
        return memberService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MemberResponse update(@PathVariable UUID id, @Valid @RequestBody MemberUpdateRequest request) {
        return memberService.update(id, request);
    }
}
