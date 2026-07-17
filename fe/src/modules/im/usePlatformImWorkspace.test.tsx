import {act, renderHook, waitFor} from '@testing-library/react'
import {describe, expect, test, vi} from 'vitest'
import type {MarkReadRequest, MessageView, SendMessageRequest} from './imTypes'
import type {PlatformBootstrapView, PlatformConversationView} from './platformImTypes'
import {
    mergeMessages,
    type PlatformImWorkspaceApi,
    usePlatformImWorkspace,
} from './usePlatformImWorkspace'

describe('usePlatformImWorkspace', () => {
    test('bootstraps the first member conversation without assuming a default public channel', async () => {
        const api = createApi()
        api.listMessages = vi.fn().mockResolvedValue(page([message({messageSeq: 3})]))

        const {result} = renderHook(() => usePlatformImWorkspace({api, realtimeEnabled: false}))

        await waitFor(() => expect(result.current.status).toBe('ready'))
        await waitFor(() => expect(result.current.messages).toHaveLength(1))

        expect(result.current.selectedConversationId).toBe(10)
        expect(api.listMessages).toHaveBeenCalledWith(10, {
            page: 1,
            size: 50,
            afterSeq: undefined,
        })
        expect(api.markRead).toHaveBeenCalledWith(10, {messageSeq: 3})
    })

    test('falls back to REST and reuses the client message id when a failed message is retried', async () => {
        const api = createApi()
        api.listMessages = vi.fn().mockResolvedValue(page([]))
        api.sendMessage = vi.fn()
            .mockRejectedValueOnce(new Error('offline'))
            .mockImplementation((request: SendMessageRequest) => Promise.resolve(message({
                id: 91,
                messageSeq: 2,
                clientMsgId: request.clientMsgId,
                content: request.content,
            })))
        const {result} = renderHook(() => usePlatformImWorkspace({api, realtimeEnabled: false}))
        await waitFor(() => expect(result.current.status).toBe('ready'))

        await act(async () => {
            await result.current.sendText(' Hello ')
        })

        expect(result.current.messages).toHaveLength(1)
        expect(result.current.messages[0]).toMatchObject({content: 'Hello', status: 'FAILED', optimistic: true})
        const clientMsgId = result.current.messages[0].clientMsgId as string

        await act(async () => {
            await result.current.retryMessage(10, clientMsgId)
        })

        expect(api.sendMessage).toHaveBeenCalledTimes(2)
        expect(api.sendMessage).toHaveBeenNthCalledWith(2, expect.objectContaining({clientMsgId}))
        expect(result.current.messages[0]).toMatchObject({id: 91, clientMsgId, status: 'VISIBLE', optimistic: false})
    })

    test('resyncs from the locally reconciled sequence before advancing the read cursor', async () => {
        const api = createApi()
        api.listMessages = vi.fn()
            .mockResolvedValueOnce(page([message({messageSeq: 2, clientMsgId: 'server-2'})]))
            .mockResolvedValueOnce(page([message({id: 92, messageSeq: 3, clientMsgId: 'server-3'})]))
        api.listConversations = vi.fn().mockResolvedValue([conversation({messageSeq: 3})])
        const {result} = renderHook(() => usePlatformImWorkspace({api, realtimeEnabled: false}))
        await waitFor(() => expect(result.current.messages[0]?.messageSeq).toBe(2))

        await act(async () => {
            await result.current.handleResync({reason: 'OUTBOUND_OVERFLOW', conversationId: 10, messageSeq: 3})
        })

        expect(api.listMessages).toHaveBeenNthCalledWith(2, 10, {
            page: 1,
            size: 50,
            afterSeq: 2,
        })
        expect(result.current.messages.map((item) => item.messageSeq)).toEqual([2, 3])
        expect(api.markRead).toHaveBeenLastCalledWith(10, {messageSeq: 3})
    })
})

