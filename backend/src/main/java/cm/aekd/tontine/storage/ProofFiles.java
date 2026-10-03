package cm.aekd.tontine.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

/**
 * Contrôle commun des justificatifs (paiements de cotisations, remboursements
 * de prêts) : formats JPG, JPEG, PNG, PDF (CLAUDE.md §16) et taille maximale.
 */
public final class ProofFiles {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf");
    // Limite technique anti-abus (pas une règle métier CLAUDE.md) : 10 Mo.
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private ProofFiles() {
    }

    public static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun fichier fourni");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le fichier dépasse la taille maximale autorisée (10 Mo)");
        }
        String contentType = file.getContentType();
        String extension = extension(file.getOriginalFilename());
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))
                || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Format de fichier non autorisé. Formats acceptés : JPG, JPEG, PNG, PDF");
        }
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
