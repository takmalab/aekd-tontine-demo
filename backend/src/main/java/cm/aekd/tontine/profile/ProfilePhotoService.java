package cm.aekd.tontine.profile;

import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.contribution.StoredResource;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.storage.FileStorageService;
import cm.aekd.tontine.storage.StoredFile;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Photo de profil (facultative). Chacun ne gère que la sienne, mais toutes
 * sont visibles par les utilisateurs connectés (choix du propriétaire du projet). L'interface la recadre et la réduit avant l'envoi ; le serveur
 * contrôle tout de même le format et la taille.
 */
@Service
@Transactional
public class ProfilePhotoService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    // Limite technique anti-abus : 2 Mo (l'interface envoie une image d'environ 50 Ko).
    private static final long MAX_SIZE_BYTES = 2L * 1024 * 1024;

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final FileStorageService fileStorageService;
    private final MemberRepository memberRepository;

    public ProfilePhotoService(UserRepository userRepository, CurrentUserProvider currentUserProvider,
                               FileStorageService fileStorageService, MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.fileStorageService = fileStorageService;
    }

    public void upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune image fournie");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'image dépasse la taille maximale (2 Mo)");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Format d'image non autorisé. Formats acceptés : JPG, PNG, WEBP");
        }
        User user = currentUser();
        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = fileStorageService.store("profile-photos/" + user.getId(), file.getOriginalFilename(),
                    contentType, in);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Échec de l'enregistrement de la photo");
        }
        String previousKey = user.getPhotoStorageKey();
        user.setPhoto(stored.storageKey(), contentType);
        if (previousKey != null && !previousKey.equals(stored.storageKey())) {
            deleteAfterCommit(previousKey);
        }
    }

    @Transactional(readOnly = true)
    public StoredResource download() {
        return photoOf(currentUser());
    }

    /** Photo d'un membre, visible par tout utilisateur connecté. */
    @Transactional(readOnly = true)
    public StoredResource downloadForMember(UUID memberId) {
        User user = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"))
                .getUser();
        return photoOf(user);
    }

    private StoredResource photoOf(User user) {
        if (!user.hasPhoto()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune photo de profil");
        }
        try {
            return new StoredResource(fileStorageService.retrieve(user.getPhotoStorageKey()),
                    user.getPhotoContentType(), "photo");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo introuvable sur le stockage");
        }
    }

    public void delete() {
        User user = currentUser();
        String key = user.getPhotoStorageKey();
        if (key != null) {
            user.setPhoto(null, null);
            deleteAfterCommit(key);
        }
    }

    /** Le fichier n'est effacé que si la base a bien été mise à jour. */
    private void deleteAfterCommit(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    fileStorageService.delete(key);
                } catch (IOException ignored) {
                    // Best-effort : un fichier orphelin n'est pas bloquant.
                }
            }
        });
    }

    private User currentUser() {
        return currentUserProvider.currentUserId().flatMap(userRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié"));
    }
}
