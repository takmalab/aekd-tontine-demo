package cm.aekd.tontine.user;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.storage.FileStorageService;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Suppression définitive d'un compte par l'ADMIN (docs/decisions.md n° 41).
 *
 * Décision du propriétaire du projet : le compte, son profil membre et TOUT son
 * historique (paiements, justificatifs, prêts, remboursements, sanctions,
 * épargne, participations) sont effacés (ON DELETE CASCADE). Les données des
 * AUTRES membres sont conservées : là où le compte apparaît comme auteur
 * (validation, enregistrement, sanction appliquée…), la référence passe à NULL
 * (migration V15) et l'interface affiche « utilisateur supprimé ».
 *
 * Garde-fous techniques : on ne supprime ni son propre compte, ni le dernier ADMIN
 * (l'application deviendrait inadministrable).
 */
@Service
@Transactional
public class UserDeletionService {

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    public UserDeletionService(UserRepository userRepository, CurrentUserProvider currentUserProvider,
                               JdbcTemplate jdbc, EntityManager entityManager,
                               FileStorageService fileStorageService, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.fileStorageService = fileStorageService;
        this.auditLogService = auditLogService;
    }

    /** Ce qui sera effacé, pour la confirmation affichée à l'ADMIN. */
    public record DeletionImpact(
            UUID userId,
            String email,
            String memberFullName,
            long contributionPayments,
            long participations,
            long loans,
            long loanRepayments,
            long savingsDeposits,
            long appliedSanctions,
            long sessionPayouts,
            long actionsAsAuthor
    ) {
    }

    @Transactional(readOnly = true)
    public DeletionImpact impact(UUID userId) {
        User user = userOrThrow(userId);
        UUID memberId = memberIdOf(userId);
        return new DeletionImpact(
                user.getId(),
                user.getEmail(),
                memberId == null ? null : jdbc.queryForObject(
                        "SELECT full_name FROM member WHERE id = ?", String.class, memberId),
                countByMember("SELECT count(*) FROM contribution_transaction WHERE member_id = ?", memberId),
                countByMember("SELECT count(*) FROM contribution_participant WHERE member_id = ?", memberId),
                countByMember("SELECT count(*) FROM loan WHERE member_id = ?", memberId),
                countByMember("SELECT count(*) FROM loan_repayment r JOIN loan l ON l.id = r.loan_id "
                        + "WHERE l.member_id = ?", memberId),
                countByMember("SELECT count(*) FROM savings_deposit WHERE member_id = ?", memberId),
                countByMember("SELECT count(*) FROM applied_sanction WHERE member_id = ?", memberId),
                countByMember("SELECT count(*) FROM session_payout WHERE member_id = ?", memberId),
                count("""
                        SELECT (SELECT count(*) FROM contribution_transaction WHERE validated_by = ?)
                             + (SELECT count(*) FROM loan WHERE decision_by = ?)
                             + (SELECT count(*) FROM loan_repayment WHERE recorded_by = ? OR validated_by = ?)
                             + (SELECT count(*) FROM applied_sanction WHERE applied_by = ?)
                             + (SELECT count(*) FROM savings_deposit WHERE validated_by = ?)
                             + (SELECT count(*) FROM savings_interest_rate WHERE created_by = ?)
                             + (SELECT count(*) FROM session_payout WHERE recorded_by = ?)
                        """, userId, userId, userId, userId, userId, userId, userId, userId));
    }

    public void delete(UUID userId) {
        User user = userOrThrow(userId);
        UUID currentUserId = currentUserProvider.currentUserId().orElse(null);
        if (userId.equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vous ne pouvez pas supprimer votre propre compte.");
        }
        if (hasRole(userId, "ADMIN") && count("""
                SELECT count(*) FROM user_roles ur JOIN role r ON r.id = ur.role_id WHERE r.name = 'ADMIN'
                """) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer le dernier administrateur : l'application deviendrait inadministrable.");
        }

        DeletionImpact impact = impact(userId);
        List<String> proofKeys = proofStorageKeys(memberIdOf(userId));

        // Suppression en base (cascades et SET NULL) ; contexte JPA vidé pour ne
        // garder aucune entité supprimée en mémoire.
        entityManager.flush();
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        entityManager.clear();

        auditLogService.record(AuditAction.USER_DELETED, "User", userId, describe(impact));

        // Fichiers justificatifs : effacés seulement si la transaction est validée.
        if (!proofKeys.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String key : proofKeys) {
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

    private static String describe(DeletionImpact impact) {
        return "Suppression du compte " + impact.email()
                + (impact.memberFullName() != null ? " (" + impact.memberFullName() + ")" : "")
                + " et de son historique : " + impact.contributionPayments() + " paiement(s), "
                + impact.loans() + " prêt(s), " + impact.loanRepayments() + " remboursement(s), "
                + impact.savingsDeposits() + " versement(s) d'épargne, " + impact.appliedSanctions()
                + " sanction(s), " + impact.sessionPayouts() + " remise(s) de cagnotte reçue(s). Auteur retiré de " + impact.actionsAsAuthor() + " opération(s) d'autres membres.";
    }

    private List<String> proofStorageKeys(UUID memberId) {
        if (memberId == null) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        keys.addAll(jdbc.queryForList("""
                SELECT p.storage_key FROM payment_proof p
                JOIN contribution_transaction t ON t.id = p.contribution_transaction_id
                WHERE t.member_id = ?
                """, String.class, memberId));
        keys.addAll(jdbc.queryForList("""
                SELECT r.proof_storage_key FROM loan_repayment r JOIN loan l ON l.id = r.loan_id
                WHERE l.member_id = ? AND r.proof_storage_key IS NOT NULL
                """, String.class, memberId));
        keys.addAll(jdbc.queryForList("""
                SELECT proof_storage_key FROM savings_deposit
                WHERE member_id = ? AND proof_storage_key IS NOT NULL
                """, String.class, memberId));
        keys.addAll(jdbc.queryForList("""
                SELECT proof_storage_key FROM sanction_payment
                WHERE member_id = ? AND proof_storage_key IS NOT NULL
                """, String.class, memberId));
        return keys;
    }

    private boolean hasRole(UUID userId, String role) {
        return count("SELECT count(*) FROM user_roles ur JOIN role r ON r.id = ur.role_id "
                + "WHERE ur.user_id = ? AND r.name = ?", userId, role) > 0;
    }

    private UUID memberIdOf(UUID userId) {
        List<UUID> ids = jdbc.queryForList("SELECT id FROM member WHERE user_id = ?", UUID.class, userId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private long countByMember(String sql, UUID memberId) {
        return memberId == null ? 0 : count(sql, memberId);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private User userOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
    }
}
