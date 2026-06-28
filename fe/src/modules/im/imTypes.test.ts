import {describe, expect, test} from 'vitest'
import type {PageResult} from '../../types/api'
import type {
    ChannelView,
    ConversationView,
    CreateChannelRequest,
    CreateGroupRequest,
    GlobalMuteRequest,
    GroupView,
    ImClientFrame,
    ImClientFrameFor,
    ImClientFrameType,
    ImJoinRequestStatus,
    ImJoinResultStatus,
    ImJoinPolicy,
    ImMemberRole,
    ImMarkReadPayload,
    ImMembershipStatus,
    ImMessagePushPayload,
    ImResyncPayload,
    ImServerFrame,
    ImServerFrameType,
    ImSurfaceType,
    ListChannelsParams,
    ListConversationsParams,
    ListGroupsParams,
    MessageView,
    SendMessageRequest,
    UnreadView,
    WsTicketView,
    JoinResultView,
} from './imTypes'

type IsExact<Type, Expected> =
    (<Value>() => Value extends Type ? 1 : 2) extends
    (<Value>() => Value extends Expected ? 1 : 2)
        ? (<Value>() => Value extends Expected ? 1 : 2) extends
        (<Value>() => Value extends Type ? 1 : 2)
            ? true
            : false
        : false
type IsOptional<Type, Key extends keyof Type> = Record<string, never> extends Pick<Type, Key> ? true : false

const imTypeContract: {
    channelIdIsNumber: IsExact<ChannelView['id'], number>
    channelContextIdAllowsNullNumber: IsExact<ChannelView['contextId'], number | null>
    groupContextIdAllowsNullNumber: IsExact<GroupView['contextId'], number | null>
    conversationContextIdAllowsNullNumber: IsExact<ConversationView['contextId'], number | null | undefined>
    listConversationContextIdAllowsNullNumber: IsExact<ListConversationsParams['contextId'], number | null | undefined>
    listChannelContextIdAllowsNullNumber: IsExact<ListChannelsParams['contextId'], number | null | undefined>
    listGroupContextIdAllowsNullNumber: IsExact<ListGroupsParams['contextId'], number | null | undefined>
    createChannelContextIdAllowsNullNumber: IsExact<CreateChannelRequest['contextId'], number | null | undefined>
    createGroupContextIdAllowsNullNumber: IsExact<CreateGroupRequest['contextId'], number | null | undefined>
    globalMuteScopeIdAllowsNullNumber: IsExact<GlobalMuteRequest['scopeId'], number | null | undefined>
    lastActiveAtAllowsNull: IsExact<ChannelView['lastActiveAt'], string | null | undefined>
    conversationLastMessageAllowsNull: IsExact<ConversationView['lastMessage'], MessageView | null | undefined>
    messageSeqIsNumber: IsExact<MessageView['messageSeq'], number>
    wsTicketExpiresAtIsString: IsExact<WsTicketView['expiresAt'], string>
    pageResultUsesMessageView: IsExact<PageResult<MessageView>['records'][number], MessageView>
    metadataJsonRequestOptional: IsOptional<CreateChannelRequest, 'metadataJson'>
    createChannelContextIdOptional: IsOptional<CreateChannelRequest, 'contextId'>
    createGroupContextIdOptional: IsOptional<CreateGroupRequest, 'contextId'>
    globalMuteScopeIdOptional: IsOptional<GlobalMuteRequest, 'scopeId'>
    listChannelContextTypeRequired: IsOptional<ListChannelsParams, 'contextType'> extends false ? true : false
    listChannelContextTypeIsString: IsExact<ListChannelsParams['contextType'], string>
    surfaceTypes: IsExact<ImSurfaceType, 'CHANNEL' | 'GROUP'>
    joinPolicies: IsExact<ImJoinPolicy, 'OPEN' | 'APPROVAL' | 'INVITE_ONLY'>
    membershipStatuses: IsExact<ImMembershipStatus, 'ACTIVE' | 'PENDING' | 'LEFT' | 'BANNED'>
    joinRequestStatuses: IsExact<ImJoinRequestStatus, 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'>
    joinResultStatuses: IsExact<ImJoinResultStatus, 'PENDING' | 'ACTIVE' | 'REJECTED' | 'CANCELLED'>
    memberRoleRemainsBackendOpenString: IsExact<ImMemberRole, string>
    conversationOwnerSurfaceTypeRemainsBackendOpenString: IsExact<NonNullable<ConversationView['ownerSurfaceType']>, string>
    channelMembershipStatusUsesBackendMembership: IsExact<NonNullable<ChannelView['membershipStatus']>, ImMembershipStatus>
    groupMembershipStatusUsesBackendMembership: IsExact<NonNullable<GroupView['membershipStatus']>, ImMembershipStatus>
    joinResultStatusUsesBackendResult: IsExact<JoinResultView['status'], ImJoinResultStatus>
    clientFrameTypes: IsExact<ImClientFrameType, 'PING' | 'SUBSCRIBE' | 'SEND_MESSAGE' | 'MARK_READ'>
    serverFrameTypes: IsExact<ImServerFrameType, 'PONG' | 'SEND_ACK' | 'READ_UPDATED' | 'MESSAGE_PUSH' | 'RESYNC'>
    markReadPayloadUsesNumbers: IsExact<ImMarkReadPayload, {conversationId: number; messageSeq: number}>
    messagePushPayloadRequiresEventType: IsExact<ImMessagePushPayload['eventType'], string>
    resyncPayloadReasonIsString: IsExact<ImResyncPayload['reason'], string>
    sendMessageFrameUsesRequestPayload: IsExact<ImClientFrameFor<'SEND_MESSAGE'>['payload'], SendMessageRequest>
} = {
    channelIdIsNumber: true,
    channelContextIdAllowsNullNumber: true,
    groupContextIdAllowsNullNumber: true,
    conversationContextIdAllowsNullNumber: true,
    listConversationContextIdAllowsNullNumber: true,
    listChannelContextIdAllowsNullNumber: true,
    listGroupContextIdAllowsNullNumber: true,
    createChannelContextIdAllowsNullNumber: true,
    createGroupContextIdAllowsNullNumber: true,
    globalMuteScopeIdAllowsNullNumber: true,
    lastActiveAtAllowsNull: true,
    conversationLastMessageAllowsNull: true,
    messageSeqIsNumber: true,
    wsTicketExpiresAtIsString: true,
    pageResultUsesMessageView: true,
    metadataJsonRequestOptional: true,
    createChannelContextIdOptional: true,
    createGroupContextIdOptional: true,
    globalMuteScopeIdOptional: true,
    listChannelContextTypeRequired: true,
    listChannelContextTypeIsString: true,
    surfaceTypes: true,
    joinPolicies: true,
    membershipStatuses: true,
    joinRequestStatuses: true,
    joinResultStatuses: true,
    memberRoleRemainsBackendOpenString: true,
    conversationOwnerSurfaceTypeRemainsBackendOpenString: true,
    channelMembershipStatusUsesBackendMembership: true,
    groupMembershipStatusUsesBackendMembership: true,
    joinResultStatusUsesBackendResult: true,
    clientFrameTypes: true,
    serverFrameTypes: true,
    markReadPayloadUsesNumbers: true,
    messagePushPayloadRequiresEventType: true,
    resyncPayloadReasonIsString: true,
    sendMessageFrameUsesRequestPayload: true,
}