describe('mergeMessages', () => {
    test('replaces an optimistic message with its server acknowledgement and removes duplicate pushes', () => {
        const optimistic = message({
            id: -1,
            messageSeq: 2,
            clientMsgId: 'client-1',
            status: 'PENDING',
            optimistic: true,
        })
        const acknowledged = message({id: 22, messageSeq: 2, clientMsgId: 'client-1'})

        const merged = mergeMessages([message({messageSeq: 1}), optimistic], [acknowledged, acknowledged])

        expect(merged).toHaveLength(2)
        expect(merged[1]).toMatchObject({id: 22, clientMsgId: 'client-1', optimistic: false})
    })
})

function createApi(): PlatformImWorkspaceApi {
    return {
        getBootstrap: vi.fn().mockResolvedValue(bootstrap()),
        listConversations: vi.fn().mockResolvedValue([conversation()]),
        listMessages: vi.fn().mockResolvedValue(page([])),
        markRead: vi.fn().mockImplementation((conversationId: number, request: MarkReadRequest) => Promise.resolve({
            conversationId,
            userId: 1,
            lastReadSeq: request.messageSeq,
            unreadCount: 0,
        })),
        sendMessage: vi.fn(),
        openDm: vi.fn(),
        searchUsers: vi.fn().mockResolvedValue(page([])),
        listFriends: vi.fn().mockResolvedValue(page([])),
        listFriendRequests: vi.fn().mockResolvedValue(page([])),
        createFriendRequest: vi.fn(),
        acceptFriendRequest: vi.fn(),
        rejectFriendRequest: vi.fn(),
        cancelFriendRequest: vi.fn(),
        updateFriendRemark: vi.fn(),
        removeFriend: vi.fn(),
        createChannel: vi.fn(),
        listChannels: vi.fn().mockResolvedValue([]),
        createGroup: vi.fn(),
        listGroups: vi.fn().mockResolvedValue([]),
        createChannelGroup: vi.fn(),
        listChannelGroups: vi.fn().mockResolvedValue([]),
        listInvitations: vi.fn().mockResolvedValue(page([])),
        inviteSurface: vi.fn(),
        acceptInvitation: vi.fn(),
        rejectInvitation: vi.fn(),
        transferOwnership: vi.fn(),
        applyJoin: vi.fn(),
        approveJoin: vi.fn(),
        rejectJoin: vi.fn(),
        cancelJoin: vi.fn(),
        leaveSurface: vi.fn(),
        listSurfaceMembers: vi.fn().mockResolvedValue(page([])),
        listJoinRequests: vi.fn().mockResolvedValue(page([])),
        removeSurfaceMember: vi.fn(),
    }
}

function bootstrap(): PlatformBootstrapView {
    const channel = conversation()
    return {
        currentUser: {userId: 1, accountNo: 'mario', displayName: 'Mario'},
        conversations: [channel],
        unreadTotal: 1,
        pendingFriendRequestCount: 0,
    }
}

function conversation(overrides: Partial<PlatformConversationView> = {}): PlatformConversationView {
    return {
        conversationId: 10,
        conversationType: 'CHANNEL',
        displayType: 'CHANNEL',
        title: 'Product',
        ownerSurfaceType: 'CHANNEL',
        surfaceId: 2,
        surfaceKey: 'product',
        membershipStatus: 'ACTIVE',
        memberRole: 'MEMBER',
        canRead: true,
        canPost: true,
        messageSeq: 3,
        status: 'ACTIVE',
        unreadCount: 1,
        ...overrides,
    }
}

function message(overrides: Partial<MessageView & {optimistic: boolean}> = {}) {
    return {
        id: 21,
        conversationId: 10,
        senderUserId: 1,
        messageSeq: 1,
        clientMsgId: 'server-1',
        messageType: 'TEXT',
        content: 'Hello',
        status: 'VISIBLE',
        sentAt: '2026-07-16T12:00:00Z',
        ...overrides,
    }
}

function page<T>(records: T[]) {
    return {records, page: 1, size: 50, total: records.length, totalPages: records.length ? 1 : 0}
}
