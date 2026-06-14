package top.egon.mario.rag.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.rag.po.enums.RagFeedbackType;

/**
 * User feedback for RAG answers and cited sources.
 */
@Getter
@Setter
@Entity
@Table(name = "rag_feedback")
public class RagFeedbackPo extends BaseAuditablePo {

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "message_id", length = 64)
    private String messageId;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false)
    private RagFeedbackType feedbackType;

    @Column(name = "question")
    private String question;

    @Column(name = "answer_preview", length = 1024)
    private String answerPreview;

    @Column(name = "source_chunk_ids", length = 1024)
    private String sourceChunkIds;

    @Column(name = "comment_text", length = 1024)
    private String commentText;

}