const messageView: MessageView = {
    id: 10,
    conversationId: 42,
    senderUserId: 99,
    messageSeq: 9,
    clientMsgId: 'client-1',
    messageType: 'TEXT',
    content: 'hello',
    payloadJson: null,
    status: 'VISIBLE',
    sentAt: '2026-06-28T10:00:00Z',
    editedAt: null,
    deletedAt: null,
    metadataJson: null,
}

const unreadView: UnreadView = {
    conversationId: 42,
    userId: 99,
    lastReadSeq: 9,
    unreadCount: 0,
}

const clientFrames: ImClientFrame[] = [
    {type: 'PING', requestId: 'r-ping', payload: {}},
    {type: 'SUBSCRIBE', requestId: 'r-subscribe', payload: {conversationId: 42, lastSeq: 8}},
    {
        type: 'SEND_MESSAGE',
        requestId: 'r-send',
        payload: {conversationId: 42, clientMsgId: 'client-1', messageType: 'TEXT', content: 'hello'},
    },
    {type: 'MARK_READ', requestId: 'r-read', payload: {conversationId: 42, messageSeq: 9}},
]

const serverFrames: ImServerFrame[] = [
    {type: 'PONG', requestId: 'r-ping', payload: {time: '2026-06-28T10:00:00Z'}},
    {type: 'SEND_ACK', requestId: 'r-send', payload: {message: messageView}},
    {type: 'READ_UPDATED', requestId: 'r-read', payload: {unread: unreadView}},
    {type: 'MESSAGE_PUSH', payload: {eventType: 'MESSAGE_CREATED', conversationId: 42, messageSeq: 9, message: messageView}},
    {type: 'RESYNC', payload: {reason: 'OUTBOUND_OVERFLOW', conversationId: 42, messageSeq: 9}},
]

describe('imTypes', () => {
    test('exposes the backend DTO contract with frontend null safety', () => {
        expect(imTypeContract).toEqual({
            channelIdIsNumber: true,
            channelContextIdAllowsNullNumber: true,
            groupContextIdAllowsNullNumber: true,
            conversationContextIdAllowsNullNumber: true,
            listConversationContextIdAllowsNullNumber: true,
            listChannelContextIdAllowsNullNumber: true,
            listGroupContextIdAllowsNullNumber: true,
            createChannelContextIdAllowsNullNumber: true,
            createGroupContextIdAllowsNullNumber: true,
            globalMuteScopeIdAllowsNullNumber: true,
            lastActiveAtAllowsNull: true,
            conversationLastMessageAllowsNull: true,
            messageSeqIsNumber: true,
            wsTicketExpiresAtIsString: true,
            pageResultUsesMessageView: true,
            metadataJsonRequestOptional: true,
            createChannelContextIdOptional: true,
            createGroupContextIdOptional: true,
            globalMuteScopeIdOptional: true,
            listChannelContextTypeRequired: true,
            listChannelContextTypeIsString: true,
            surfaceTypes: true,
            joinPolicies: true,
            membershipStatuses: true,
            joinRequestStatuses: true,
            joinResultStatuses: true,
            memberRoleRemainsBackendOpenString: true,
            conversationOwnerSurfaceTypeRemainsBackendOpenString: true,
            channelMembershipStatusUsesBackendMembership: true,
            groupMembershipStatusUsesBackendMembership: true,
            joinResultStatusUsesBackendResult: true,
            clientFrameTypes: true,
            serverFrameTypes: true,
            markReadPayloadUsesNumbers: true,
            messagePushPayloadRequiresEventType: true,
            resyncPayloadReasonIsString: true,
            sendMessageFrameUsesRequestPayload: true,
        })
        expect(clientFrames.map((frame) => frame.type)).toEqual(['PING', 'SUBSCRIBE', 'SEND_MESSAGE', 'MARK_READ'])
        expect(serverFrames.map((frame) => frame.type)).toEqual(['PONG', 'SEND_ACK', 'READ_UPDATED', 'MESSAGE_PUSH', 'RESYNC'])
    })
})
