import {describe, expect, test} from 'vitest'
import {
    mapImConversationToWorkspaceConversation,
    mapImMessageToWorkspaceMessage,
    mapImMessagesToWorkspaceMessages,
} from './imMappers'
import type {ConversationView, MessageView} from './imTypes'

const message: MessageView = {
    id: 10,
    conversationId: 2,
    senderUserId: 99,
    messageSeq: 5,
    clientMsgId: 'client-1',
    messageType: 'TEXT',
    content: 'Hello team',
    payloadJson: null,
    status: 'SENT',
    sentAt: '2026-06-28T10:00:00Z',
    editedAt: null,
    deletedAt: null,
    metadataJson: '{"role":"sender"}',
}

const conversation: ConversationView = {
    id: 2,
    conversationType: 'GROUP',
    ownerSurfaceType: 'GROUP',
    ownerSurfaceId: 7,
    contextType: 'clocktower',
    contextId: 101,
    messageSeq: 5,
    lastMessageId: 10,
    lastMessageAt: '2026-06-28T10:00:00Z',
    lastMessage: message,
    lastActiveAt: '2026-06-28T10:01:00Z',
    status: 'ACTIVE',
    unreadCount: 3,
}

describe('imMappers', () => {
    test('maps IM conversations to shared chat workspace conversation entries', () => {
        expect(mapImConversationToWorkspaceConversation(conversation)).toEqual({
            key: 'im-conversation-2',
            label: 'GROUP #7',
            description: 'Hello team',
            group: 'GROUP',
            updatedAt: '2026-06-28T10:01:00Z',
            imConversation: conversation,
            unreadCount: 3,
        })
    })

    test('maps current user IM messages into workspace user messages with stable metadata', () => {
        expect(mapImMessageToWorkspaceMessage(message, {currentUserId: 99})).toEqual({
            id: 'im-message-10',
            role: 'user',
            content: 'Hello team',
            status: 'success',
            messageId: '10',
            conversationId: 2,
            senderUserId: 99,
            messageSeq: 5,
            clientMsgId: 'client-1',
            messageType: 'TEXT',
            payloadJson: null,
            metadataJson: '{"role":"sender"}',
            sentAt: '2026-06-28T10:00:00Z',
            imMessage: message,
        })
    })

    test('maps other sender IM messages into workspace assistant messages', () => {
        expect(mapImMessageToWorkspaceMessage(message, {currentUserId: 12})).toMatchObject({
            id: 'im-message-10',
            role: 'assistant',
            content: 'Hello team',
            senderUserId: 99,
        })
    })

    test('maps system IM messages into workspace system messages', () => {
        expect(mapImMessageToWorkspaceMessage({
            ...message,
            id: 12,
            senderUserId: null,
            messageType: 'SYSTEM',
            content: 'System notice',
        }, {currentUserId: 99})).toMatchObject({
            id: 'im-message-12',
            role: 'system',
            content: 'System notice',
            senderUserId: null,
        })
    })

    test('marks deleted messages as system notices and sorts history by sequence', () => {
        const deletedMessage: MessageView = {
            ...message,
            id: 11,
            messageSeq: 4,
            content: null,
            deletedAt: '2026-06-28T10:05:00Z',
        }

        expect(mapImMessagesToWorkspaceMessages([message, deletedMessage], {currentUserId: 12})).toMatchObject([
            {
                id: 'im-message-11',
                role: 'system',
                content: 'Message deleted',
                status: 'success',
            },
            {
                id: 'im-message-10',
                role: 'assistant',
                content: 'Hello team',
                status: 'success',
            },
        ])
    })
})
