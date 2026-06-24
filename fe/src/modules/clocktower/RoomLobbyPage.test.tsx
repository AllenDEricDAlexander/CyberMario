import {renderToStaticMarkup} from 'react-dom/server'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    Component as RoomLobbyPage,
    RoomLobbyManagementActions,
    RoomLobbyTabs,
    canStartClocktowerRoom,
    clocktowerRoomPlayPath,
    loadClocktowerRoomForLobby,
    roomLobbyCounts,
} from './RoomLobbyPage'
import {ClocktowerSeatGrid} from './components/ClocktowerSeatGrid'
import type {ClocktowerRoomResponse} from './clocktowerTypes'

type MockAuthState = {
    roleCodes: string[]
    adminBypass: boolean
    userId: number | null
}

const authStateKey = '__clocktowerRoomLobbyAuthState__'
const authState = ((globalThis as typeof globalThis & Record<string, MockAuthState>)[authStateKey] = {
    roleCodes: ['CLOCKTOWER_STORYTELLER'],
    adminBypass: false,
    userId: 101,
})

vi.mock('react-router', () => ({
    useNavigate: () => vi.fn(),
    useParams: () => ({roomId: '7'}),
}))

vi.mock('../auth/authStore', () => ({
    useAuth: () => ({
        roleCodes: (globalThis as typeof globalThis & Record<string, MockAuthState>)[authStateKey].roleCodes,
        hasPermission: () => (globalThis as typeof globalThis & Record<string, MockAuthState>)[authStateKey].adminBypass,
        user: {id: (globalThis as typeof globalThis & Record<string, MockAuthState>)[authStateKey].userId},
    }),
    hasAdminPermissionBypass: () => (globalThis as typeof globalThis & Record<string, MockAuthState>)[authStateKey].adminBypass,
}))

vi.mock('./clocktowerService', () => ({
    claimClocktowerSeat: vi.fn(),
    createClocktowerInvitation: vi.fn(),
    enterClocktowerRoom: vi.fn().mockResolvedValue(clocktowerRoomFixture()),
    getClocktowerRoom: vi.fn().mockResolvedValue(clocktowerRoomFixture()),
    heartbeatClocktowerRoom: vi.fn(),
    kickClocktowerRoomMember: vi.fn(),
    leaveClocktowerRoom: vi.fn(),
    startClocktowerGame: vi.fn(),
}))

function clocktowerRoomFixture(overrides: Partial<ClocktowerRoomResponse> = {}): ClocktowerRoomResponse {
    return {
        roomId: 7,
        roomCode: 'ABC123',
        name: '测试房间',
        scriptCode: 'TROUBLE_BREWING',
        status: 'LOBBY',
        phase: 'LOBBY',
        playerCount: 3,
        storytellerUserId: 99,
        seats: [
            {
                seatId: 11,
                seatNo: 1,
                displayName: '玩家一',
                userId: 101,
                roleCode: 'CHEF',
                roleType: 'TOWNSFOLK',
                lifeStatus: 'ALIVE',
                publicLifeStatus: 'ALIVE',
                connected: true,
                hasDeadVote: true,
                ready: true,
            },
            {
                seatId: 12,
                seatNo: 2,
                displayName: '玩家二',
                userId: 102,
                roleCode: 'IMP',
                roleType: 'DEMON',
                lifeStatus: 'ALIVE',
                publicLifeStatus: 'ALIVE',
                connected: false,
                hasDeadVote: true,
                ready: false,
            },
            {
                seatId: 13,
                seatNo: 3,
                displayName: '',
                userId: null,
                roleCode: null,
                roleType: null,
                lifeStatus: null,
                publicLifeStatus: null,
                connected: false,
                hasDeadVote: true,
            },
        ],
        members: [
            {memberId: 20, userId: 99, memberType: 'OWNER', status: 'ACTIVE', seatNo: null, displayName: '房主'},
            {memberId: 21, userId: 101, memberType: 'PLAYER', status: 'ACTIVE', seatNo: 1, displayName: '玩家一'},
        ],
        reservations: [
            {invitationId: 31, inviteeUserId: 201, targetSeatNo: 3, expiresAt: '2026-06-25T00:00:00Z'},
        ],
        ...overrides,
    }
}

