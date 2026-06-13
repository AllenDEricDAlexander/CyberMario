package top.egon.mario.rag.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rag.config.RagProperties;
import top.egon.mario.rag.po.enums.RagStorageType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Local-disk implementation of RAG file storage.
 */
@Component
@Slf4j
public class LocalRagFileStorage implements RagFileStorage {

    private final Path root;

    public LocalRagFileStorage(RagProperties properties) {
        this.root = Path.of(properties.storage().localRoot()).toAbsolutePath().normalize();
    }

    @Override
    public RagStoredFile save(Path temporaryFile, String sha256, String originalFilename) throws IOException {
        Files.createDirectories(root);
        String storageKey = storageKey(sha256, originalFilename);
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("resolved file target is outside the RAG storage root");
        }
        Files.createDirectories(target.getParent());
        if (!Files.exists(target)) {
            Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
        LogUtil.info(log).log("rag local file stored, storageKey={}, sha256={}", storageKey, sha256);
        return new RagStoredFile(RagStorageType.LOCAL, null, storageKey, Files.size(target));
    }

    @Override
    public Resource load(String storageKey) {
        return new FileSystemResource(root.resolve(storageKey).normalize());
    }

    @Override
    public void deleteIfUnused(String storageKey) throws IOException {
        Path target = root.resolve(storageKey).normalize();
        if (target.startsWith(root)) {
            Files.deleteIfExists(target);
        }
    }

    private String storageKey(String sha256, String originalFilename) {
        String extension = "";
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        return sha256.substring(0, 2) + "/" + sha256 + extension;
    }

}
