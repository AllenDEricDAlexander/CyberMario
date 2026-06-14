package top.egon.mario.rag.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.rag.dto.request.RagFeedbackRequest;
import top.egon.mario.rag.dto.response.RagFeedbackResponse;
import top.egon.mario.rag.po.RagFeedbackPo;
import top.egon.mario.rag.repository.RagFeedbackRepository;
import top.egon.mario.rag.service.RagFeedbackService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.stream.Collectors;

/**
 * Default implementation for RAG feedback storage.
 */
@Service
@RequiredArgsConstructor
@Validated
public class RagFeedbackServiceImpl implements RagFeedbackService {

    private final RagFeedbackRepository feedbackRepository;

    @Override
    @Transactional
    public RagFeedbackResponse create(RagFeedbackRequest request, RbacPrincipal principal) {
        RagFeedbackPo feedback = new RagFeedbackPo();
        feedback.setTraceId(request.traceId());
        feedback.setMessageId(request.messageId());
        feedback.setUserId(principal == null ? null : principal.userId());
        feedback.setFeedbackType(request.feedbackType());
        feedback.setQuestion(request.question());
        feedback.setAnswerPreview(preview(request.answer(), 1024));
        feedback.setSourceChunkIds(request.sourceChunkIds() == null ? null : request.sourceChunkIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")));
        feedback.setCommentText(request.comment());
        RagFeedbackPo saved = feedbackRepository.save(feedback);
        return new RagFeedbackResponse(saved.getId(), saved.getTraceId(), saved.getMessageId(), saved.getUserId(),
                saved.getFeedbackType(), saved.getCreatedAt());
    }

    private String preview(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
