import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as GameRoomPage, GameRoomSurface, SeatPublicList} from './GameRoomPage'
import type {ClocktowerGameViewResponse} from './clocktowerTypes'

vi.mock('react-router', () => ({
    useParams: () => ({roomId: '7'}),
}))

vi.mock('./clocktowerService', () => ({
    getClocktowerPlayerView: vi.fn().mockResolvedValue({
        room: {roomId: 7, roomCode: 'ABC123', name: '测试房间', scriptCode: 'TROUBLE_BREWING', status: 'RUNNING', phase: 'DAY', playerCount: 5, seats: []},
        viewerMode: 'PLAYER',
        mySeat: {
            seatId: 3,
            seatNo: 3,
            displayName: 'Alice',
            roleCode: 'washerwoman',
            roleType: 'TOWNSFOLK',
            lifeStatus: 'ALIVE',
            publicLifeStatus: 'DEAD',
            hasDeadVote: true,
        },
        publicSeats: [
            {
                seatId: 4,
                seatNo: 4,
                displayName: 'Bob',
                roleCode: null,
                lifeStatus: 'DEAD',
                connected: true,
                hasDeadVote: false,
            },
        ],
        phase: {phase: 'DAY', dayNo: 1, nightNo: 0},
        availableActions: [{actionType: 'PUBLIC_SPEECH', label: '公开发言', enabled: true}],
        recentEvents: [],
        privateThreads: [],
    }),
    streamClocktowerEvents: vi.fn(),
    submitClocktowerPlayerAction: vi.fn(),
    listClocktowerChatMessages: vi.fn().mockResolvedValue({items: [], page: 1, size: 20, total: 0}),
    markClocktowerChatRead: vi.fn(),
    sendClocktowerChatMessage: vi.fn(),
}))

const gameView: ClocktowerGameViewResponse = {
    gameId: 11,
    roomId: 7,
    gameNo: 1,
    status: 'RUNNING',
    phase: 'DAY',
    viewerMode: 'PLAYER',
    mySeat: {
        gameSeatId: 31,
        roomSeatId: 3,
        seatNo: 3,
        userId: 101,
        displayName: 'Alice',
        roleCode: 'washerwoman',
        roleType: 'TOWNSFOLK',
        alignment: 'GOOD',
        lifeStatus: 'ALIVE',
        publicLifeStatus: 'ALIVE',
        hasDeadVote: true,
        traveler: false,
        status: 'ACTIVE',
    },
    publicSeats: [
        {
            gameSeatId: 32,
            roomSeatId: 4,
            seatNo: 4,
            userId: 102,
            displayName: 'Bob',
            roleCode: null,
            roleType: null,
            alignment: null,
            lifeStatus: 'DEAD',
            publicLifeStatus: 'DEAD',
            hasDeadVote: false,
            traveler: false,
            status: 'ACTIVE',
        },
    ],
    grimoire: [],
    availableActions: [{actionType: 'PUBLIC_SPEECH', label: '公开发言', enabled: true}],
    events: [
        {
            eventId: 91,
            gameId: 11,
            eventSeq: 6,
            eventType: 'PUBLIC_MESSAGE_SENT',
            phase: 'DAY',
            dayNo: 1,
            nightNo: 0,
            visibility: 'PUBLIC',
            visibleGameSeatIds: [],
            payload: {content: '白天讨论开始'},
            occurredAt: '2026-06-24T10:00:00Z',
        },
    ],
    conversations: [
        {
            conversationId: 201,
            roomId: 7,
            gameId: 11,
            channelKey: 'PUBLIC',
            groupKey: 'PUBLIC',
            conversationType: 'GROUP',
            messageSeq: 3,
            lastMessageAt: '2026-06-24T10:00:00Z',
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
    ],
}

describe('GameRoomPage', () => {
    test('renders player room surface', () => {
        const markup = renderToStaticMarkup(<GameRoomPage/>)

        expect(markup).toContain('游戏房间')
        expect(markup).toContain('我的身份')
        expect(markup).toContain('公共事件')
    })

    test('renders public seat life status from player view', () => {
        const markup = renderToStaticMarkup(
            <SeatPublicList
                seats={[
                    {
                        seatId: 4,
                        seatNo: 4,
                        displayName: 'Bob',
                        roleCode: null,
                        lifeStatus: 'DEAD',
                        connected: true,
                        hasDeadVote: false,
                    },
                ]}
            />,
        )

        expect(markup).toContain('Bob')
        expect(markup).toContain('DEAD')
    })

    test('renders player game view with role, actions, events, and private chat', () => {
        const markup = renderToStaticMarkup(<GameRoomSurface roomName="测试房间" view={gameView}/>)

        expect(markup).toContain('玩家视角')
        expect(markup).toContain('washerwoman')
        expect(markup).toContain('公开发言')
        expect(markup).toContain('白天讨论开始')
        expect(markup).toContain('私聊')
        expect(markup).toContain('Alice')
        expect(markup).not.toContain('旁观席')
    })

    test('renders spectator view with read-only public chat and writable spectator channel', () => {
        const markup = renderToStaticMarkup(
            <GameRoomSurface
                roomName="测试房间"
                view={{
                    ...gameView,
                    viewerMode: 'SPECTATOR',
                    mySeat: null,
                }}
            />,
        )

        expect(markup).toContain('旁观视角')
        expect(markup).toContain('Bob')
        expect(markup).toContain('公共事件')
        expect(markup).toContain('玩家公聊')
        expect(markup).toContain('只读')
        expect(markup).toContain('旁观席')
        expect(markup).toContain('可发言')
        expect(markup).not.toContain('我的身份')
    })
})
