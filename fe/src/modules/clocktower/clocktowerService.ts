import {requestJson, streamServerSentEvents} from '../../services/request'
import {buildSearchParams} from '../../services/urlSearch'
import {
    listImConversations,
    listImMessages,
    markImRead,
    sendImMessage,
} from '../im/imService'
import type {ConversationView, MessagePage, MessageView, UnreadView} from '../im/imTypes'
import type {
    BoardValidationResponse,
    ClocktowerActionResponse,
    ClocktowerAuditQuery,
    ClocktowerChatReadStateResponse,
    ClocktowerBoardConfigResponse,
    ClocktowerBoardGenerateRequest,
    ClocktowerBoardGenerateResponse,
    ClocktowerBoardQuery,
    ClocktowerBoardSaveRequest,
    ClocktowerBoardValidateRequest,
    ClocktowerConversationResponse,
    ClocktowerEventResponse,
    ClocktowerFlowResponse,
    ClocktowerGameActionRequest,
    ClocktowerGameActionResponse,
    ClocktowerGameAuditResponse,
    ClocktowerGameHistoryResponse,
    ClocktowerGameReplayResponse,
    ClocktowerGameResponse,
    ClocktowerGameViewResponse,
    ClocktowerGrimoireResponse,
    ClocktowerJinxRuleResponse,
    ClocktowerMessageResponse,
    ClocktowerMicSessionView,
    ClocktowerNightChecklistResponse,
    ClocktowerNightOrderGroupResponse,
    ClocktowerNightOrderResponse,
    ClocktowerPage,
    ClocktowerPlayerActionRequest,
    ClocktowerPlayerViewResponse,
    ClocktowerReplayResponse,
    ClocktowerRoleResponse,
    ClocktowerRoleTypeCode,
    ClocktowerRoomCreateRequest,
    ClocktowerRoomInvitationCreateRequest,
    ClocktowerRoomInvitationResponse,
    ClocktowerRoomJoinRequest,
    ClocktowerRoomMemberActionRequest,
    ClocktowerRoomAuditResponse,
    ClocktowerRoomResponse,
    ClocktowerReadStateRequest,
    ClocktowerSeatClaimRequest,
    ClocktowerRoomStartRequest,
    ClocktowerRulingApplyResponse,
    ClocktowerRulingCreateRequest,
    ClocktowerRulingResponse,
    ClocktowerRulingUndoRequest,
    ClocktowerScriptCode,
    ClocktowerScriptResponse,
    ClocktowerSeatResponse,
    ClocktowerSendMessageRequest,
    ClocktowerStartGameResponse,
    ClocktowerStorytellerActionRequest,
    ClocktowerTermResponse,
    ClocktowerUpdateSeatRequest,
    CloseNominationRequest,
    ExecutionConfirmRequest,
    SkipNightTaskRequest,
    ClocktowerVoteReplayResponse,
    StorytellerActionResponse,
} from './clocktowerTypes'

export const CLOCKTOWER_IM_CONTEXT_TYPE = 'CLOCKTOWER_ROOM'

export function getClocktowerScripts() {
    return requestJson<ClocktowerScriptResponse[]>('/api/clocktower/scripts')
}

export function getClocktowerScript(scriptCode: ClocktowerScriptCode) {
    return requestJson<ClocktowerScriptResponse>(`/api/clocktower/scripts/${scriptCode}`)
}

export function getClocktowerRoles(
    scriptCode: ClocktowerScriptCode,
    params: { roleType?: ClocktowerRoleTypeCode; enabled?: boolean } = {},
) {
    const search = buildSearchParams(params)
    return requestJson<ClocktowerRoleResponse[]>(`/api/clocktower/scripts/${scriptCode}/roles${suffix(search)}`)
}

export function getClocktowerNightOrder(scriptCode: ClocktowerScriptCode, params: { nightType?: string } = {}) {
    const search = buildSearchParams(params)
    return requestJson<ClocktowerNightOrderResponse[]>(`/api/clocktower/scripts/${scriptCode}/night-order${suffix(search)}`)
}

export function getClocktowerGroupedNightOrder(scriptCode: ClocktowerScriptCode) {
    return requestJson<ClocktowerNightOrderGroupResponse>(`/api/clocktower/scripts/${scriptCode}/night-order/grouped`)
}

