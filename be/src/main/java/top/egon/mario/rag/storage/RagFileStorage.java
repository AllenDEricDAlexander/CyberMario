package top.egon.mario.rag.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Storage boundary for RAG files so local disk can be replaced by MinIO later.
 */
public interface RagFileStorage {

    /**
     * Stores a temporary uploaded file using the content hash as the stable key.
     */
    RagStoredFile save(Path temporaryFile, String sha256, String originalFilename) throws IOException;

    /**
     * Loads a previously stored file.
     */
    Resource load(String storageKey);

    /**
     * Deletes a stored file once no user document references it.
     */
    void deleteIfUnused(String storageKey) throws IOException;

}
