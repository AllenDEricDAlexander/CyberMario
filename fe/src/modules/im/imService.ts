import {requestJson} from '../../services/request'
import {buildSearchParams} from '../../services/urlSearch'
import type {PageResult} from '../../types/api'
import type {
    AnnouncementRequest,
    ChannelView,
    ConversationView,
    CreateChannelRequest,
    CreateDmRequest,
    CreateGroupRequest,
    DmBlockRequest,
    GlobalMuteRequest,
    GovernanceSurfaceRequest,
    GroupView,
    ImSurfaceType,
    JoinRequestCreateRequest,
    JoinRequestRejectRequest,
    JoinResultView,
    ListChannelsParams,
    ListConversationsParams,
    ListGroupsParams,
    ListMessagesParams,
    MarkReadRequest,
    MessageView,
    MuteSurfaceRequest,
    SendMessageRequest,
    UnreadView,
    WsTicketRequest,
    WsTicketView,
} from './imTypes'

export function listImConversations(params: ListConversationsParams = {}) {
    const search = buildSearchParams(params)
    return requestJson<ConversationView[]>(`/api/im/conversations${suffix(search)}`)
}

export function sendImMessage(request: SendMessageRequest) {
    return requestJson<MessageView>('/api/im/messages', {
        method: 'POST',
        body: request,
    })
}

export function listImMessages(conversationId: number, params: ListMessagesParams = {}) {
    const search = buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
        beforeSeq: params.beforeSeq,
        afterSeq: params.afterSeq,
    })
    return requestJson<PageResult<MessageView>>(`/api/im/conversations/${conversationId}/messages${suffix(search)}`)
}

export function markImRead(conversationId: number, request: MarkReadRequest) {
    return requestJson<UnreadView>(`/api/im/conversations/${conversationId}/read`, {
        method: 'POST',
        body: request,
    })
}

export function createImChannel(request: CreateChannelRequest) {
    return requestJson<ChannelView>('/api/im/channels', {
        method: 'POST',
        body: request,
    })
}

export function listImChannels(params: ListChannelsParams) {
    const search = buildSearchParams(params)
    return requestJson<ChannelView[]>(`/api/im/channels${suffix(search)}`)
}

export function createImGroup(request: CreateGroupRequest) {
    return requestJson<GroupView>('/api/im/groups', {
        method: 'POST',
        body: request,
    })
}

export function listImGroups(params: ListGroupsParams = {}) {
    const search = buildSearchParams(params)
    return requestJson<GroupView[]>(`/api/im/groups${suffix(search)}`)
}

export function createImJoinRequest(request: JoinRequestCreateRequest) {
    return requestJson<JoinResultView>('/api/im/join-requests', {
        method: 'POST',
        body: request,
    })
}

export function approveImJoinRequest(id: number) {
    return requestJson<JoinResultView>(`/api/im/join-requests/${id}/approve`, {method: 'POST'})
}

export function rejectImJoinRequest(id: number, request?: JoinRequestRejectRequest) {
    return requestJson<JoinResultView>(`/api/im/join-requests/${id}/reject`, {
        method: 'POST',
        body: request,
    })
}

export function cancelImJoinRequest(id: number) {
    return requestJson<JoinResultView>(`/api/im/join-requests/${id}/cancel`, {method: 'POST'})
}

export function leaveImSurface(surfaceType: ImSurfaceType, surfaceId: number) {
    return requestJson<void>(`/api/im/surfaces/${surfaceType}/${surfaceId}/leave`, {method: 'POST'})
}

export function createImDm(request: CreateDmRequest) {
    return requestJson<ConversationView>('/api/im/dms', {
        method: 'POST',
        body: request,
    })
}

export function blockImDm(request: DmBlockRequest) {
    return requestJson<void>('/api/im/dms/block', {
        method: 'POST',
        body: request,
    })
}

export function unblockImDm(request: DmBlockRequest) {
    return requestJson<void>('/api/im/dms/unblock', {
        method: 'POST',
        body: request,
    })
}

export function muteImSurface(request: MuteSurfaceRequest) {
    return requestJson<void>('/api/im/governance/mute', {
        method: 'POST',
        body: request,
    })
}

export function muteImGlobal(request: GlobalMuteRequest) {
    return requestJson<void>('/api/im/governance/global-mute', {
        method: 'POST',
        body: request,
    })
}

export function setImAnnouncement(request: AnnouncementRequest) {
    return requestJson<void>('/api/im/governance/announcement', {
        method: 'POST',
        body: request,
    })
}

export function banImSurfaceUser(request: GovernanceSurfaceRequest) {
    return requestJson<void>('/api/im/governance/ban', {
        method: 'POST',
        body: request,
    })
}

export function createImWsTicket(request?: WsTicketRequest) {
    return requestJson<WsTicketView>('/api/im/ws-ticket', {
        method: 'POST',
        body: request,
    })
}

function suffix(search: string) {
    return search ? `?${search}` : ''
}