export function getClocktowerTerms(params: { keyword?: string; category?: string } = {}) {
    const search = buildSearchParams(params)
    return requestJson<ClocktowerTermResponse[]>(`/api/clocktower/terms${suffix(search)}`)
}

export function getClocktowerJinxRules(params: { roleCode?: string; severity?: string } = {}) {
    const search = buildSearchParams(params)
    return requestJson<ClocktowerJinxRuleResponse[]>(`/api/clocktower/jinx-rules${suffix(search)}`)
}

export function generateClocktowerBoard(request: ClocktowerBoardGenerateRequest) {
    return requestJson<ClocktowerBoardGenerateResponse>('/api/clocktower/boards/generate', {
        method: 'POST',
        body: request,
    })
}

export function validateClocktowerBoard(request: ClocktowerBoardValidateRequest) {
    return requestJson<BoardValidationResponse>('/api/clocktower/boards/validate', {
        method: 'POST',
        body: request,
    })
}

export function saveClocktowerBoard(request: ClocktowerBoardSaveRequest) {
    return requestJson<ClocktowerBoardConfigResponse>('/api/clocktower/boards/save', {
        method: 'POST',
        body: request,
    })
}

export function listClocktowerBoards(params: ClocktowerBoardQuery = {}) {
    const search = buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
        scriptCode: params.scriptCode,
        playerCount: params.playerCount,
        valid: params.valid,
    })
    return requestJson<ClocktowerPage<ClocktowerBoardConfigResponse>>(`/api/clocktower/boards${suffix(search)}`)
}

export function deleteClocktowerBoard(boardId: number) {
    return requestJson<void>(`/api/clocktower/boards/${boardId}`, {method: 'DELETE'})
}

export function createClocktowerRoom(request: ClocktowerRoomCreateRequest) {
    return requestJson<ClocktowerRoomResponse>('/api/clocktower/rooms', {
        method: 'POST',
        body: request,
    })
}

export function listClocktowerRooms() {
    return requestJson<ClocktowerRoomResponse[]>('/api/clocktower/rooms')
}

export function getClocktowerRoom(roomId: number) {
    return requestJson<ClocktowerRoomResponse>(`/api/clocktower/rooms/${roomId}`)
}

export function joinClocktowerRoom(roomId: number, request: ClocktowerRoomJoinRequest) {
    return requestJson<ClocktowerSeatResponse>(`/api/clocktower/rooms/${roomId}/join`, {
        method: 'POST',
        body: request,
    })
}

export function enterClocktowerRoom(roomId: number) {
    return requestJson<ClocktowerRoomResponse>(`/api/clocktower/rooms/${roomId}/enter`, {method: 'POST'})
}

export function heartbeatClocktowerRoom(roomId: number) {
    return requestJson<void>(`/api/clocktower/rooms/${roomId}/heartbeat`, {method: 'POST'})
}

export function claimClocktowerSeat(roomId: number, seatNo: number, request?: ClocktowerSeatClaimRequest) {
    return requestJson<ClocktowerSeatResponse>(`/api/clocktower/rooms/${roomId}/seats/${seatNo}/claim`, {
        method: 'POST',
        body: request,
    })
}

export function createClocktowerInvitation(roomId: number, request: ClocktowerRoomInvitationCreateRequest) {
    return requestJson<ClocktowerRoomInvitationResponse>(`/api/clocktower/rooms/${roomId}/invitations`, {
        method: 'POST',
        body: request,
    })
}

export function acceptClocktowerInvitation(roomId: number, invitationId: number) {
    return requestJson<ClocktowerRoomInvitationResponse>(
        `/api/clocktower/rooms/${roomId}/invitations/${invitationId}/accept`,
        {method: 'POST'},
    )
}

export function declineClocktowerInvitation(roomId: number, invitationId: number) {
    return requestJson<ClocktowerRoomInvitationResponse>(
        `/api/clocktower/rooms/${roomId}/invitations/${invitationId}/decline`,
        {method: 'POST'},
    )
}

export function kickClocktowerRoomMember(
    roomId: number,
    userId: number,
    request?: ClocktowerRoomMemberActionRequest,
) {
    return requestJson<void>(`/api/clocktower/rooms/${roomId}/members/${userId}/kick`, {
        method: 'POST',
        body: request,
    })
}

