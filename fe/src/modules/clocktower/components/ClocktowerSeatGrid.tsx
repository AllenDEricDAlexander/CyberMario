import {CheckCircleOutlined, UserAddOutlined} from '@ant-design/icons'
import {Badge, Button, Empty, Flex, Space, Table, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {isAgentSeat} from '../RoomLobbyPage'
import type {ClocktowerRoomReservationResponse, ClocktowerSeatResponse} from '../clocktowerTypes'

type ClocktowerSeatGridProps = {
    seats: ClocktowerSeatResponse[]
    reservations?: ClocktowerRoomReservationResponse[]
    claimingSeatNo?: number | null
    onClaimSeat: (seatNo: number) => void
}

type SeatDraftRow = {
    seat: ClocktowerSeatResponse
    state: SeatDraftState
}

type SeatDraftState = 'occupied' | 'reserved' | 'ready' | 'agent' | 'open'

const seatStateMeta = {
    occupied: {label: '已入座', color: 'processing' as const, badge: 'processing' as const},
    reserved: {label: '已预留', color: 'warning' as const, badge: 'warning' as const},
    ready: {label: '已就绪', color: 'success' as const, badge: 'success' as const},
    agent: {label: 'Agent', color: 'purple' as const, badge: 'processing' as const},
    open: {label: '空座', color: 'default' as const, badge: 'default' as const},
}

export function ClocktowerSeatGrid({
                                      seats,
                                      reservations = [],
                                      claimingSeatNo = null,
                                      onClaimSeat,
                                  }: ClocktowerSeatGridProps) {
    const reservationSeatNos = new Set(reservations.map((reservation) => reservation.targetSeatNo).filter(isNumber))
    const rows = seats.map((seat) => ({seat, state: seatState(seat, reservationSeatNos)}))
    const columns: ColumnsType<SeatDraftRow> = [
        {
            title: '座位',
            key: 'seat',
            width: 120,
            render: (_, row) => {
                const meta = seatStateMeta[row.state]
                return (
                    <Space>
                        <Badge status={meta.badge}/>
                        <Typography.Text strong>#{row.seat.seatNo}</Typography.Text>
                    </Space>
                )
            },
        },
        {
            title: '状态',
            key: 'state',
            width: 120,
            render: (_, row) => {
                const meta = seatStateMeta[row.state]
                return <Tag color={meta.color}>{meta.label}</Tag>
            },
        },
        {
            title: '玩家',
            key: 'player',
            render: (_, row) => (
                <Typography.Text strong>
                    {row.seat.displayName || (row.state === 'reserved' ? '等待受邀玩家' : '未入座')}
                </Typography.Text>
            ),
        },
        {
            title: '标记',
            key: 'tags',
            render: (_, row) => (
                <Flex gap={4} wrap>
                    {isAgentSeat(row.seat) ? <Tag color="purple">Agent</Tag> : (
                        row.seat.connected ? <Tag color="success">在线</Tag> : <Tag>离线</Tag>
                    )}
                    {isAgentSeat(row.seat) && <Tag color="blue">自动</Tag>}
                    {row.seat.ready === true && <Tag icon={<CheckCircleOutlined/>} color="success">已就绪</Tag>}
                    {row.seat.ready === false && <Tag color="warning">未就绪</Tag>}
                    {row.seat.roleCode && <Tag>{row.seat.roleCode}</Tag>}
                </Flex>
            ),
        },
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 140,
            render: (_, row) => {
                const canClaim = row.state === 'open'
                return (
                    <Button
                        disabled={!canClaim}
                        icon={<UserAddOutlined/>}
                        loading={claimingSeatNo === row.seat.seatNo}
                        onClick={() => onClaimSeat(row.seat.seatNo)}
                        type={canClaim ? 'primary' : 'default'}
                    >
                        认领座位
                    </Button>
                )
            },
        },
    ]

    return (
        <Table
            columns={columns}
            dataSource={rows}
            locale={{emptyText: <Empty description="暂无座位"/>}}
            pagination={false}
            rowKey={(row) => row.seat.seatId}
            scroll={{x: 760}}
        />
    )
}

function seatState(seat: ClocktowerSeatResponse, reservationSeatNos: Set<number>): SeatDraftState {
    if (isAgentSeat(seat)) {
        return seat.ready === true ? 'ready' : 'agent'
    }
    if (seat.userId && seat.ready === true) {
        return 'ready'
    }
    if (seat.userId) {
        return 'occupied'
    }
    if (reservationSeatNos.has(seat.seatNo)) {
        return 'reserved'
    }
    return 'open'
}

function isNumber(value: number | null | undefined): value is number {
    return typeof value === 'number'
}
