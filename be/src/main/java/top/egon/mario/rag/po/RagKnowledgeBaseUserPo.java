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
import top.egon.mario.rag.po.enums.RagAccessLevel;

/**
 * User-scoped access grant for a RAG knowledge base.
 */
@Getter
@Setter
@Entity
@Table(name = "rag_knowledge_base_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rag_kb_user_deleted", columnNames = {"knowledge_base_id", "user_id", "deleted"})
})
public class RagKnowledgeBaseUserPo extends BaseAuditablePo {

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false)
    private RagAccessLevel accessLevel = RagAccessLevel.READ;

}
