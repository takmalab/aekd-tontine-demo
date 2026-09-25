package cm.aekd.tontine.user;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.security.RoleName;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Gestion des comptes utilisateurs (CLAUDE.md §3, §6 : "gérer les
 * utilisateurs" est une permission ADMIN uniquement). La création
 * directe par un ADMIN peut inclure un profil Member lié en une seule
 * requête.
 *
 * L'auto-inscription (decision validée avec l'utilisateur : un futur
 * membre peut créer lui-même son compte, mais celui-ci reste inutilisable
 * tant qu'un administrateur ne l'a pas validé) crée toujours un compte
 * MEMBRE avec un profil Member, jamais un rôle ADMIN/TRESORIER — seul un
 * administrateur peut attribuer ces rôles via {@link #update}.
 * La date d'adhésion d'un compte auto-inscrit est fixée à la date
 * d'inscription (pas saisie par le membre), pour ne pas permettre de
 * déclarer une ancienneté fictive utilisée ensuite par les règles de
 * politique de prêt (§20).
 */
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                        MemberRepository memberRepository, PasswordEncoder passwordEncoder,
                        AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public UserResponse create(CreateUserRequest request) {
        User user = createInternal(request.email(), request.password(), request.roles(),
                request.member() != null ? request.member().fullName() : null,
                request.member() != null ? request.member().phone() : null,
                request.member() != null ? request.member().joinDate() : null,
                UserStatus.ACTIVE);

        auditLogService.record(AuditAction.USER_CREATED, "User", user.getId(),
                "Création du compte " + user.getEmail() + " par un administrateur");

        return toResponse(user, memberRepository.findByUserId(user.getId()).orElse(null));
    }

    /**
     * Auto-inscription (public, sans authentification). Le compte est
     * créé en PENDING_VALIDATION : {@link cm.aekd.tontine.auth.AuthService#login}
     * refuse la connexion tant qu'un administrateur ne l'a pas approuvé.
     */
    public RegisterResponse registerSelf(String email, String password, String fullName, String phone) {
        User user = createInternal(email, password, List.of(RoleName.MEMBRE), fullName, phone,
                LocalDate.now(), UserStatus.PENDING_VALIDATION);

        auditLogService.record(AuditAction.USER_REGISTERED, "User", user.getId(),
                "Auto-inscription de " + user.getEmail() + " (en attente de validation)");

        return new RegisterResponse(user.getEmail(),
                "Compte créé avec succès. Il doit être validé par un administrateur avant de pouvoir vous connecter.");
    }

    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
                .map(user -> toResponse(user, memberRepository.findByUserId(user.getId()).orElse(null)))
                .toList();
    }

    public List<UserResponse> findPending() {
        return userRepository.findByStatus(UserStatus.PENDING_VALIDATION).stream()
                .map(user -> toResponse(user, memberRepository.findByUserId(user.getId()).orElse(null)))
                .toList();
    }

    public UserResponse findById(UUID id) {
        User user = getOrThrow(id);
        return toResponse(user, memberRepository.findByUserId(id).orElse(null));
    }

    public UserResponse approve(UUID id) {
        User user = getOrThrow(id);
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);

        auditLogService.record(AuditAction.USER_APPROVED, "User", user.getId(),
                "Validation du compte " + user.getEmail());

        return toResponse(user, memberRepository.findByUserId(id).orElse(null));
    }

    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = getOrThrow(id);

        if (request.status() != null) {
            user.setStatus(request.status());
        }

        if (request.roles() != null) {
            if (request.roles().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Un utilisateur doit avoir au moins un rôle");
            }
            Set<Role> newRoles = resolveRoles(request.roles());
            new HashSet<>(user.getRoles()).forEach(user::removeRole);
            newRoles.forEach(user::addRole);
        }

        user = userRepository.save(user);

        auditLogService.record(AuditAction.USER_UPDATED, "User", user.getId(),
                "Mise à jour du compte " + user.getEmail());

        return toResponse(user, memberRepository.findByUserId(id).orElse(null));
    }

    private User createInternal(String email, String password, List<RoleName> roleNames, String fullName,
                                 String phone, LocalDate joinDate, UserStatus status) {
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà avec cet email");
        }

        User user = new User(email, passwordEncoder.encode(password));
        resolveRoles(roleNames).forEach(user::addRole);
        user.setStatus(status);
        user = userRepository.save(user);

        if (fullName != null) {
            Member member = new Member(user, fullName, joinDate);
            member.setPhone(phone);
            memberRepository.save(member);
        }

        return user;
    }

    private Set<Role> resolveRoles(List<RoleName> names) {
        Set<Role> roles = new HashSet<>();
        for (RoleName name : names) {
            roles.add(roleRepository.findByName(name)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Rôle introuvable : " + name)));
        }
        return roles;
    }

    private User getOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
    }

    private UserResponse toResponse(User user, Member member) {
        List<RoleName> roleNames = user.getRoles().stream().map(Role::getName).toList();
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getStatus(),
                roleNames,
                member != null ? member.getId() : null,
                member != null ? member.getFullName() : null,
                user.getCreatedAt()
        );
    }
}
