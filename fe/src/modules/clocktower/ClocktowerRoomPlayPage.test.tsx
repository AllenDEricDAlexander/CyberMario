import {isValidElement} from 'react'
import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {
    ClocktowerRoomPlaySurface,
    loadClocktowerRoomPlayView,
} from './ClocktowerRoomPlayPage'
import {StorytellerGameSurface} from './StorytellerGrimoirePage'
import type {ClocktowerGameViewResponse, ClocktowerRoomResponse} from './clocktowerTypes'

vi.mock('react-router', () => ({
    Link: ({children, to}: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
    useParams: () => ({roomId: '7'}),
}))

vi.mock('./clocktowerService', () => ({
    getClocktowerRoom: vi.fn(),
    getClocktowerGameView: vi.fn(),
    listClocktowerChatMessages: vi.fn().mockResolvedValue({items: [], page: 1, size: 20, total: 0}),
    markClocktowerChatRead: vi.fn(),
    sendClocktowerChatMessage: vi.fn(),
    getClocktowerGameFlow: vi.fn(),
    advanceClocktowerGameFlow: vi.fn(),
}))

const room: ClocktowerRoomResponse = {
    roomId: 7,
    roomCode: 'ABC123',
    name: '测试房间',
    scriptCode: 'TROUBLE_BREWING',
    status: 'RUNNING',
    phase: 'DAY',
    playerCount: 5,
    currentGameId: 11,
    seats: [],
}

const view: ClocktowerGameViewResponse = {
    gameId: 11,
    roomId: 7,
    gameNo: 1,
    status: 'RUNNING',
    phase: 'DAY',
    viewerMode: 'PLAYER',
    mySeat: null,
    publicSeats: [],
    grimoire: [],
    availableActions: [],
    events: [],
    conversations: [],
}

describe('ClocktowerRoomPlayPage', () => {
    test('loads the room aggregate before resolving the game view', async () => {
        const service = await import('./clocktowerService')
        const getRoom = service.getClocktowerRoom as ReturnType<typeof vi.fn>
        const getGameView = service.getClocktowerGameView as ReturnType<typeof vi.fn>
        getRoom.mockResolvedValue(room)
        getGameView.mockResolvedValue(view)

        const result = await loadClocktowerRoomPlayView(7)

        expect(service.getClocktowerRoom).toHaveBeenCalledWith(7)
        expect(service.getClocktowerGameView).toHaveBeenCalledWith(11)
        expect(result).toEqual({room, gameView: view})
        expect(getRoom.mock.invocationCallOrder[0]).toBeLessThan(
            getGameView.mock.invocationCallOrder[0],
        )
    })

    test('renders lobby entry when no current game exists', () => {
        const markup = renderToStaticMarkup(
            <ClocktowerRoomPlaySurface
                room={{
                    ...room,
                    status: 'LOBBY',
                    phase: 'LOBBY',
                    currentGameId: null,
                }}
            />,
        )

        expect(markup).toContain('游戏尚未开始')
        expect(markup).toContain('返回大厅')
        expect(markup).toContain('/clocktower/rooms/7/lobby')
    })

    test('renders storyteller game surface for storyteller viewer mode', () => {
        const markup = renderToStaticMarkup(
            <ClocktowerRoomPlaySurface
                gameView={{
                    ...view,
                    viewerMode: 'STORYTELLER',
                    grimoire: [
                        {
                            gameSeatId: 31,
                            roomSeatId: 3,
                            seatNo: 1,
                            userId: 101,
                            displayName: 'Alice',
                            roleCode: 'EMPATH',
                            roleType: 'TOWNSFOLK',
                            alignment: 'GOOD',
                            lifeStatus: 'ALIVE',
                            publicLifeStatus: 'ALIVE',
                            hasDeadVote: true,
                            traveler: false,
                            status: 'ACTIVE',
                        },
                    ],
                }}
                room={room}
            />,
        )

        expect(markup).toContain('说书人魔典')
        expect(markup).toContain('聊天监控')
        expect(markup).toContain('EMPATH')
    })

    test('forwards reload to the storyteller game surface', () => {
        const onReload = vi.fn().mockResolvedValue(undefined)
        const surface = ClocktowerRoomPlaySurface({
            gameView: {...view, viewerMode: 'STORYTELLER'},
            onReload,
            room,
        })

        expect(isValidElement(surface)).toBe(true)
        if (!isValidElement(surface)) {
            throw new Error('storyteller game surface missing')
        }

        const props = surface.props as {onGameChanged?: () => Promise<void>}
        expect(surface.type).toBe(StorytellerGameSurface)
        expect(props.onGameChanged).toBe(onReload)
    })

    test('renders player play surface with new game action controls', () => {
        const markup = renderToStaticMarkup(
            <ClocktowerRoomPlaySurface
                gameView={{
                    ...view,
                    mySeat: {
                        gameSeatId: 31,
                        roomSeatId: 3,
                        seatNo: 1,
                        userId: 101,
                        displayName: 'Alice',
                        roleCode: 'EMPATH',
                        roleType: 'TOWNSFOLK',
                        alignment: 'GOOD',
                        lifeStatus: 'ALIVE',
                        publicLifeStatus: 'ALIVE',
                        hasDeadVote: true,
                        traveler: false,
                        status: 'ACTIVE',
                    },
                    availableActions: [{actionType: 'PUBLIC_SPEECH', label: '公开发言', enabled: true}],
                }}
                room={room}
            />,
        )

        expect(markup).toContain('玩家视角')
        expect(markup).toContain('聊天')
        expect(markup).toContain('操作')
        expect(markup).toContain('公开发言')
    })
})
