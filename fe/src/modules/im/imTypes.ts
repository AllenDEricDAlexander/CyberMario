import type {PageResult} from '../../types/api'

export type ImSurfaceType = 'CHANNEL' | 'GROUP'
export type ImJoinPolicy = 'OPEN' | 'APPROVAL'
export type ImMembershipStatus = 'ACTIVE' | 'PENDING' | 'LEFT' | 'BANNED'
export type ImJoinRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
export type ImJoinResultStatus = 'PENDING' | 'ACTIVE' | 'REJECTED' | 'CANCELLED'
export type ImMemberRole = string

export type ChannelView = {
    id: number
    contextType: string
    contextId: number | null
    channelKey: string
    joinKey: string
    name: string
    ownerUserId: number
    visibility?: string | null
    joinPolicy: ImJoinPolicy
    status: string
    announcement?: string | null
    mainConversationId?: number | null
    memberCount?: number | null
    lastActiveAt: string | null | undefined
    membershipStatus?: ImMembershipStatus | null
    memberRole?: ImMemberRole | null
    canRead?: boolean | null
    canPost?: boolean | null
    unreadCount?: number | null
}

export type GroupView = {
    id: number
    channelId?: number | null
    contextType: string
    contextId: number | null
    groupKey: string
    joinKey: string
    name: string
    ownerUserId: number
    joinPolicy: ImJoinPolicy
    status: string
    announcement?: string | null
    conversationId?: number | null
    memberCount?: number | null
    lastActiveAt?: string | null
    membershipStatus?: ImMembershipStatus | null
    memberRole?: ImMemberRole | null
    canRead?: boolean | null
    canPost?: boolean | null
    unreadCount?: number | null
}

export type ConversationView = {
    id: number
    conversationType: string
    ownerSurfaceType?: string | null
    ownerSurfaceId?: number | null
    contextType?: string | null
    contextId?: number | null
    messageSeq: number
    lastMessageId?: number | null
    lastMessageAt?: string | null
    lastMessage: MessageView | null | undefined
    lastActiveAt?: string | null
    status: string
    unreadCount?: number | null
}

export type MessageView = {
    id: number
    conversationId: number
    senderUserId?: number | null
    messageSeq: number
    clientMsgId?: string | null
    messageType: string
    content?: string | null
    payloadJson?: string | null
    status: string
    sentAt?: string | null
    editedAt?: string | null
    deletedAt?: string | null
    metadataJson?: string | null
}

export type UnreadView = {
    conversationId: number
    userId: number
    lastReadSeq: number
    unreadCount: number
}

export type JoinResultView = {
    status: ImJoinResultStatus
    surfaceType: ImSurfaceType
    surfaceId: number
    membershipId?: number | null
    joinRequestId?: number | null
}

export type WsTicketView = {
    ticket: string
    expiresAt: string
}

export type ListConversationsParams = {
    contextType?: string
    contextId?: number | null
}

export type ListMessagesParams = {
    page?: number
    size?: number
    beforeSeq?: number
    afterSeq?: number
}

export type SendMessageRequest = {
    conversationId: number
    clientMsgId: string
    messageType: string
    content?: string | null
    payloadJson?: string | null
    metadataJson?: string | null
}

export type MarkReadRequest = {
    messageSeq: number
}

export type CreateChannelRequest = {
    contextType: string
    contextId?: number | null
    channelKey: string
    name: string
    joinPolicy: ImJoinPolicy
    metadataJson?: string | null
}

export type ListChannelsParams = {
    contextType: string
    contextId?: number | null
}

export type CreateGroupRequest = {
    channelId?: number | null
    contextType: string
    contextId?: number | null
    groupKey: string
    name: string
    joinPolicy: ImJoinPolicy
    metadataJson?: string | null
}

export type ListGroupsParams = {
    channelId?: number
    contextType?: string
    contextId?: number | null
}

export type JoinRequestCreateRequest = {
    joinKey: string
    reason?: string | null
}

export type JoinRequestRejectRequest = {
    reason?: string | null
}

export type CreateDmRequest = {
    targetUserId: number
}

export type DmBlockRequest = {
    targetUserId: number
    reason?: string | null
}

export type GovernanceSurfaceRequest = {
    surfaceType: ImSurfaceType
    surfaceId: number
    userId: number
    reason?: string | null
}

export type MuteSurfaceRequest = GovernanceSurfaceRequest & {
    mutedUntil: string
}

export type GlobalMuteRequest = {
    scopeType: string
    scopeId?: number | null
    userId: number
    mutedUntil: string
    reason?: string | null
}

export type AnnouncementRequest = {
    surfaceType: ImSurfaceType
    surfaceId: number
    announcement: string
}

export type WsTicketRequest = {
    conversationId?: number
}

export type MessagePage = PageResult<MessageView>

export type ImPingPayload = Record<string, never>

export type ImSubscribePayload = {
    conversationId?: number | null
    lastSeq?: number | null
}

export type ImSendMessagePayload = SendMessageRequest

export type ImMarkReadPayload = {
    conversationId: number
    messageSeq: number
}

export type ImPongPayload = {
    time: string
}

export type ImSendAckPayload = {
    message: MessageView
}

export type ImReadUpdatedPayload = {
    unread: UnreadView
}

export type ImMessagePushPayload = {
    eventType: string
    conversationId?: number | null
    messageId?: number | null
    messageSeq?: number | null
    message?: MessageView
    unread?: UnreadView
    [key: string]: unknown
}

export type ImResyncPayload = {
    reason: string
    conversationId?: number | null
    messageSeq?: number | null
}

type ImClientFrameBase<Type extends string, Payload> = {
    type: Type
    requestId: string
    payload: Payload
}

export type ImClientPingFrame = ImClientFrameBase<'PING', ImPingPayload>
export type ImClientSubscribeFrame = ImClientFrameBase<'SUBSCRIBE', ImSubscribePayload>
export type ImClientSendMessageFrame = ImClientFrameBase<'SEND_MESSAGE', ImSendMessagePayload>
export type ImClientMarkReadFrame = ImClientFrameBase<'MARK_READ', ImMarkReadPayload>

export type ImClientFrame =
    | ImClientPingFrame
    | ImClientSubscribeFrame
    | ImClientSendMessageFrame
    | ImClientMarkReadFrame

export type ImClientFrameType = ImClientFrame['type']
export type ImClientFrameFor<Type extends ImClientFrameType> = Extract<ImClientFrame, {type: Type}>

type ImServerFrameBase<Type extends string, Payload> = {
    type: Type
    requestId?: string | null
    payload: Payload
}

export type ImServerPongFrame = ImServerFrameBase<'PONG', ImPongPayload>
export type ImServerSendAckFrame = ImServerFrameBase<'SEND_ACK', ImSendAckPayload>
export type ImServerReadUpdatedFrame = ImServerFrameBase<'READ_UPDATED', ImReadUpdatedPayload>
export type ImServerMessagePushFrame = ImServerFrameBase<'MESSAGE_PUSH', ImMessagePushPayload>
export type ImServerResyncFrame = ImServerFrameBase<'RESYNC', ImResyncPayload>

export type ImServerFrame =
    | ImServerPongFrame
    | ImServerSendAckFrame
    | ImServerReadUpdatedFrame
    | ImServerMessagePushFrame
    | ImServerResyncFrame

export type ImServerFrameType = ImServerFrame['type']

export type ImServerFrameInput = ImServerFrame | {
    type?: string | null
    requestId?: string | null
    payload?: Record<string, unknown> | null
}
