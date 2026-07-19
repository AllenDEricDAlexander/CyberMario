import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {
    appendClocktowerSentMessage,
    buildClocktowerContextIds,
    clocktowerChatMessagePage,
    ClocktowerChatPanel,
    filterClocktowerConversations,
    filterDiscoverableClocktowerSurfaces,
    markClocktowerPendingMessageFailed,
    mergeClocktowerAckMessage,
    mergeClocktowerConversationRecords,
    mergeClocktowerPushedMessage,
    resolveClocktowerConfirmedMessageSeq,
    resolveClocktowerMessagePushAction,
    resolveClocktowerChatPolicy,
    shouldMarkClocktowerChatRead,
} from './ClocktowerChatPanel'
import {ClocktowerConversationList} from './ClocktowerConversationList'
import {ClocktowerMessageList} from './ClocktowerMessageList'
import type {ClocktowerConversationResponse, ClocktowerMessageResponse} from '../clocktowerTypes'
import type {ChannelView, GroupView} from '../../im/imTypes'
import type {ImSocketOptions} from '../../im/useImSocket'

const imSocketMock = vi.hoisted(() => ({
    callbacks: [] as ImSocketOptions[],
    controller: {
        connect: vi.fn(),
        disconnect: vi.fn(),
        markRead: vi.fn(() => false),
        reconnect: vi.fn(),
        sendMessage: vi.fn(() => false),
    },
}))

vi.mock('../clocktowerService', async (importOriginal) => {
    const actual = await importOriginal<typeof import('../clocktowerService')>()
    return {
        ...actual,
        listClocktowerChatMessages: vi.fn().mockResolvedValue({items: [], page: 1, size: 20, total: 0}),
        markClocktowerChatRead: vi.fn(),
        sendClocktowerChatMessage: vi.fn(),
    }
})

vi.mock('../../im/imService', () => ({
    approveImJoinRequest: vi.fn(),
    blockImDm: vi.fn(),
    cancelImJoinRequest: vi.fn(),
    createImDm: vi.fn(),
    createImJoinRequest: vi.fn(),
    createImWsTicket: vi.fn(),
    leaveImSurface: vi.fn(),
    listImChannels: vi.fn().mockResolvedValue([]),
    listImConversations: vi.fn().mockResolvedValue([]),
    listImGroups: vi.fn().mockResolvedValue([]),
    listImMessages: vi.fn().mockResolvedValue({records: [], page: 1, size: 20, total: 0, totalPages: 0}),
    markImRead: vi.fn(),
    muteImSurface: vi.fn(),
    rejectImJoinRequest: vi.fn(),
    sendImMessage: vi.fn(),
    setImAnnouncement: vi.fn(),
    unblockImDm: vi.fn(),
}))

vi.mock('../../im/useImSocket', () => ({
    useImSocket: (options: ImSocketOptions) => {
        imSocketMock.callbacks.push(options)
        return imSocketMock.controller
    },
}))

const conversations: ClocktowerConversationResponse[] = [
    {
        conversationId: 201,
        roomId: 7,
        gameId: 11,
        channelKey: 'PUBLIC',
        groupKey: 'PUBLIC',
        conversationType: 'GROUP',
        messageSeq: 4,
        unreadCount: 3,
        lastMessage: {
            id: 10,
            conversationId: 201,
            messageSeq: 4,
            messageType: 'TEXT',
            content: '最后一条消息',
            status: 'SENT',
            sentAt: '2026-06-24T11:00:00Z',
        },
    },
    {
        conversationId: 202,
        roomId: 7,
        gameId: 11,
        channelKey: 'PRIVATE:31',
        groupKey: 'PRIVATE',
        conversationType: 'PRIVATE',
        displayPeerKey: 'Alice',
        messageSeq: 2,
    },
    {
        conversationId: 203,
        roomId: 7,
        gameId: 11,
        channelKey: 'SPECTATOR',
        groupKey: 'SPECTATOR',
        conversationType: 'GROUP',
        messageSeq: 1,
    },
    {
        conversationId: 204,
        roomId: 7,
        gameId: 11,
        channelKey: 'SYSTEM',
        groupKey: 'SYSTEM',
        conversationType: 'SYSTEM',
        messageSeq: 9,
    },
]

