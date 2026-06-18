import {renderToStaticMarkup} from 'react-dom/server'
import {Form} from 'antd'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {Component as RoomLobbyPage, roleSelectOptions, SeatEditorFields, SeatList} from './RoomLobbyPage'

type MockAuthState = {
    roleCodes: string[]
    adminBypass: boolean
}

const authStateKey = '__clocktowerRoomLobbyAuthState__'
const authState = ((globalThis as typeof globalThis & Record<string, MockAuthState>)[authStateKey] = {
    roleCodes: ['CLOCKTOWER_STORYTELLER'],
    adminBypass: false,
})

vi.mock('react-router', () => ({
    useNavigate: () => vi.fn(),
    useParams: () => ({roomId: '7'}),
}))

vi.mock('../auth/authStore', () => ({
    useAuth: () => ({
        roleCodes: (globalThis as typeof globalThis & Record<string, MockAuthState>)[authStateKey].roleCodes,
        hasPermission: () => (globalThis as typeof globalThis & Record<string, MockAuthState>)[authStateKey].adminBypass,
    }),
    hasAdminPermissionBypass: () => (globalThis as typeof globalThis & Record<string, MockAuthState>)[authStateKey].adminBypass,
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
    getClocktowerRoles: vi.fn().mockResolvedValue([]),
    joinClocktowerRoom: vi.fn(),
    leaveClocktowerRoom: vi.fn(),
    startClocktowerGame: vi.fn(),
    updateClocktowerSeat: vi.fn(),
}))

describe('RoomLobbyPage', () => {
    beforeEach(() => {
        authState.roleCodes = ['CLOCKTOWER_STORYTELLER']
        authState.adminBypass = false
    })

    test('renders lobby shell with leave control', () => {
        const markup = renderToStaticMarkup(<RoomLobbyPage/>)

        expect(markup).toContain('房间大厅')
        expect(markup).toContain('离开房间')
    })

    test('does not render storyteller-only controls for player role', () => {
        authState.roleCodes = ['CLOCKTOWER_PLAYER']
        const markup = renderToStaticMarkup(<RoomLobbyPage/>)

        expect(markup).toContain('房间大厅')
        expect(markup).toContain('离开房间')
        expect(markup).not.toContain('开始游戏')
    })

    test('does not render seat adjustment control when edit is disabled', () => {
        const markup = renderToStaticMarkup(
            <SeatList
                canEdit={false}
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

        expect(markup).not.toContain('调整座位')
    })

    test('renders seat adjustment control when edit is enabled', () => {
        const markup = renderToStaticMarkup(
            <SeatList
                canEdit
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

    test('renders role assignment field in the seat editor', () => {
        const markup = renderToStaticMarkup(
            <Form layout="vertical">
                <SeatEditorFields
                    roleLoading={false}
                    roles={[{
                        scriptCode: 'TROUBLE_BREWING',
                        roleCode: 'EMPATH',
                        roleName: '共情者',
                        name: '共情者',
                        roleType: {code: 1, desc: '镇民'},
                        alignment: {code: 1, desc: '善良'},
                        abilityText: '每晚得知邻近存活玩家中有几名邪恶玩家。',
                        enabled: true,
                    }]}
                />
            </Form>,
        )

        expect(markup).toContain('角色')
    })

    test('maps script roles to role select options', () => {
        const options = roleSelectOptions([{
            scriptCode: 'TROUBLE_BREWING',
            roleCode: 'EMPATH',
            roleName: '共情者',
            name: '共情者',
            roleType: {code: 1, desc: '镇民'},
            alignment: {code: 1, desc: '善良'},
            abilityText: '每晚得知邻近存活玩家中有几名邪恶玩家。',
            enabled: true,
        }])

        expect(options).toContainEqual({label: '共情者 (EMPATH)', value: 'EMPATH'})
    })
})
