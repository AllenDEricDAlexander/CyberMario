import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {
    appendClocktowerSentMessage,
    clocktowerChatMessagePage,
    ClocktowerChatPanel,
    filterClocktowerConversations,
    resolveClocktowerChatPolicy,
    shouldMarkClocktowerChatRead,
} from './ClocktowerChatPanel'
import {ClocktowerConversationList} from './ClocktowerConversationList'
import {ClocktowerMessageList} from './ClocktowerMessageList'
import type {ClocktowerConversationResponse, ClocktowerMessageResponse} from '../clocktowerTypes'

vi.mock('../clocktowerService', () => ({
    listClocktowerChatMessages: vi.fn().mockResolvedValue({items: [], page: 1, size: 20, total: 0}),
    markClocktowerChatRead: vi.fn(),
    sendClocktowerChatMessage: vi.fn(),
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
    },
    {
        conversationId: 202,
        roomId: 7,
        gameId: 11,
        channelKey: 'PRIVATE:31',
        groupKey: 'PRIVATE',
        conversationType: 'PRIVATE',
        participantKey: 'Alice',
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

    test('scopes game chat conversations to the active game id', () => {
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
            .toEqual([201, 202, 204])
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
        expect(messageMarkup).toContain('公开消息')
        expect(messageMarkup).toContain('#1')
    })

    test('renders spectator chat panel without private conversations', () => {
        const markup = renderToStaticMarkup(
            <ClocktowerChatPanel conversations={conversations} phase="DAY" viewerMode="SPECTATOR"/>,
        )

        expect(markup).toContain('玩家公聊')
        expect(markup).toContain('只读')
        expect(markup).toContain('旁观席')
        expect(markup).toContain('可发言')
        expect(markup).not.toContain('私聊')
    })
})
