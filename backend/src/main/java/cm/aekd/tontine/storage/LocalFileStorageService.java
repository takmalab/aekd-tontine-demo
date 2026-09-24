package cm.aekd.tontine.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Implémentation MVP de {@link FileStorageService} : stockage sur le
 * disque local du serveur (CLAUDE.md §16).
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path rootDirectory;

    public LocalFileStorageService(@Value("${app.storage.local.root:./storage}") String root) {
        this.rootDirectory = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(String subDirectory, String originalFilename, String contentType, InputStream content)
            throws IOException {
        Path targetDir = resolveSafe(subDirectory);
        Files.createDirectories(targetDir);

        String storedName = UUID.randomUUID() + extensionOf(originalFilename);
        Path targetFile = targetDir.resolve(storedName);

        long size;
        try (OutputStream out = Files.newOutputStream(targetFile)) {
            size = content.transferTo(out);
        }

        String storageKey = rootDirectory.relativize(targetFile).toString().replace('\\', '/');
        return new StoredFile(storageKey, originalFilename, contentType, size);
    }

    @Override
    public InputStream retrieve(String storageKey) throws IOException {
        return Files.newInputStream(resolveSafe(storageKey));
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolveSafe(storageKey));
    }

    /**
     * Empêche toute évasion du répertoire racine (ex: "../../etc/passwd")
     * quelle que soit la provenance de la clé.
     */
    private Path resolveSafe(String key) throws IOException {
        Path resolved = rootDirectory.resolve(key).normalize();
        if (!resolved.startsWith(rootDirectory)) {
            throw new IOException("Chemin de stockage invalide");
        }
        return resolved;
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1 ? filename.substring(dot).toLowerCase() : "";
    }
}