export function leaveClocktowerRoom(roomId: number) {
    return requestJson<void>(`/api/clocktower/rooms/${roomId}/leave`, {method: 'POST'})
}

export function updateClocktowerSeat(roomId: number, seatId: number, request: ClocktowerUpdateSeatRequest) {
    return requestJson<ClocktowerRoomResponse>(`/api/clocktower/rooms/${roomId}/seats/${seatId}`, {
        method: 'PATCH',
        body: request,
    })
}

export function startClocktowerGame(roomId: number) {
    return requestJson<ClocktowerGameResponse>(`/api/clocktower/rooms/${roomId}/games/start`, {
        method: 'POST',
    })
}

export function startClocktowerRoom(roomId: number, request: ClocktowerRoomStartRequest) {
    return requestJson<ClocktowerStartGameResponse>(`/api/clocktower/rooms/${roomId}/start`, {
        method: 'POST',
        body: request,
    })
}

export function getClocktowerGrimoire(roomId: number) {
    return requestJson<ClocktowerGrimoireResponse>(`/api/clocktower/rooms/${roomId}/grimoire`)
}

export function getClocktowerNightChecklist(roomId: number) {
    return requestJson<ClocktowerNightChecklistResponse>(`/api/clocktower/rooms/${roomId}/night-checklist`)
}

export function getClocktowerFlow(roomId: number) {
    return requestJson<ClocktowerFlowResponse>(`/api/clocktower/rooms/${roomId}/flow`)
}

export function advanceClocktowerFlow(roomId: number) {
    return requestJson<ClocktowerFlowResponse>(`/api/clocktower/rooms/${roomId}/flow/advance`, {method: 'POST'})
}

export function skipClocktowerNightTask(roomId: number, taskId: number, request: SkipNightTaskRequest) {
    return requestJson<ClocktowerFlowResponse>(`/api/clocktower/rooms/${roomId}/night-tasks/${taskId}/skip`, {
        method: 'POST',
        body: request,
    })
}

export function closeClocktowerNomination(roomId: number, nominationId: number, request: CloseNominationRequest) {
    return requestJson<ClocktowerFlowResponse>(`/api/clocktower/rooms/${roomId}/nominations/${nominationId}/close`, {
        method: 'POST',
        body: request,
    })
}

export function confirmClocktowerExecution(roomId: number, request: ExecutionConfirmRequest) {
    return requestJson<ClocktowerFlowResponse>(`/api/clocktower/rooms/${roomId}/execution/confirm`, {
        method: 'POST',
        body: request,
    })
}

export function createClocktowerRuling(roomId: number, request: ClocktowerRulingCreateRequest) {
    return requestJson<ClocktowerRulingApplyResponse>(`/api/clocktower/rooms/${roomId}/rulings`, {
        method: 'POST',
        body: request,
    })
}

export function listClocktowerRulings(roomId: number) {
    return requestJson<ClocktowerRulingResponse[]>(`/api/clocktower/rooms/${roomId}/rulings`)
}

export function undoClocktowerRuling(roomId: number, rulingId: number, request: ClocktowerRulingUndoRequest) {
    return requestJson<ClocktowerRulingApplyResponse>(`/api/clocktower/rooms/${roomId}/rulings/${rulingId}/undo`, {
        method: 'POST',
        body: request,
    })
}

export function getClocktowerPlayerView(roomId: number, params: { seatId?: number } = {}) {
    const search = buildSearchParams(params)
    return requestJson<ClocktowerPlayerViewResponse>(`/api/clocktower/rooms/${roomId}/view${suffix(search)}`)
}

export function getClocktowerGameView(gameId: number) {
    return requestJson<ClocktowerGameViewResponse>(`/api/clocktower/games/${gameId}/view`)
}

export function submitClocktowerPlayerAction(roomId: number, request: ClocktowerPlayerActionRequest) {
    return requestJson<ClocktowerActionResponse>(`/api/clocktower/rooms/${roomId}/actions`, {
        method: 'POST',
        body: request,
    })
}

