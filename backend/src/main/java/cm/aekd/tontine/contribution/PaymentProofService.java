package cm.aekd.tontine.contribution;

import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.storage.FileStorageService;
import cm.aekd.tontine.storage.ProofFiles;
import cm.aekd.tontine.storage.StoredFile;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Justificatifs de paiement (CLAUDE.md §16, §18). Un justificatif ne peut
 * être ajouté/remplacé que par le membre propriétaire du paiement, tant
 * que celui-ci est encore PENDING (une fois traité, le dossier est figé).
 * Consultation ouverte à ADMIN/TRESORIER et au membre concerné
 * (regles-metier.md §18 "consultable par les utilisateurs autorisés").
 */
@Service
@Transactional
public class PaymentProofService {

    private final ContributionTransactionRepository transactionRepository;
    private final PaymentProofRepository proofRepository;
    private final FileStorageService fileStorageService;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    public PaymentProofService(ContributionTransactionRepository transactionRepository,
                                PaymentProofRepository proofRepository,
                                FileStorageService fileStorageService,
                                CurrentUserProvider currentUserProvider,
                                UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.proofRepository = proofRepository;
        this.fileStorageService = fileStorageService;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
    }

    public PaymentProofResponse upload(UUID transactionId, MultipartFile file) {
        ContributionTransaction transaction = getOrThrow(transactionId);

        UUID currentMemberId = currentUserProvider.currentMemberId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun profil membre associé à ce compte"));
        if (!currentMemberId.equals(transaction.getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous ne pouvez transmettre un justificatif que pour votre propre paiement");
        }
        if (transaction.getStatus() != ContributionTransactionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce paiement a déjà été traité, le justificatif ne peut plus être modifié");
        }

        ProofFiles.validate(file);

        User uploader = currentUserProvider.currentUserId()
                .flatMap(userRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));

        proofRepository.findByContributionTransactionId(transactionId).ifPresent(existing -> {
            deleteQuietly(existing.getStorageKey());
            proofRepository.delete(existing);
            proofRepository.flush();
        });

        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = fileStorageService.store("payment-proofs/" + transactionId, file.getOriginalFilename(),
                    file.getContentType(), in);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Échec de l'enregistrement du justificatif");
        }

        PaymentProof proof = new PaymentProof(transaction, stored.storageKey(), stored.originalFilename(),
                stored.contentType(), stored.size(), uploader);
        return PaymentProofResponse.from(proofRepository.save(proof));
    }

    public StoredResource download(UUID transactionId) {
        ContributionTransaction transaction = getOrThrow(transactionId);
        checkCanView(transaction);

        PaymentProof proof = proofRepository.findByContributionTransactionId(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun justificatif pour ce paiement"));

        try {
            InputStream content = fileStorageService.retrieve(proof.getStorageKey());
            return new StoredResource(content, proof.getContentType(), proof.getOriginalFilename());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Justificatif introuvable sur le stockage");
        }
    }

    private void checkCanView(ContributionTransaction transaction) {
        if (currentUserProvider.hasAnyRole("ADMIN", "TRESORIER")) {
            return;
        }
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId == null || !currentMemberId.equals(transaction.getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paiement introuvable");
        }
    }

    private void deleteQuietly(String storageKey) {
        try {
            fileStorageService.delete(storageKey);
        } catch (IOException ignored) {
            // Suppression best-effort : ne doit pas bloquer le remplacement du justificatif.
        }
    }

    private ContributionTransaction getOrThrow(UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paiement introuvable"));
    }
}
