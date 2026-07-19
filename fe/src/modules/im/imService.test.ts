import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    approveImJoinRequest,
    blockImDm,
    cancelImJoinRequest,
    createImChannel,
    createImDm,
    createImGroup,
    createImJoinRequest,
    createImWsTicket,
    leaveImSurface,
    listImChannels,
    listImConversations,
    listImGroups,
    listImMessages,
    markImRead,
    banImSurfaceUser,
    muteImGlobal,
    muteImSurface,
    rejectImJoinRequest,
    sendImMessage,
    setImAnnouncement,
    unblockImDm,
} from './imService'

vi.mock('../../services/request', () => ({
    requestJson: vi.fn(),
}))

describe('imService', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    test('builds encoded query strings for conversation and surface discovery', async () => {
        const {requestJson} = await import('../../services/request')

        void listImConversations({contextType: 'clocktower room', contextId: 101})
        void listImChannels({contextType: 'clocktower', contextId: 101})
        void listImGroups({channelId: 12, contextType: 'clocktower', contextId: 101})
        void listImMessages(9, {page: 2, size: 30, beforeSeq: 88, afterSeq: 42})

        expect(requestJson).toHaveBeenNthCalledWith(
            1,
            '/api/im/conversations?contextType=clocktower+room&contextId=101',
        )
        expect(requestJson).toHaveBeenNthCalledWith(
            2,
            '/api/im/channels?contextType=clocktower&contextId=101',
        )
        expect(requestJson).toHaveBeenNthCalledWith(
            3,
            '/api/im/groups?channelId=12&contextType=clocktower&contextId=101',
        )
        expect(requestJson).toHaveBeenNthCalledWith(
            4,
            '/api/im/conversations/9/messages?page=2&size=30&beforeSeq=88&afterSeq=42',
        )
    })

    test('posts message, read, channel, group, and join payloads to the backend contract', async () => {
        const {requestJson} = await import('../../services/request')

        void sendImMessage({
            conversationId: 9,
            clientMsgId: 'client-1',
            messageType: 'TEXT',
            content: 'hello',
            payloadJson: '{"kind":"plain"}',
            metadataJson: '{"source":"test"}',
        })
        void markImRead(9, {messageSeq: 12})
        void createImChannel({
            contextType: 'clocktower',
            contextId: 101,
            channelKey: 'town-square',
            name: 'Town Square',
            joinPolicy: 'OPEN',
            metadataJson: '{}',
        })
        void createImGroup({
            channelId: 3,
            contextType: 'clocktower',
            contextId: 101,
            groupKey: 'storytellers',
            name: 'Storytellers',
            joinPolicy: 'APPROVAL',
        })
        void createImJoinRequest({joinKey: 'grp_storytellers000000000', reason: 'Need access'})

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/im/messages', {
            method: 'POST',
            body: {
                conversationId: 9,
                clientMsgId: 'client-1',
                messageType: 'TEXT',
                content: 'hello',
                payloadJson: '{"kind":"plain"}',
                metadataJson: '{"source":"test"}',
            },
        })
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/im/conversations/9/read', {
            method: 'POST',
            body: {messageSeq: 12},
        })
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/im/channels', {
            method: 'POST',
            body: {
                contextType: 'clocktower',
                contextId: 101,
                channelKey: 'town-square',
                name: 'Town Square',
                joinPolicy: 'OPEN',
                metadataJson: '{}',
            },
        })
        expect(requestJson).toHaveBeenNthCalledWith(4, '/api/im/groups', {
            method: 'POST',
            body: {
                channelId: 3,
                contextType: 'clocktower',
                contextId: 101,
                groupKey: 'storytellers',
                name: 'Storytellers',
                joinPolicy: 'APPROVAL',
            },
        })
        expect(requestJson).toHaveBeenNthCalledWith(5, '/api/im/join-requests', {
            method: 'POST',
            body: {joinKey: 'grp_storytellers000000000', reason: 'Need access'},
        })
    })

    test('posts membership, DM, and governance actions to exact endpoints', async () => {
        const {requestJson} = await import('../../services/request')

        void approveImJoinRequest(7)
        void rejectImJoinRequest(8, {reason: 'Full'})
        void cancelImJoinRequest(9)
        void leaveImSurface('CHANNEL', 2)
        void createImDm({targetUserId: 88})
        void blockImDm({targetUserId: 88, reason: 'spam'})
        void unblockImDm({targetUserId: 88})
        void muteImSurface({surfaceType: 'GROUP', surfaceId: 4, userId: 77, mutedUntil: '2026-07-01T00:00:00Z'})
        void muteImGlobal({
            scopeType: 'clocktower',
            scopeId: 101,
            userId: 77,
            mutedUntil: '2026-07-02T00:00:00Z',
            reason: 'quiet period',
        })
        void setImAnnouncement({surfaceType: 'CHANNEL', surfaceId: 2, announcement: 'Tonight at 8'})
        void banImSurfaceUser({surfaceType: 'GROUP', surfaceId: 4, userId: 77, reason: 'Appeal accepted'})
        void createImWsTicket({conversationId: 9})

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/im/join-requests/7/approve', {method: 'POST'})
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/im/join-requests/8/reject', {
            method: 'POST',
            body: {reason: 'Full'},
        })
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/im/join-requests/9/cancel', {method: 'POST'})
        expect(requestJson).toHaveBeenNthCalledWith(4, '/api/im/surfaces/CHANNEL/2/leave', {method: 'POST'})
        expect(requestJson).toHaveBeenNthCalledWith(5, '/api/im/dms', {
            method: 'POST',
            body: {targetUserId: 88},
        })
        expect(requestJson).toHaveBeenNthCalledWith(6, '/api/im/dms/block', {
            method: 'POST',
            body: {targetUserId: 88, reason: 'spam'},
        })
        expect(requestJson).toHaveBeenNthCalledWith(7, '/api/im/dms/unblock', {
            method: 'POST',
            body: {targetUserId: 88},
        })
        expect(requestJson).toHaveBeenNthCalledWith(8, '/api/im/governance/mute', {
            method: 'POST',
            body: {surfaceType: 'GROUP', surfaceId: 4, userId: 77, mutedUntil: '2026-07-01T00:00:00Z'},
        })
        expect(requestJson).toHaveBeenNthCalledWith(9, '/api/im/governance/global-mute', {
            method: 'POST',
            body: {
                scopeType: 'clocktower',
                scopeId: 101,
                userId: 77,
                mutedUntil: '2026-07-02T00:00:00Z',
                reason: 'quiet period',
            },
        })
        expect(requestJson).toHaveBeenNthCalledWith(10, '/api/im/governance/announcement', {
            method: 'POST',
            body: {surfaceType: 'CHANNEL', surfaceId: 2, announcement: 'Tonight at 8'},
        })
        expect(requestJson).toHaveBeenNthCalledWith(11, '/api/im/governance/ban', {
            method: 'POST',
            body: {surfaceType: 'GROUP', surfaceId: 4, userId: 77, reason: 'Appeal accepted'},
        })
        expect(requestJson).toHaveBeenNthCalledWith(12, '/api/im/ws-ticket', {
            method: 'POST',
            body: {conversationId: 9},
        })
    })
})
