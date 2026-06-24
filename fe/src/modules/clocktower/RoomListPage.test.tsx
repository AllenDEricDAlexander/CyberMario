import {renderToStaticMarkup} from 'react-dom/server'
import {Table} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import {
    Component as RoomListPage,
    createRoomListColumns,
    roomCreateInitialValues,
    roomListCounts,
} from './RoomListPage'
import type {ClocktowerRoomResponse} from './clocktowerTypes'

vi.mock('react-router', () => ({
    useNavigate: () => vi.fn(),
}))

vi.mock('./clocktowerService', () => ({
    createClocktowerRoom: vi.fn(),
    listClocktowerBoards: vi.fn().mockResolvedValue({records: []}),
    listClocktowerRooms: vi.fn().mockResolvedValue([]),
}))

function roomFixture(overrides: Partial<ClocktowerRoomResponse> = {}): ClocktowerRoomResponse {
    return {
        roomId: 7,
        roomCode: 'ABC123',
        name: '测试房间',
        scriptCode: 'TROUBLE_BREWING',
        status: 'LOBBY',
        phase: 'LOBBY',
        playerCount: 5,
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
            },
            {
                seatId: 12,
                seatNo: 2,
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
        reservations: [
            {invitationId: 21, inviteeUserId: 201, targetSeatNo: 2, expiresAt: '2026-06-25T00:00:00Z'},
        ],
        visibility: 'PUBLIC',
        ...overrides,
    }
}

describe('RoomListPage', () => {
    test('renders room list toolbar actions', () => {
        const markup = renderToStaticMarkup(<RoomListPage/>)

        expect(markup).toContain('钟楼房间')
        expect(markup).toContain('刷新')
        expect(markup).toContain('创建房间')
    })

    test('configures required table columns for room discovery', () => {
        const titles = createRoomListColumns(vi.fn()).map((column) => column.title)

        expect(titles).toEqual([
            '房间',
            '剧本',
            '可见性',
            '状态',
            '玩家',
            '预留',
            '说书人',
            '操作',
        ])
    })

    test('derives occupied and reserved counts from room response', () => {
        expect(roomListCounts(roomFixture())).toEqual({
            occupied: 1,
            required: 5,
            reserved: 1,
        })
    })

    test('defaults new rooms to open seating for the claim-seat lobby flow', () => {
        expect(roomCreateInitialValues.seatingPolicy).toBe('OPEN_SEATING')
    })

    test('renders list row data including visibility and reservation count', () => {
        const markup = renderToStaticMarkup(
            <Table
                columns={createRoomListColumns(vi.fn())}
                dataSource={[roomFixture()]}
                pagination={false}
                rowKey="roomId"
            />,
        )

        expect(markup).toContain('测试房间')
        expect(markup).toContain('TROUBLE_BREWING')
        expect(markup).toContain('公开')
        expect(markup).toContain('1/5')
        expect(markup).toContain('1')
    })

    test('does not describe missing room visibility as public', () => {
        const markup = renderToStaticMarkup(
            <Table
                columns={createRoomListColumns(vi.fn())}
                dataSource={[roomFixture({visibility: null})]}
                pagination={false}
                rowKey="roomId"
            />,
        )

        expect(markup).toContain('未提供')
        expect(markup).not.toContain('公开')
    })
})