describe('RoomLobbyPage', () => {
    beforeEach(() => {
        authState.roleCodes = ['CLOCKTOWER_STORYTELLER']
        authState.adminBypass = false
        authState.userId = 101
    })

    test('renders lobby toolbar and hides owner controls before room ownership is known', () => {
        const markup = renderToStaticMarkup(<RoomLobbyPage/>)

        expect(markup).toContain('房间大厅')
        expect(markup).toContain('心跳')
        expect(markup).not.toContain('邀请')
        expect(markup).not.toContain('成员')
        expect(markup).not.toContain('开始游戏')
    })

    test('enters the route room once for a lobby load without scheduling a repeat', async () => {
        const loadedRooms: ClocktowerRoomResponse[] = []
        const loadingStates: boolean[] = []
        const enterRoom = vi.fn(() => Promise.resolve(clocktowerRoomFixture()))

        await loadClocktowerRoomForLobby({
            enterRoom,
            roomId: 7,
            onLoadingChange: (loading) => loadingStates.push(loading),
            onRoomLoaded: (room) => loadedRooms.push(room),
        })
        await Promise.resolve()
        await Promise.resolve()

        expect(enterRoom).toHaveBeenCalledTimes(1)
        expect(enterRoom).toHaveBeenCalledWith(7)
        expect(loadedRooms).toEqual([clocktowerRoomFixture()])
        expect(loadingStates).toEqual([true, false])
    })

    test('renders lobby tab content for a loaded room', () => {
        const markup = renderToStaticMarkup(
            <RoomLobbyTabs claimingSeatNo={null} onClaimSeat={vi.fn()} room={clocktowerRoomFixture()}/>,
        )

        expect(markup).toContain('座位')
        expect(markup).toContain('房间公聊')
        expect(markup).toContain('邀请')
        expect(markup).toContain('成员')
    })

    test('hides invitation and member controls for player role', () => {
        authState.roleCodes = ['CLOCKTOWER_PLAYER']
        const markup = renderToStaticMarkup(<RoomLobbyPage/>)

        expect(markup).toContain('房间大厅')
        expect(markup).toContain('心跳')
        expect(markup).not.toContain('邀请')
        expect(markup).not.toContain('成员')
        expect(markup).not.toContain('开始游戏')
    })

    test('shows owner management actions only to the room owner', () => {
        const room = clocktowerRoomFixture()
        const callbacks = {
            onOpenInvitation: vi.fn(),
            onOpenMembers: vi.fn(),
            onStartGame: vi.fn(),
        }
        const nonOwnerMarkup = renderToStaticMarkup(
            <RoomLobbyManagementActions
                currentUserId={101}
                room={room}
                startDisabled={false}
                starting={false}
                {...callbacks}
            />,
        )
        const ownerMarkup = renderToStaticMarkup(
            <RoomLobbyManagementActions
                currentUserId={99}
                room={room}
                startDisabled={false}
                starting={false}
                {...callbacks}
            />,
        )

        expect(nonOwnerMarkup).not.toContain('邀请')
        expect(nonOwnerMarkup).not.toContain('成员')
        expect(nonOwnerMarkup).not.toContain('开始游戏')
        expect(ownerMarkup).toContain('邀请')
        expect(ownerMarkup).toContain('成员')
        expect(ownerMarkup).toContain('开始游戏')
    })

    test('seat grid distinguishes occupied reserved ready and open states', () => {
        const room = clocktowerRoomFixture()
        const markup = renderToStaticMarkup(
            <ClocktowerSeatGrid
                claimingSeatNo={null}
                onClaimSeat={vi.fn()}
                reservations={room.reservations}
                seats={[
                    ...room.seats,
                    {
                        seatId: 14,
                        seatNo: 4,
                        displayName: '',
                        userId: null,
                        roleCode: null,
                        roleType: null,
                        lifeStatus: null,
                        publicLifeStatus: null,
                        connected: false,
                        hasDeadVote: true,
                    },
                ]}
            />,
        )

        expect(markup).toContain('玩家一')
        expect(markup).toContain('已就绪')
        expect(markup).toContain('已预留')
        expect(markup).toContain('空座')
        expect(markup).toContain('认领座位')
    })

    test('derives lobby counts from seats and active reservations', () => {
        expect(roomLobbyCounts(clocktowerRoomFixture())).toEqual({
            occupied: 2,
            reserved: 1,
            required: 3,
        })
    })

    test('requires lobby status occupied seats and no active reservations before start', () => {
        const blocked = clocktowerRoomFixture()
        const ready = clocktowerRoomFixture({
            seats: clocktowerRoomFixture().seats.map((seat) => ({
                ...seat,
                userId: seat.userId ?? 103,
                roleCode: seat.roleCode ?? 'WASHERWOMAN',
                ready: true,
            })),
            reservations: [],
        })

        expect(canStartClocktowerRoom(blocked)).toBe(false)
        expect(canStartClocktowerRoom(ready)).toBe(true)
        expect(canStartClocktowerRoom({...ready, status: 'RUNNING'})).toBe(false)
        expect(canStartClocktowerRoom({...ready, seats: [{...ready.seats[0], ready: false}, ...ready.seats.slice(1)]}))
            .toBe(false)
    })

    test('requires role assignments before start', () => {
        const ready = clocktowerRoomFixture({
            seats: clocktowerRoomFixture().seats.map((seat) => ({
                ...seat,
                userId: seat.userId ?? 103,
                roleCode: seat.roleCode ?? 'WASHERWOMAN',
                ready: true,
            })),
            reservations: [],
        })

        expect(canStartClocktowerRoom({
            ...ready,
            seats: [{...ready.seats[0], roleCode: null}, ...ready.seats.slice(1)],
        })).toBe(false)
    })

    test('routes started games to the refactor play surface', () => {
        expect(clocktowerRoomPlayPath(7)).toBe('/clocktower/rooms/7/play')
        expect(clocktowerRoomPlayPath(7)).not.toContain('/grimoire')
    })
})
