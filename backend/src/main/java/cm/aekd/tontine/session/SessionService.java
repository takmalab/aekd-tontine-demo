package cm.aekd.tontine.session;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class SessionService {

    private static final DateTimeFormatter AUDIT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SessionRepository sessionRepository;
    private final SessionBeneficiaryRepository beneficiaryRepository;
    private final MemberRepository memberRepository;
    private final AuditLogService auditLogService;

    public SessionService(SessionRepository sessionRepository,
                          SessionBeneficiaryRepository beneficiaryRepository,
                          MemberRepository memberRepository,
                          AuditLogService auditLogService) {
        this.sessionRepository = sessionRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.memberRepository = memberRepository;
        this.auditLogService = auditLogService;
    }

    public SessionResponse create(SessionRequest request) {
        Member host = memberRepository.findById(request.hostMemberId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Membre récepteur introuvable"));

        // Un même membre n'est enregistré qu'une fois comme bénéficiaire de la séance.
        List<Member> beneficiaries = new LinkedHashSet<>(request.beneficiaryMemberIds()).stream()
                .map(memberId -> memberRepository.findById(memberId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Membre bénéficiaire introuvable")))
                .toList();

        Session session = sessionRepository.save(
                new Session(request.label().trim(), request.date(), request.location().trim(), host));

        List<SessionBeneficiary> savedBeneficiaries = beneficiaries.stream()
                .map(member -> beneficiaryRepository.save(new SessionBeneficiary(session, member)))
                .toList();

        auditLogService.record(AuditAction.SESSION_CREATED, "Session", session.getId(),
                "Création de la séance \"" + session.getLabel() + "\" du "
                        + session.getSessionDate().format(AUDIT_DATE) + " à " + session.getLocation()
                        + " (récepteur : " + host.getFullName() + ")");

        return SessionResponse.from(session, savedBeneficiaries);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> findAll() {
        List<Session> sessions = sessionRepository.findAllByOrderBySessionDateDesc();
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<SessionBeneficiary>> beneficiariesBySession = beneficiaryRepository
                .findBySessionIdInOrderByCreatedAtAsc(sessions.stream().map(Session::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(b -> b.getSession().getId()));

        return sessions.stream()
                .map(session -> SessionResponse.from(session,
                        beneficiariesBySession.getOrDefault(session.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionResponse findById(UUID id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        return SessionResponse.from(session, beneficiaryRepository.findBySessionIdOrderByCreatedAtAsc(id));
    }
}
