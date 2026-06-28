package top.egon.mario.im.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.im.service.ImException;
import top.egon.mario.im.facade.dto.command.MarkReadCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.query.AuditHistoryQuery;
import top.egon.mario.im.facade.dto.query.HistoryQuery;
import top.egon.mario.im.facade.dto.view.MessageView;
import top.egon.mario.im.facade.dto.view.UnreadView;
import top.egon.mario.im.facade.mapper.ImFacadeMapper;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationMemberPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImDmPairPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.ImMembershipPo;
import top.egon.mario.im.po.ImMessagePo;
import top.egon.mario.im.po.enums.ImConversationStatus;
import top.egon.mario.im.po.enums.ImGlobalMuteScopeType;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImMessageStatus;
import top.egon.mario.im.po.enums.ImMessageType;
import top.egon.mario.im.po.enums.ImOutboxEventType;
import top.egon.mario.im.po.enums.ImSurfaceStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImAccessContext;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.policy.PolicyRegistry;
import top.egon.mario.im.repository.ImBanRepository;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.im.repository.ImDmPairRepository;
import top.egon.mario.im.repository.ImGlobalMuteRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.im.repository.ImMembershipRepository;
import top.egon.mario.im.repository.ImMessageRepository;

import java.time.Instant;
import java.util.Locale;

@Service
public class MessageService {

    private static final long PLATFORM_SCOPE_ID = 0L;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_AUDIT_PAGE_SIZE = 200;
    private static final int MAX_CLIENT_MSG_ID_LENGTH = 128;
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_CLOCKTOWER_ADMIN = "CLOCKTOWER_ADMIN";

    private final ImConversationRepository conversationRepository;
    private final ImConversationMemberRepository conversationMemberRepository;
    private final ImMessageRepository messageRepository;
    private final OutboxService outboxService;
    private final InboxService inboxService;
    private final ImMembershipRepository membershipRepository;
    private final ImChannelRepository channelRepository;
    private final ImGroupRepository groupRepository;
    private final ImDmPairRepository dmPairRepository;
    private final ImBanRepository banRepository;
    private final ImGlobalMuteRepository globalMuteRepository;
    private final PolicyRegistry policyRegistry;
    private final ObjectMapper objectMapper;
    private final ImFacadeMapper mapper = new ImFacadeMapper();

