package cm.aekd.tontine.reset;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.storage.FileStorageService;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Réinitialisation des données par le super administrateur (docs/decisions.md n° 63).
 *
 * <p>Suppression définitive, en une seule transaction, des catégories choisies et de
 * celles qui en dépendent ({@link DataResetCategory#withDependencies}). Garde-fous : mot
 * de confirmation et mot de passe du super administrateur. Jamais effacés : les super
 * administrateurs et le compte qui réinitialise, les rôles, les paramètres du système et
 * le journal d'audit (où la réinitialisation est inscrite). Les fichiers justificatifs et
 * photos qui ne sont plus référencés sont supprimés après validation de la transaction.
 * La date de réinitialisation empêche les données de démonstration de revenir.
 */
@Service
@Transactional
public class DataResetService {

    public static final String CONFIRMATION_WORD = "REINITIALISER";

    /** Comptes conservés : super administrateurs et compte courant (paramètre 1). */
    private static final String DELETABLE_USERS = """
            FROM users u WHERE u.id <> ? AND NOT EXISTS (
                SELECT 1 FROM user_roles ur JOIN role r ON r.id = ur.role_id
                WHERE ur.user_id = u.id AND r.name = 'SUPER_ADMIN')
            """;

    /** Toutes les clés de fichiers stockés référencées en base (hors logo de l'association). */
    private static final String STORED_FILE_KEYS = """
            SELECT storage_key FROM payment_proof
            UNION SELECT proof_storage_key FROM loan_repayment WHERE proof_storage_key IS NOT NULL
            UNION SELECT proof_storage_key FROM savings_deposit WHERE proof_storage_key IS NOT NULL
            UNION SELECT proof_storage_key FROM sanction_payment WHERE proof_storage_key IS NOT NULL
            UNION SELECT photo_storage_key FROM users WHERE photo_storage_key IS NOT NULL
            """;

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    public DataResetService(JdbcTemplate jdbc, EntityManager entityManager, CurrentUserProvider currentUserProvider,
                            UserRepository userRepository, PasswordEncoder passwordEncoder,
                            FileStorageService fileStorageService, AuditLogService auditLogService) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.auditLogService = auditLogService;
    }

    /** Ce que contient chaque catégorie aujourd'hui, pour l'écran de confirmation. */
    @Transactional(readOnly = true)
    public List<DataResetDtos.CategoryPreview> preview() {
        UUID currentUserId = currentUserId();
        List<DataResetDtos.CategoryPreview> result = new ArrayList<>();
        for (DataResetCategory category : DataResetCategory.values()) {
            result.add(new DataResetDtos.CategoryPreview(category, category.label(),
                    category.implies().stream().sorted().toList(), contents(category, currentUserId)));
        }
        return result;
    }

    public DataResetDtos.ResetResult reset(DataResetDtos.ResetRequest request) {
        if (!CONFIRMATION_WORD.equals(normalize(request.confirmation()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tapez " + CONFIRMATION_WORD + " pour confirmer la réinitialisation.");
        }
        User current = userRepository.findById(currentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Compte introuvable"));
        if (request.password() == null || !passwordEncoder.matches(request.password(), current.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Mot de passe incorrect.");
        }
        if (request.categories() == null || request.categories().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez au moins une catégorie.");
        }

        Set<DataResetCategory> categories = DataResetCategory.withDependencies(EnumSet.copyOf(request.categories()));
        Map<String, Long> before = new LinkedHashMap<>();
        for (DataResetCategory category : categories) {
            contents(category, current.getId()).forEach(item -> before.merge(item.label(), item.count(), Long::sum));
        }
        Set<String> filesBefore = new HashSet<>(jdbc.queryForList(STORED_FILE_KEYS, String.class));

        // Suppression en base (ordre de l'énumération = dépendances d'abord) ; contexte JPA
        // vidé pour ne garder aucune entité supprimée en mémoire.
        entityManager.flush();
        for (DataResetCategory category : DataResetCategory.values()) {
            if (!categories.contains(category)) {
                continue;
            }
            if (category == DataResetCategory.MEMBERS) {
                jdbc.update("DELETE " + DELETABLE_USERS, current.getId());
            } else {
                category.statements().forEach(jdbc::update);
            }
        }
        jdbc.update("UPDATE system_settings SET data_reset_at = now() WHERE id = 1");
        entityManager.clear();

        Set<String> orphanFiles = new HashSet<>(filesBefore);
        orphanFiles.removeAll(jdbc.queryForList(STORED_FILE_KEYS, String.class));
        deleteFilesAfterCommit(orphanFiles);

        String summary = before.entrySet().stream()
                .map(e -> e.getValue() + " " + e.getKey().toLowerCase())
                .collect(Collectors.joining(", "));
        auditLogService.record(AuditAction.DATA_RESET, "DataReset", current.getId(),
                "Réinitialisation des données (" + categories.stream().map(DataResetCategory::label)
                        .collect(Collectors.joining(" ; ")) + ") : " + summary + ".");

        return new DataResetDtos.ResetResult(categories.stream().sorted().toList(),
                before.entrySet().stream().map(e -> new DataResetDtos.Item(e.getKey(), e.getValue())).toList(),
                orphanFiles.size());
    }

    /** Éléments principaux d'une catégorie, avec leur nombre actuel. */
    private List<DataResetDtos.Item> contents(DataResetCategory category, UUID currentUserId) {
        return switch (category) {
            case NOTIFICATIONS -> List.of(item("Notifications", "notification"),
                    item("Rappels de paiement envoyés", "contribution_reminder"));
            case TOURNAMENTS -> List.of(item("Tournois", "tournament"));
            case PAYOUTS -> List.of(item("Remises de cagnotte", "session_payout"));
            case ACCOUNT_OPERATIONS -> List.of(item("Dépenses et virements", "account_operation"));
            case SANCTIONS -> List.of(item("Règles de sanction", "sanction_rule"),
                    item("Sanctions appliquées", "applied_sanction"),
                    item("Paiements de sanctions", "sanction_payment"));
            case LOANS -> List.of(item("Prêts", "loan"), item("Remboursements", "loan_repayment"));
            case LOAN_POLICIES -> List.of(item("Politiques de prêt", "loan_policy"));
            case SAVINGS -> List.of(item("Versements d'épargne", "savings_deposit"),
                    item("Taux d'intérêt de l'épargne", "savings_interest_rate"));
            case CONTRIBUTIONS -> List.of(item("Cotisations", "contribution_definition"),
                    item("Participations", "contribution_participant"),
                    item("Paiements de cotisations", "contribution_transaction"));
            case SESSIONS -> List.of(item("Séances", "session"));
            case MEMBERS -> List.of(new DataResetDtos.Item("Comptes",
                    count("SELECT count(*) " + DELETABLE_USERS, currentUserId)));
        };
    }

    private DataResetDtos.Item item(String label, String table) {
        // Noms de tables fixes, définis ci-dessus : pas d'entrée utilisateur dans la requête.
        return new DataResetDtos.Item(label, count("SELECT count(*) FROM " + table));
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private UUID currentUserId() {
        return currentUserProvider.currentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Compte introuvable"));
    }

    private static String normalize(String word) {
        if (word == null) {
            return "";
        }
        String plain = java.text.Normalizer.normalize(word.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return plain.toUpperCase(java.util.Locale.ROOT);
    }

    private void deleteFilesAfterCommit(Set<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String key : keys) {
                    try {
                        fileStorageService.delete(key);
                    } catch (IOException ignored) {
                        // Best-effort : un fichier orphelin n'est pas bloquant.
                    }
                }
            }
        });
    }
}
