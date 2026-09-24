package cm.aekd.tontine.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstraction de stockage de fichiers (CLAUDE.md §16). Le MVP n'a
 * qu'une implémentation locale (LocalFileStorageService) ; l'interface
 * permet de brancher plus tard un stockage cloud (S3, Cloudinary,
 * Google Cloud Storage) sans changer les appelants.
 */
public interface FileStorageService {

    StoredFile store(String subDirectory, String originalFilename, String contentType, InputStream content) throws IOException;

    InputStream retrieve(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}