    public MessageService(ImConversationRepository conversationRepository,
                          ImConversationMemberRepository conversationMemberRepository,
                          ImMessageRepository messageRepository,
                          OutboxService outboxService,
                          InboxService inboxService,
                          ImMembershipRepository membershipRepository,
                          ImChannelRepository channelRepository,
                          ImGroupRepository groupRepository,
                          ImDmPairRepository dmPairRepository,
                          ImBanRepository banRepository,
                          ImGlobalMuteRepository globalMuteRepository,
                          PolicyRegistry policyRegistry,
                          ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.messageRepository = messageRepository;
        this.outboxService = outboxService;
        this.inboxService = inboxService;
        this.membershipRepository = membershipRepository;
        this.channelRepository = channelRepository;
        this.groupRepository = groupRepository;
        this.dmPairRepository = dmPairRepository;
        this.banRepository = banRepository;
        this.globalMuteRepository = globalMuteRepository;
        this.policyRegistry = policyRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MessageView send(SendMessageCommand command) {
        if (command == null) {
            throw new ImException("IM_SEND_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        Long conversationId = requireId(command.conversationId(), "IM_CONVERSATION_ID_REQUIRED");
        String clientMsgId = clientMsgId(command.clientMsgId());
        ImMessageType messageType = messageType(command.messageType());
        String content = requireContent(command.content());
        String payloadJson = jsonObject(command.payloadJson(), "IM_MESSAGE_PAYLOAD_INVALID");
        String metadataJson = jsonObject(command.metadataJson(), "IM_MESSAGE_METADATA_INVALID");

        ImConversationPo conversation = conversationRepository.findLockedByIdAndDeletedFalse(conversationId)
                .orElseThrow(() -> new ImException("IM_CONVERSATION_NOT_FOUND"));
        if (clientMsgId != null) {
            var existing = messageRepository.findByConversationIdAndSenderUserIdAndClientMsgIdAndDeletedFalse(
                    conversation.getId(), principal.userId(), clientMsgId);
            if (existing.isPresent()) {
                return mapper.toMessageView(existing.get());
            }
        }

        ImAccessContext accessContext = accessContext(conversation, principal, Instant.now());
        if (!policyRegistry.resolveSendPolicy(conversation.getContextType()).canSend(accessContext)) {
            throw new ImException("IM_SEND_DENIED");
        }

        Instant now = Instant.now();
        long nextSeq = sequence(conversation.getMessageSeq()) + 1;
        ImMessagePo message = new ImMessagePo();
        message.setConversationId(conversation.getId());
        message.setSenderUserId(principal.userId());
        message.setMessageSeq(nextSeq);
        message.setClientMsgId(clientMsgId);
        message.setMessageType(messageType);
        message.setContent(content);
        message.setPayloadJson(payloadJson);
        message.setStatus(ImMessageStatus.VISIBLE);
        message.setSentAt(now);
        message.setMetadataJson(metadataJson);
        message = messageRepository.saveAndFlush(message);

        conversation.setMessageSeq(nextSeq);
        conversation.setLastMessageId(message.getId());
        conversation.setLastMessageAt(now);
        conversation.setLastActiveAt(now);
        conversationRepository.saveAndFlush(conversation);

        outboxService.createPending(
                conversation.getId(), message.getId(), nextSeq, ImOutboxEventType.MESSAGE_CREATED, now);
        inboxService.fanOutMessage(conversation.getId(), message.getId(), nextSeq);
        return mapper.toMessageView(message);
    }

    @Transactional(readOnly = true)
    public Page<MessageView> history(HistoryQuery query) {
        if (query == null) {
            throw new ImException("IM_HISTORY_QUERY_REQUIRED");
        }
        Long conversationId = requireId(query.conversationId(), "IM_CONVERSATION_ID_REQUIRED");
        ImConversationPo conversation = conversationRepository.findByIdAndDeletedFalse(conversationId)
                .orElseThrow(() -> new ImException("IM_CONVERSATION_NOT_FOUND"));
        ImAccessContext accessContext = accessContext(conversation, query.principal(), Instant.now());
        if (!policyRegistry.resolveVisibilityPolicy(conversation.getContextType()).canRead(accessContext)) {
            throw new ImException("IM_HISTORY_FORBIDDEN");
        }

        Pageable pageable = page(query.page(), query.size());
        Page<ImMessagePo> messages;
        if (query.afterSeq() != null) {
            messages = messageRepository.findByConversationIdAndMessageSeqGreaterThanEqualAndDeletedFalseOrderByMessageSeqAsc(
                    conversationId, Math.max(0L, query.afterSeq()), pageable);
        } else if (query.beforeSeq() != null) {
            messages = messageRepository.findByConversationIdAndMessageSeqLessThanEqualAndDeletedFalseOrderByMessageSeqAsc(
                    conversationId, Math.max(0L, query.beforeSeq()), pageable);
        } else {
            messages = messageRepository.findByConversationIdAndDeletedFalseOrderByMessageSeqAsc(
                    conversationId, pageable);
        }
        return messages.map(mapper::toMessageView);
    }

    @Transactional(readOnly = true)
    public Page<MessageView> auditHistory(AuditHistoryQuery query) {
        if (query == null) {
            throw new ImException("IM_AUDIT_HISTORY_QUERY_REQUIRED");
        }
        requireAuditPrincipal(query.principal());
        Long conversationId = requireId(query.conversationId(), "IM_CONVERSATION_ID_REQUIRED");
        conversationRepository.findByIdAndDeletedFalse(conversationId)
                .orElseThrow(() -> new ImException("IM_CONVERSATION_NOT_FOUND"));

        Pageable pageable = auditPage(query.page(), query.size());
        Page<ImMessagePo> messages;
        if (query.afterSeq() != null) {
            messages = messageRepository.findByConversationIdAndMessageSeqGreaterThanEqualAndDeletedFalseOrderByMessageSeqAsc(
                    conversationId, Math.max(0L, query.afterSeq()), pageable);
        } else if (query.beforeSeq() != null) {
            messages = messageRepository.findByConversationIdAndMessageSeqLessThanEqualAndDeletedFalseOrderByMessageSeqAsc(
                    conversationId, Math.max(0L, query.beforeSeq()), pageable);
        } else {
            messages = messageRepository.findByConversationIdAndDeletedFalseOrderByMessageSeqAsc(
                    conversationId, pageable);
        }
        return messages.map(mapper::toMessageView);
    }

    @Transactional
    public UnreadView markRead(MarkReadCommand command) {
        if (command == null) {
            throw new ImException("IM_MARK_READ_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        Long conversationId = requireId(command.conversationId(), "IM_CONVERSATION_ID_REQUIRED");
        Long requestedSeq = requireId(command.messageSeq(), "IM_MESSAGE_SEQ_REQUIRED");

        ImConversationPo conversation = conversationRepository.findLockedByIdAndDeletedFalse(conversationId)
                .orElseThrow(() -> new ImException("IM_CONVERSATION_NOT_FOUND"));
        ImConversationMemberPo member = conversationMemberRepository.findActiveByConversationIdAndUserId(
                        conversation.getId(), principal.userId())
                .orElseThrow(() -> new ImException("IM_MEMBER_REQUIRED"));

        long conversationSeq = sequence(conversation.getMessageSeq());
        long currentSeq = sequence(member.getLastReadSeq());
        long targetSeq = Math.max(0L, Math.min(requestedSeq, conversationSeq));
        long nextSeq = Math.max(currentSeq, targetSeq);
        if (nextSeq == currentSeq) {
            return new UnreadView(conversation.getId(), principal.userId(), nextSeq,
                    Math.max(0L, conversationSeq - nextSeq));
        }

        member.setLastReadSeq(nextSeq);
        conversationMemberRepository.saveAndFlush(member);
        inboxService.markReadUpTo(principal.userId(), conversation.getId(), nextSeq);
        if (nextSeq > 0L) {
            outboxService.createPending(
                    conversation.getId(), messageIdAtSequence(conversation.getId(), nextSeq), nextSeq,
                    ImOutboxEventType.READ_UPDATED, Instant.now());
        }
        return new UnreadView(conversation.getId(), principal.userId(), nextSeq,
                Math.max(0L, conversationSeq - nextSeq));
    }

    private ImAccessContext accessContext(ImConversationPo conversation, ImPrincipal principal, Instant now) {
        SurfaceState surface = surfaceState(conversation);
        ImConversationMemberPo conversationMember = principal == null ? null : conversationMemberRepository
                .findByConversationIdAndUserIdAndDeletedFalse(conversation.getId(), principal.userId())
                .orElse(null);
        ImMembershipPo membership = surfaceMembership(surface.surfaceType(), surface.surfaceId(), principal);
        ImMembershipStatus membershipStatus = effectiveMembershipStatus(membership, conversationMember);
        return new ImAccessContext(
                principal,
                conversation.getContextType(),
                conversation.getContextId(),
                conversation.getConversationTypeEnum(),
                conversationStatus(conversation),
                surface.surfaceType(),
                surface.surfaceId(),
                surface.surfaceStatus(),
                membershipStatus,
                membership == null ? null : membership.getMutedUntil(),
                activeConversationMember(conversationMember) && Boolean.TRUE.equals(conversationMember.getMuted()),
                activeBan(surface, principal, now),
                activeGlobalMute(conversation, principal, now),
                surface.dmPairParticipant(principal),
                surface.dmPairFrozen(),
                now);
    }

    private SurfaceState surfaceState(ImConversationPo conversation) {
        ImSurfaceType surfaceType = conversation.getOwnerSurfaceType();
        Long surfaceId = conversation.getOwnerSurfaceId();
        if (ImSurfaceType.CHANNEL.equals(surfaceType)) {
            ImChannelPo channel = channelRepository.findByIdAndDeletedFalse(surfaceId).orElse(null);
            return new SurfaceState(surfaceType, surfaceId,
                    channel == null ? ImSurfaceStatus.ARCHIVED : channel.getStatus(),
                    null);
        }
        if (ImSurfaceType.GROUP.equals(surfaceType)) {
            ImGroupPo group = groupRepository.findByIdAndDeletedFalse(surfaceId).orElse(null);
            return new SurfaceState(surfaceType, surfaceId,
                    group == null ? ImSurfaceStatus.ARCHIVED : group.getStatus(),
                    null);
        }
        if (ImSurfaceType.DM_PAIR.equals(surfaceType)) {
            ImDmPairPo pair = dmPairRepository.findByIdAndDeletedFalse(surfaceId).orElse(null);
            return new SurfaceState(surfaceType, surfaceId,
                    pair == null ? ImSurfaceStatus.ARCHIVED : ImSurfaceStatus.ACTIVE,
                    pair);
        }
        return new SurfaceState(surfaceType, surfaceId, ImSurfaceStatus.ARCHIVED, null);
    }

    private ImMembershipPo surfaceMembership(ImSurfaceType surfaceType, Long surfaceId, ImPrincipal principal) {
        if (principal == null || surfaceType == null || surfaceId == null || ImSurfaceType.DM_PAIR.equals(surfaceType)) {
            return null;
        }
        return membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
                        surfaceType, surfaceId, principal.userId())
                .orElse(null);
    }

    private ImMembershipStatus effectiveMembershipStatus(ImMembershipPo membership,
                                                         ImConversationMemberPo conversationMember) {
        if (membership == null) {
            return null;
        }
        if (ImMembershipStatus.ACTIVE.equals(membership.getStatus()) && !activeConversationMember(conversationMember)) {
            return null;
        }
        return membership.getStatus();
    }

    private boolean activeConversationMember(ImConversationMemberPo conversationMember) {
        return conversationMember != null && ImMembershipStatus.ACTIVE.equals(conversationMember.getStatus());
    }

    private boolean activeBan(SurfaceState surface, ImPrincipal principal, Instant now) {
        if (principal == null || surface.surfaceType() == null || surface.surfaceId() == null
                || ImSurfaceType.DM_PAIR.equals(surface.surfaceType())) {
            return false;
        }
        return banRepository.findActiveBan(surface.surfaceType(), surface.surfaceId(), principal.userId(), now).isPresent();
    }

    private boolean activeGlobalMute(ImConversationPo conversation, ImPrincipal principal, Instant now) {
        if (principal == null) {
            return false;
        }
        if (globalMuteRepository.findActiveMute(
                principal.userId(), ImGlobalMuteScopeType.PLATFORM, PLATFORM_SCOPE_ID, now).isPresent()) {
            return true;
        }
        Long contextId = conversation.getContextId();
        return contextId != null && globalMuteRepository.findActiveMute(
                principal.userId(), ImGlobalMuteScopeType.CONTEXT, contextId, now).isPresent();
    }

    private ImPrincipal requirePrincipal(ImPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ImException("IM_PRINCIPAL_REQUIRED");
        }
        return principal;
    }

    private void requireAuditPrincipal(ImPrincipal principal) {
        if (principal == null || !auditRole(principal)) {
            throw new ImException("IM_AUDIT_FORBIDDEN");
        }
    }

    private boolean auditRole(ImPrincipal principal) {
        return principal.roleCodes().contains(ROLE_SUPER_ADMIN)
                || principal.roleCodes().contains(ROLE_CLOCKTOWER_ADMIN);
    }

    private Long requireId(Long id, String code) {
        if (id == null) {
            throw new ImException(code);
        }
        return id;
    }

    private ImMessageType messageType(String value) {
        if (!StringUtils.hasText(value)) {
            return ImMessageType.TEXT;
        }
        String messageType = value.trim();
        try {
            return ImMessageType.valueOf(messageType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ImException("IM_MESSAGE_TYPE_INVALID", messageType);
        }
    }

    private String requireContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new ImException("IM_MESSAGE_CONTENT_REQUIRED");
        }
        return content;
    }

    private String optionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String clientMsgId(String value) {
        String clientMsgId = optionalText(value);
        if (clientMsgId != null && clientMsgId.length() > MAX_CLIENT_MSG_ID_LENGTH) {
            throw new ImException("IM_CLIENT_MSG_ID_TOO_LONG");
        }
        return clientMsgId;
    }

    private String jsonObject(String value, String code) {
        String json = StringUtils.hasText(value) ? value.trim() : "{}";
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                throw new ImException(code);
            }
        } catch (JsonProcessingException ex) {
            throw new ImException(code, json);
        }
        return json;
    }

    private Pageable page(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }

    private Pageable auditPage(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_AUDIT_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }

    private ImConversationStatus conversationStatus(ImConversationPo conversation) {
        return conversation.getStatus() == null ? ImConversationStatus.ARCHIVED : conversation.getStatus();
    }

    private long sequence(Long sequence) {
        return sequence == null ? 0L : sequence;
    }

    private Long messageIdAtSequence(Long conversationId, Long messageSeq) {
        return messageRepository.findByConversationIdAndMessageSeqAndDeletedFalse(conversationId, messageSeq)
                .map(ImMessagePo::getId)
                .orElseThrow(() -> new ImException("IM_MESSAGE_NOT_FOUND"));
    }

    private record SurfaceState(ImSurfaceType surfaceType, Long surfaceId, ImSurfaceStatus surfaceStatus,
                                ImDmPairPo dmPair) {

        boolean dmPairParticipant(ImPrincipal principal) {
            return principal != null
                    && dmPair != null
                    && (principal.userId().equals(dmPair.getUserLoId()) || principal.userId().equals(dmPair.getUserHiId()));
        }

        boolean dmPairFrozen() {
            return dmPair != null && Boolean.TRUE.equals(dmPair.getFrozen());
        }
    }
}