const messages: ClocktowerMessageResponse[] = [
    {
        messageId: 1,
        conversationId: 201,
        senderUserId: 101,
        messageSeq: 1,
        messageType: 'TEXT',
        content: '公开消息',
        sentAt: '2026-06-24T10:00:00Z',
    },
]

const pendingMessage: ClocktowerMessageResponse = {
    messageId: -1,
    conversationId: 201,
    senderUserId: null,
    messageSeq: 5,
    messageType: 'TEXT',
    content: 'pending',
    sentAt: '2026-06-24T10:01:00Z',
    clientMsgId: 'client-1',
    status: 'PENDING',
}

const ackMessage: ClocktowerMessageResponse = {
    messageId: 2,
    conversationId: 201,
    senderUserId: 101,
    messageSeq: 5,
    messageType: 'TEXT',
    content: 'pending',
    sentAt: '2026-06-24T10:01:01Z',
    clientMsgId: 'client-1',
    status: 'SENT',
}

const channel: ChannelView = {
    id: 301,
    contextType: 'CLOCKTOWER_ROOM',
    contextId: 7,
    channelKey: 'PUBLIC',
    joinKey: 'chn_clocktower_public01',
    name: '玩家公聊',
    ownerUserId: 1,
    joinPolicy: 'OPEN',
    status: 'ACTIVE',
    announcement: '今晚 8 点开局',
    mainConversationId: 201,
    lastActiveAt: '2026-06-24T11:00:00Z',
    membershipStatus: 'ACTIVE',
    memberRole: 'MEMBER',
    canRead: true,
    canPost: true,
    unreadCount: 3,
}

const discoverableGroup: GroupView = {
    id: 302,
    channelId: 301,
    contextType: 'CLOCKTOWER_ROOM',
    contextId: 7,
    groupKey: 'SPECTATOR',
    joinKey: 'grp_clocktower_spectator',
    name: '旁观席',
    ownerUserId: 1,
    joinPolicy: 'APPROVAL',
    status: 'ACTIVE',
    conversationId: 203,
    lastActiveAt: '2026-06-24T11:02:00Z',
    membershipStatus: 'PENDING',
    memberRole: null,
    canRead: false,
    canPost: false,
    unreadCount: 0,
}

const newDiscoverableGroup: GroupView = {
    ...discoverableGroup,
    id: 303,
    conversationId: 303,
    groupKey: 'TRAVELER',
    membershipStatus: null,
    name: '旅行者频道',
}

