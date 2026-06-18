import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as GameRoomPage, SeatPublicList} from './GameRoomPage'

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
}))

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
})
