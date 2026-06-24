package top.egon.mario.clocktower.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomProfilePo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomProfileRepository;
import top.egon.mario.im.context.ImContext;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.room.po.RoomMemberPo;
import top.egon.mario.room.po.RoomSpacePo;
import top.egon.mario.room.repository.RoomMemberRepository;
import top.egon.mario.room.repository.RoomSpaceRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClocktowerChatConversationResolver {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String MEMBER_TYPE_OWNER = "OWNER";
    private static final String MEMBER_TYPE_MEMBER = "MEMBER";
    private static final String MEMBER_TYPE_SPECTATOR = "SPECTATOR";
    private static final String PHASE_LOBBY = "LOBBY";

    private final ImConversationRepository conversationRepository;
    private final ImGroupRepository groupRepository;
    private final ImChannelRepository channelRepository;
    private final ImConversationMemberRepository memberRepository;
    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerRoomProfileRepository profileRepository;
    private final RoomSpaceRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;

    @Transactional(readOnly = true)
    public Optional<ClocktowerChatConversationContext> resolve(ImContext context) {
        if (context == null || !ClocktowerChatConstants.CONTEXT_TYPE.equals(context.contextType())
                || context.conversationId() == null) {
            return Optional.empty();
        }
        return conversationRepository.findByIdAndDeletedFalse(context.conversationId())
                .flatMap(this::resolve);
    }

    @Transactional(readOnly = true)
    public ClocktowerChatConversationContext require(Long conversationId) {
        if (conversationId == null) {
            throw new ClocktowerException("CLOCKTOWER_CHAT_CONVERSATION_ID_REQUIRED");
        }
        ImConversationPo conversation = conversationRepository.findByIdAndDeletedFalse(conversationId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_CHAT_CONVERSATION_NOT_FOUND"));
        return resolve(conversation)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_CHAT_CONVERSATION_NOT_FOUND"));
    }

    @Transactional(readOnly = true)
    public ClocktowerChatAccessContext accessContext(ClocktowerChatConversationContext context, Long userId,
                                                     boolean activeConversationMember,
                                                     ClocktowerChatViewerMode overrideMode) {
        ClocktowerChatViewerMode mode = overrideMode == null
                ? viewerMode(context, userId)
                : overrideMode;
        ClocktowerGamePo game = context.game();
        return new ClocktowerChatAccessContext(mode, context.groupKey(),
                context.conversation().getConversationType(), game == null ? PHASE_LOBBY : game.getPhase(),
                game == null ? 0 : game.getDayNo(), activeConversationMember);
    }

    @Transactional(readOnly = true)
    public boolean activeConversationMember(Long conversationId, Long userId) {
        return conversationId != null && userId != null
                && memberRepository.existsByConversationIdAndUserIdAndStatusAndDeletedFalse(
                conversationId, userId, STATUS_ACTIVE);
    }

    private Optional<ClocktowerChatConversationContext> resolve(ImConversationPo conversation) {
        if (!ClocktowerChatConstants.CONTEXT_TYPE.equals(conversation.getContextType())) {
            return Optional.empty();
        }
        Optional<ImGroupPo> group = groupRepository.findByIdAndDeletedFalse(conversation.getGroupId());
        Optional<ImChannelPo> channel = channelRepository.findByIdAndDeletedFalse(conversation.getChannelId());
        if (group.isEmpty() || channel.isEmpty()) {
            return Optional.empty();
        }
        if (ClocktowerChatConstants.SCOPE_GAME.equals(conversation.getScopeType())) {
            return gameRepository.findByIdAndDeletedFalse(conversation.getScopeId())
                    .map(game -> new ClocktowerChatConversationContext(conversation, channel.get().getChannelKey(),
                            group.get().getGroupKey(), game.getRoomId(), game));
        }
        if (ClocktowerChatConstants.SCOPE_ROOM.equals(conversation.getScopeType())) {
            return Optional.of(new ClocktowerChatConversationContext(conversation, channel.get().getChannelKey(),
                    group.get().getGroupKey(), conversation.getScopeId(), null));
        }
        return Optional.empty();
    }

    private ClocktowerChatViewerMode viewerMode(ClocktowerChatConversationContext context, Long userId) {
        if (userId == null || context == null || context.roomId() == null) {
            return ClocktowerChatViewerMode.UNKNOWN;
        }
        RoomSpacePo room = roomRepository.findByIdAndDeletedFalse(context.roomId()).orElse(null);
        ClocktowerRoomProfilePo profile = profileRepository.findByRoomIdAndDeletedFalse(context.roomId()).orElse(null);
        if (isStoryteller(room, profile, userId)) {
            return ClocktowerChatViewerMode.STORYTELLER;
        }
        if (context.game() != null && activeGamePlayer(context.game().getId(), userId)) {
            return ClocktowerChatViewerMode.PLAYER;
        }
        Optional<RoomMemberPo> member = roomMemberRepository.findActiveByRoomIdAndUserId(context.roomId(), userId);
        if (member.isEmpty()) {
            return ClocktowerChatViewerMode.UNKNOWN;
        }
        String memberType = member.get().getMemberType();
        if (MEMBER_TYPE_SPECTATOR.equals(memberType)) {
            return ClocktowerChatViewerMode.SPECTATOR;
        }
        if (MEMBER_TYPE_MEMBER.equals(memberType) || MEMBER_TYPE_OWNER.equals(memberType)) {
            return ClocktowerChatViewerMode.PLAYER;
        }
        return ClocktowerChatViewerMode.UNKNOWN;
    }

    private boolean isStoryteller(RoomSpacePo room, ClocktowerRoomProfilePo profile, Long userId) {
        return (room != null && userId.equals(room.getOwnerUserId()))
                || (profile != null && userId.equals(profile.getStorytellerUserId()));
    }

    private boolean activeGamePlayer(Long gameId, Long userId) {
        return gameSeatRepository.findByGameIdAndUserIdAndDeletedFalse(gameId, userId)
                .filter(seat -> STATUS_ACTIVE.equals(seat.getStatus()))
                .map(ClocktowerGameSeatPo::getUserId)
                .isPresent();
    }
}