export function submitClocktowerGameAction(gameId: number, request: ClocktowerGameActionRequest) {
    return requestJson<ClocktowerGameActionResponse>(`/api/clocktower/games/${gameId}/actions`, {
        method: 'POST',
        body: request,
    })
}

export function getClocktowerMicSession(gameId: number) {
    return requestJson<ClocktowerMicSessionView>(`/api/clocktower/games/${gameId}/mic`)
}

export function startClocktowerDayMic(gameId: number) {
    return requestJson<ClocktowerMicSessionView>(`/api/clocktower/games/${gameId}/mic/start-day`, {method: 'POST'})
}

export function finishClocktowerMicTurn(gameId: number, turnId: number) {
    return requestJson<ClocktowerMicSessionView>(`/api/clocktower/games/${gameId}/mic/turns/${turnId}/finish`, {
        method: 'POST',
    })
}

export function skipClocktowerMicTurn(gameId: number, turnId: number) {
    return requestJson<ClocktowerMicSessionView>(`/api/clocktower/games/${gameId}/mic/turns/${turnId}/skip`, {
        method: 'POST',
    })
}

export function grabClocktowerMic(gameId: number) {
    return requestJson<ClocktowerMicSessionView>(`/api/clocktower/games/${gameId}/mic/grab`, {method: 'POST'})
}

export function releaseClocktowerMic(gameId: number) {
    return requestJson<ClocktowerMicSessionView>(`/api/clocktower/games/${gameId}/mic/release`, {method: 'POST'})
}

export function extendClocktowerMicSession(gameId: number, seconds: number) {
    return requestJson<ClocktowerMicSessionView>(`/api/clocktower/games/${gameId}/mic/extend`, {
        method: 'POST',
        body: {seconds},
    })
}

export function closeClocktowerMicSession(gameId: number) {
    return requestJson<ClocktowerMicSessionView>(`/api/clocktower/games/${gameId}/mic/close`, {method: 'POST'})
}

export function submitClocktowerStorytellerAction(roomId: number, request: ClocktowerStorytellerActionRequest) {
    return requestJson<StorytellerActionResponse>(`/api/clocktower/rooms/${roomId}/storyteller/actions`, {
        method: 'POST',
        body: request,
    })
}

export function getClocktowerReplay(roomId: number, params: { mode?: string; fromSeq?: number; toSeq?: number } = {}) {
    const search = buildSearchParams(params)
    return requestJson<ClocktowerReplayResponse>(`/api/clocktower/replays/${roomId}${suffix(search)}`)
}

export function getClocktowerGameReplay(gameId: number) {
    return requestJson<ClocktowerGameReplayResponse>(`/api/clocktower/games/${gameId}/replay`)
}

export function listClocktowerGameHistory() {
    return requestJson<ClocktowerGameHistoryResponse[]>('/api/clocktower/games/history')
}

export function getClocktowerReplayVotes(roomId: number) {
    return requestJson<ClocktowerVoteReplayResponse[]>(`/api/clocktower/replays/${roomId}/votes`)
}

export function listClocktowerChatConversations(roomId: number) {
    return listImConversations({
        contextType: CLOCKTOWER_IM_CONTEXT_TYPE,
        contextId: roomId,
    }).then((conversations) => conversations.map((conversation) => mapImConversationToClocktower(conversation, roomId)))
}

export function listClocktowerChatMessages(conversationId: number, params: { page?: number; size?: number } = {}) {
    return listImMessages(conversationId, params).then(mapImMessagePageToClocktower)
}

export function sendClocktowerChatMessage(conversationId: number, request: ClocktowerSendMessageRequest) {
    return sendImMessage({
        conversationId,
        clientMsgId: createClocktowerClientMsgId(),
        messageType: 'TEXT',
        content: request.content,
        metadataJson: request.metadataJson,
    }).then(mapImMessageToClocktower)
}

export function markClocktowerChatRead(conversationId: number, request: ClocktowerReadStateRequest) {
    return markImRead(conversationId, request).then(mapImUnreadToClocktowerReadState)
}

