package cm.aekd.tontine.contribution;

import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Revue des paiements déclarés (CLAUDE.md §17). La validation/le rejet
 * est réservé au trésorier (CLAUDE.md §6 : l'administrateur ne peut que
 * "consulter les paiements", pas les valider). docs/decisions.md §23
 * (DÉCIDÉ) : personne ne peut valider ou rejeter son propre paiement,
 * même un trésorier participant à la cotisation concernée.
 */
@Service
@Transactional
public class ContributionTransactionService {

    private final ContributionTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public ContributionTransactionService(ContributionTransactionRepository transactionRepository,
                                           UserRepository userRepository,
                                           CurrentUserProvider currentUserProvider) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public List<ContributionTransactionResponse> findPending() {
        return transactionRepository.findByStatus(ContributionTransactionStatus.PENDING).stream()
                .map(ContributionTransactionResponse::from)
                .toList();
    }

    public List<ContributionTransactionResponse> findMine() {
        UUID memberId = currentUserProvider.currentMemberId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun profil membre associé à ce compte"));
        return transactionRepository.findByMemberId(memberId).stream()
                .map(ContributionTransactionResponse::from)
                .toList();
    }

    public ContributionTransactionResponse validate(UUID id) {
        return decide(id, ContributionTransactionStatus.VALIDATED);
    }

    public ContributionTransactionResponse reject(UUID id) {
        return decide(id, ContributionTransactionStatus.REJECTED);
    }

    private ContributionTransactionResponse decide(UUID id, ContributionTransactionStatus newStatus) {
        ContributionTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paiement introuvable"));

        if (transaction.getStatus() != ContributionTransactionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce paiement a déjà été traité");
        }

        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId != null && currentMemberId.equals(transaction.getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous ne pouvez pas valider ou rejeter votre propre paiement");
        }

        User validator = currentUserProvider.currentUserId()
                .flatMap(userRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));

        transaction.setStatus(newStatus);
        transaction.setValidatedBy(validator);
        transaction.setValidatedAt(Instant.now());

        return ContributionTransactionResponse.from(transactionRepository.save(transaction));
    }
}
