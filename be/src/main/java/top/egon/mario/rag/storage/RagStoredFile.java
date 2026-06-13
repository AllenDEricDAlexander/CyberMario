package top.egon.mario.rag.storage;

import top.egon.mario.rag.po.enums.RagStorageType;

/**
 * Describes a file after it has been persisted by a storage backend.
 */
public record RagStoredFile(
        RagStorageType storageType,
        String bucket,
        String storageKey,
        long fileSize
) {
}