export function mapImConversationToClocktower(
    conversation: ConversationView,
    roomId = conversation.contextId ?? 0,
): ClocktowerConversationResponse {
    const surfaceKey = conversation.ownerSurfaceType ?? conversation.conversationType
    return {
        conversationId: conversation.id,
        roomId,
        gameId: conversation.contextId,
        channelKey: surfaceKey,
        groupKey: surfaceKey,
        conversationType: conversation.conversationType,
        displayPeerKey: null,
        messageSeq: conversation.messageSeq,
        lastMessageAt: conversation.lastMessageAt,
        lastActiveAt: conversation.lastActiveAt,
        lastMessage: conversation.lastMessage,
        unreadCount: conversation.unreadCount,
        ownerSurfaceType: conversation.ownerSurfaceType,
        ownerSurfaceId: conversation.ownerSurfaceId,
        imConversation: conversation,
    }
}

export function mapImMessageToClocktower(message: MessageView): ClocktowerMessageResponse {
    return {
        messageId: message.id,
        conversationId: message.conversationId,
        senderUserId: message.senderUserId,
        messageSeq: message.messageSeq,
        messageType: message.messageType,
        content: message.content ?? '',
        sentAt: message.sentAt ?? '',
        clientMsgId: message.clientMsgId,
        status: message.status,
        payloadJson: message.payloadJson,
        metadataJson: message.metadataJson,
        imMessage: message,
    }
}

export function mapClocktowerMessageToIm(message: ClocktowerMessageResponse): MessageView {
    return message.imMessage ?? {
        id: message.messageId,
        conversationId: message.conversationId,
        senderUserId: message.senderUserId,
        messageSeq: message.messageSeq,
        clientMsgId: message.clientMsgId,
        messageType: message.messageType,
        content: message.content,
        payloadJson: message.payloadJson,
        status: message.status ?? 'SENT',
        sentAt: message.sentAt,
        metadataJson: message.metadataJson,
    }
}

export function createClocktowerClientMsgId() {
    if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
        return `clocktower-${crypto.randomUUID()}`
    }
    return `clocktower-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export function createPendingClocktowerMessage(
    conversationId: number,
    content: string,
    clientMsgId: string,
    messageSeq: number,
): ClocktowerMessageResponse {
    const sentAt = new Date().toISOString()
    const messageId = -Date.now()
    return {
        messageId,
        conversationId,
        senderUserId: null,
        messageSeq,
        messageType: 'TEXT',
        content,
        sentAt,
        clientMsgId,
        status: 'PENDING',
        imMessage: {
            id: messageId,
            conversationId,
            senderUserId: null,
            messageSeq,
            clientMsgId,
            messageType: 'TEXT',
            content,
            payloadJson: null,
            status: 'PENDING',
            sentAt,
            metadataJson: null,
        },
    }
}

function mapImMessagePageToClocktower(page: MessagePage): ClocktowerPage<ClocktowerMessageResponse> {
    return {
        ...page,
        records: page.records.map(mapImMessageToClocktower),
    }
}

function mapImUnreadToClocktowerReadState(unread: UnreadView): ClocktowerChatReadStateResponse {
    return {
        readStateId: 0,
        conversationId: unread.conversationId,
        userId: unread.userId,
        lastReadMessageSeq: unread.lastReadSeq,
        lastReadAt: null,
    }
}

export function getClocktowerRoomAudit(roomId: number) {
    return requestJson<ClocktowerRoomAuditResponse>(`/api/admin/clocktower/rooms/${roomId}/audit`)
}

export function getClocktowerGameAudit(gameId: number) {
    return requestJson<ClocktowerGameAuditResponse>(`/api/admin/clocktower/games/${gameId}/audit`)
}

export function listClocktowerAdminChatMessages(conversationId: number, params: ClocktowerAuditQuery = {}) {
    const search = buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
    })
    return requestJson<ClocktowerPage<ClocktowerMessageResponse>>(
        `/api/admin/clocktower/chat/conversations/${conversationId}/messages${suffix(search)}`,
    )
}

export function streamClocktowerEvents(
    roomId: number,
    params: { seatId?: number; lastEventSeq?: number },
    signal: AbortSignal,
    onEvent: (event: ClocktowerEventResponse) => void,
) {
    return streamServerSentEvents<ClocktowerEventResponse>(
        `/api/clocktower/rooms/${roomId}/events/stream?${buildSearchParams(params)}`,
        {signal},
        onEvent,
    )
}

function suffix(search: string) {
    return search ? `?${search}` : ''
}