describe('ClocktowerChatPanel', () => {
    test('disables player public and private composers at night', () => {
        expect(resolveClocktowerChatPolicy('PLAYER', conversations[0], 'NIGHT')).toMatchObject({
            readOnly: true,
            reason: '夜晚阶段不可发言',
        })
        expect(resolveClocktowerChatPolicy('PLAYER', conversations[1], 'FIRST_NIGHT')).toMatchObject({
            readOnly: true,
            reason: '夜晚阶段不可发言',
        })
    })

    test('allows spectator channel while keeping public player chat read-only', () => {
        expect(resolveClocktowerChatPolicy('SPECTATOR', conversations[0], 'DAY')).toMatchObject({
            readOnly: true,
            reason: '旁观者只能查看玩家公聊',
        })
        expect(resolveClocktowerChatPolicy('SPECTATOR', conversations[2], 'DAY')).toMatchObject({
            readOnly: false,
            reason: '可发言',
        })
    })

    test('filters storyteller monitor away from spectator conversations and allows announcements at night', () => {
        expect(filterClocktowerConversations(conversations, 'STORYTELLER').map((item) => item.groupKey)).toEqual([
            'PUBLIC',
            'PRIVATE',
            'SYSTEM',
        ])
        expect(resolveClocktowerChatPolicy('STORYTELLER', conversations[3], 'NIGHT')).toMatchObject({
            readOnly: false,
            reason: '可发言',
        })
    })

    test('keeps room public conversations visible in an active game view', () => {
        const scopedConversations: ClocktowerConversationResponse[] = [
            {
                conversationId: 200,
                roomId: 7,
                gameId: null,
                channelKey: 'PUBLIC',
                groupKey: 'PUBLIC',
                conversationType: 'GROUP',
                messageSeq: 99,
            },
            ...conversations,
        ]

        expect(filterClocktowerConversations(scopedConversations, 'PLAYER', 11).map((item) => item.conversationId))
            .toEqual([200, 201, 202, 204])
    })

    test('builds room and game context loads and dedupes aliases from merged contexts', () => {
        const duplicatedConversations: ClocktowerConversationResponse[] = [
            conversations[1],
            {
                ...conversations[1],
                gameId: 11,
                groupKey: 'PRIVATE:11:12',
                displayPeerKey: null,
                messageSeq: 6,
            },
        ]

        expect(buildClocktowerContextIds(7, 11)).toEqual([7, 11])
        expect(buildClocktowerContextIds(7, 7)).toEqual([7])
        expect(mergeClocktowerConversationRecords(duplicatedConversations)).toMatchObject([
            {
                conversationId: 202,
                groupKey: 'PRIVATE',
                displayPeerKey: 'Alice',
                messageSeq: 6,
            },
        ])
        expect(mergeClocktowerConversationRecords([
            {
                ...conversations[1],
                groupKey: 'PRIVATE:11:12',
                displayPeerKey: null,
            },
        ])).toMatchObject([
            {
                conversationId: 202,
                groupKey: 'PRIVATE',
                displayPeerKey: '11:12',
            },
        ])
    })

    test('marks read only for conversation member modes', () => {
        expect(shouldMarkClocktowerChatRead('PLAYER', conversations[0])).toBe(true)
        expect(shouldMarkClocktowerChatRead('PLAYER', conversations[1])).toBe(true)
        expect(shouldMarkClocktowerChatRead('SPECTATOR', conversations[2])).toBe(true)
        expect(shouldMarkClocktowerChatRead('SPECTATOR', conversations[0])).toBe(false)
        expect(shouldMarkClocktowerChatRead('STORYTELLER', conversations[1])).toBe(false)
    })

    test('loads the latest message page from the conversation high-water mark', () => {
        expect(clocktowerChatMessagePage({messageSeq: 0})).toBe(1)
        expect(clocktowerChatMessagePage({messageSeq: 1})).toBe(1)
        expect(clocktowerChatMessagePage({messageSeq: 50})).toBe(1)
        expect(clocktowerChatMessagePage({messageSeq: 51})).toBe(2)
        expect(clocktowerChatMessagePage({messageSeq: 101})).toBe(3)
    })

    test('skips local sent message append after the active conversation changes', () => {
        expect(appendClocktowerSentMessage(messages, messages[0], 202, 201)).toBe(messages)
        expect(appendClocktowerSentMessage(messages, messages[0], 201, 201)).toHaveLength(2)
    })

    test('replaces pending messages on SEND_ACK and ignores duplicate pushed messages', () => {
        const currentMessages = [messages[0]]

        expect(mergeClocktowerAckMessage([messages[0], pendingMessage], ackMessage)).toEqual([messages[0], ackMessage])
        expect(mergeClocktowerPushedMessage(currentMessages, messages[0])).toBe(currentMessages)
        expect(mergeClocktowerPushedMessage([messages[0], pendingMessage], ackMessage)).toEqual([messages[0], ackMessage])
        expect(markClocktowerPendingMessageFailed([messages[0], pendingMessage], 'client-1')).toEqual([
            messages[0],
            {
                ...pendingMessage,
                status: 'FAILED',
            },
        ])
    })

    test('keeps server catch-up seq behind local pending and failed messages', () => {
        expect(resolveClocktowerConfirmedMessageSeq([messages[0], pendingMessage], conversations[0])).toBe(4)
        expect(resolveClocktowerConfirmedMessageSeq([
            messages[0],
            {...pendingMessage, messageSeq: 6, status: 'FAILED'},
        ], conversations[0])).toBe(4)
        expect(resolveClocktowerConfirmedMessageSeq([messages[0], ackMessage], conversations[0])).toBe(5)
        expect(resolveClocktowerMessagePushAction({
            eventType: 'MESSAGE_CREATED',
            conversationId: 201,
            messageId: 22,
            messageSeq: 6,
        }, 201)).toBe('LOAD_ACTIVE_HISTORY')
    })

    test('routes outbox-shaped message pushes to history or conversation refresh', () => {
        expect(resolveClocktowerMessagePushAction({
            eventType: 'MESSAGE_CREATED',
            conversationId: 201,
            messageId: 22,
            messageSeq: 6,
        }, 201)).toBe('LOAD_ACTIVE_HISTORY')
        expect(resolveClocktowerMessagePushAction({
            eventType: 'MESSAGE_CREATED',
            conversationId: 202,
            messageId: 23,
            messageSeq: 7,
        }, 201)).toBe('REFRESH_CONVERSATIONS')
        expect(resolveClocktowerMessagePushAction({
            eventType: 'MESSAGE_CREATED',
            message: {
                id: 2,
                conversationId: 201,
                senderUserId: 101,
                messageSeq: 5,
                clientMsgId: 'client-1',
                messageType: 'TEXT',
                content: 'pending',
                status: 'SENT',
                sentAt: '2026-06-24T10:01:01Z',
            },
        }, 201)).toBe('MERGE_MESSAGE')
    })

    test('keeps discoverable surfaces that are not already in the conversation list', () => {
        const surfaces = filterDiscoverableClocktowerSurfaces(
            [channel],
            [discoverableGroup, newDiscoverableGroup],
            conversations,
            'SPECTATOR',
            11,
        )

        expect(surfaces.channels).toEqual([])
        expect(surfaces.groups.map((item) => item.name)).toEqual(['旅行者频道'])
    })

    test('renders conversation list badges and message list content', () => {
        const listMarkup = renderToStaticMarkup(
            <ClocktowerConversationList
                activeConversationId={201}
                conversations={conversations.slice(0, 2)}
                getPolicy={(conversation) => resolveClocktowerChatPolicy('PLAYER', conversation, 'DAY')}
                onSelect={() => {
                }}
            />,
        )
        const messageMarkup = renderToStaticMarkup(<ClocktowerMessageList loading={false} messages={messages}/>)

        expect(listMarkup).toContain('玩家公聊')
        expect(listMarkup).toContain('Alice')
        expect(listMarkup).toContain('可发言')
        expect(listMarkup).toContain('最后一条消息')
        expect(listMarkup).toContain('3')
        expect(messageMarkup).toContain('公开消息')
        expect(messageMarkup).toContain('#1')
        expect(renderToStaticMarkup(
            <ClocktowerMessageList loading={false} messages={[{...pendingMessage, status: 'FAILED'}]}/>,
        )).toContain('发送失败')
    })

    test('renders spectator chat panel with IM surface controls and without private conversations', () => {
        const markup = renderToStaticMarkup(
            <ClocktowerChatPanel
                channels={[channel]}
                conversations={conversations}
                groups={[discoverableGroup, newDiscoverableGroup]}
                phase="DAY"
                roomId={7}
                viewerMode="SPECTATOR"
            />,
        )

        expect(markup).toContain('玩家公聊')
        expect(markup).toContain('只读')
        expect(markup).toContain('旁观席')
        expect(markup).toContain('可发言')
        expect(markup).toContain('旅行者频道')
        expect(markup).toContain('Apply to join')
        expect(markup).toContain('今晚 8 点开局')
        expect(markup).not.toContain('私聊')
    })
})
