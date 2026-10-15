package cm.aekd.tontine.user;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.PhoneNumbers;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.notification.NotificationService;
import cm.aekd.tontine.notification.NotificationType;
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
    private final NotificationService notificationService;
    private final cm.aekd.tontine.common.CurrentUserProvider currentUserProvider;

    /** Rôles que seul un super administrateur attribue ou retire (decisions.md n° 56). */
    private static final Set<RoleName> PRIVILEGED = Set.of(RoleName.SUPER_ADMIN, RoleName.ADMIN);

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                        MemberRepository memberRepository, PasswordEncoder passwordEncoder,
                        AuditLogService auditLogService, NotificationService notificationService,
                        cm.aekd.tontine.common.CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public UserResponse create(CreateUserRequest request) {
        List<RoleName> roles = withImpliedRoles(request.roles());
        if (roles.stream().anyMatch(PRIVILEGED::contains)) {
            requireSuperAdmin("créer un compte administrateur");
        }
        User user = createInternal(request.email(), request.password(), roles,
                request.member() != null ? request.member().fullName() : null,
                request.phone() != null ? request.phone()
                        : request.member() != null ? request.member().phone() : null,
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
        notificationService.notifyAdmins(NotificationType.USER_REGISTERED, "Inscription à valider",
                fullName + " (" + user.getEmail() + ") a créé un compte et attend votre validation.", "/users");

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
        Set<RoleName> before = roleNamesOf(user);

        // Un administrateur ou super administrateur n'est modifié que par un super administrateur.
        if (before.stream().anyMatch(PRIVILEGED::contains)) {
            requireSuperAdmin("modifier un compte administrateur");
        }

        if (request.status() != null) {
            user.setStatus(request.status());
        }

        if (request.phone() != null) {
            String phone = PhoneNumbers.normalizeOrNull(request.phone());
            checkPhoneAvailable(phone, user);
            user.setPhone(phone);
            memberRepository.findByUserId(id).ifPresent(member -> member.setPhone(phone));
        }

        if (request.roles() != null) {
            if (request.roles().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Un utilisateur doit avoir au moins un rôle");
            }
            List<RoleName> wanted = withImpliedRoles(request.roles());
            if (wanted.stream().anyMatch(PRIVILEGED::contains)) {
                requireSuperAdmin("nommer un administrateur");
            }
            if (before.contains(RoleName.SUPER_ADMIN) && !wanted.contains(RoleName.SUPER_ADMIN)) {
                checkNotLastSuperAdmin(user);
            }
            Set<Role> newRoles = resolveRoles(wanted);
            new HashSet<>(user.getRoles()).forEach(user::removeRole);
            newRoles.forEach(user::addRole);
        }
        if (request.status() != null && request.status() != UserStatus.ACTIVE && before.contains(RoleName.SUPER_ADMIN)) {
            checkNotLastSuperAdmin(user);
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

        String normalizedPhone = PhoneNumbers.normalizeOrNull(phone);
        checkPhoneAvailable(normalizedPhone, null);

        User user = new User(email, passwordEncoder.encode(password));
        user.setPhone(normalizedPhone);
        resolveRoles(roleNames).forEach(user::addRole);
        user.setStatus(status);
        user = userRepository.save(user);

        if (fullName != null) {
            Member member = new Member(user, fullName, joinDate);
            member.setPhone(normalizedPhone);
            memberRepository.save(member);
        }

        return user;
    }

    /**
     * Le numéro sert à se connecter par OTP : il ne peut appartenir qu'à un
     * seul compte (decisions.md n° 47).
     */
    private void checkPhoneAvailable(String phone, User owner) {
        if (phone == null) {
            return;
        }
        userRepository.findByPhone(phone)
                .filter(existing -> !existing.equals(owner))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Ce numéro de téléphone est déjà utilisé par un autre compte");
                });
    }

    /** Un super administrateur garde toujours aussi le rôle ADMIN (decisions.md n° 56). */
    private static List<RoleName> withImpliedRoles(List<RoleName> roles) {
        if (roles == null || !roles.contains(RoleName.SUPER_ADMIN) || roles.contains(RoleName.ADMIN)) {
            return roles;
        }
        List<RoleName> all = new java.util.ArrayList<>(roles);
        all.add(RoleName.ADMIN);
        return all;
    }

    private static Set<RoleName> roleNamesOf(User user) {
        Set<RoleName> names = new HashSet<>();
        user.getRoles().forEach(role -> names.add(role.getName()));
        return names;
    }

    private void requireSuperAdmin(String action) {
        if (!currentUserProvider.hasAnyRole(RoleName.SUPER_ADMIN.name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seul un super administrateur peut " + action);
        }
    }

    /** L'application doit garder au moins un super administrateur actif. */
    private void checkNotLastSuperAdmin(User user) {
        long others = userRepository.findByRoleNamesAndStatus(Set.of(RoleName.SUPER_ADMIN), UserStatus.ACTIVE).stream()
                .filter(other -> !other.getId().equals(user.getId()))
                .count();
        if (others == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible : c'est le dernier super administrateur actif. Nommez-en d'abord un autre.");
        }
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
                user.getPhone(),
                user.getStatus(),
                roleNames,
                member != null ? member.getId() : null,
                member != null ? member.getFullName() : null,
                user.getCreatedAt(),
                user.isEmailVerified()
        );
    }
}
