package top.egon.mario.rag.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.rag.po.enums.RagFileType;
import top.egon.mario.rag.po.enums.RagStorageType;

/**
 * Globally de-duplicated physical file object used by one or more user documents.
 */
@Getter
@Setter
@Entity
@Table(name = "rag_file_object", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rag_file_sha_deleted", columnNames = {"sha256", "deleted"})
})
public class RagFileObjectPo extends BaseAuditablePo {

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false)
    private RagStorageType storageType = RagStorageType.LOCAL;

    @Column(name = "bucket", length = 128)
    private String bucket;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private RagFileType fileType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

}
