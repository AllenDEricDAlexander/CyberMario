import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as RoomLobbyPage, SeatList} from './RoomLobbyPage'

vi.mock('react-router', () => ({
    useNavigate: () => vi.fn(),
    useParams: () => ({roomId: '7'}),
}))

vi.mock('./clocktowerService', () => ({
    getClocktowerRoom: vi.fn().mockResolvedValue({
        roomId: 7,
        roomCode: 'ABC123',
        name: '测试房间',
        scriptCode: 'TROUBLE_BREWING',
        status: 'LOBBY',
        phase: 'LOBBY',
        playerCount: 5,
        seats: [],
    }),
    joinClocktowerRoom: vi.fn(),
    leaveClocktowerRoom: vi.fn(),
    startClocktowerGame: vi.fn(),
    updateClocktowerSeat: vi.fn(),
}))

describe('RoomLobbyPage', () => {
    test('renders lobby shell with leave control', () => {
        const markup = renderToStaticMarkup(<RoomLobbyPage/>)

        expect(markup).toContain('房间大厅')
        expect(markup).toContain('离开房间')
    })

    test('renders seat adjustment control for a real seat', () => {
        const markup = renderToStaticMarkup(
            <SeatList
                seats={[{
                    seatId: 11,
                    seatNo: 1,
                    displayName: '玩家一',
                    userId: 101,
                    roleCode: 'washerwoman',
                    roleType: 'TOWNSFOLK',
                    lifeStatus: 'ALIVE',
                    connected: true,
                    hasDeadVote: true,
                }]}
                onEdit={vi.fn()}
            />,
        )

        expect(markup).toContain('调整座位')
    })
})
