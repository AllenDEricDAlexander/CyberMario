import {EditOutlined, LoginOutlined, LogoutOutlined, PlayCircleOutlined, ReloadOutlined} from '@ant-design/icons'
import {App, Button, Card, Col, Empty, Form, Input, InputNumber, List, Modal, Row, Space, Tag, Typography} from 'antd'
import {useEffect, useMemo, useState} from 'react'
import {useNavigate, useParams} from 'react-router'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {
    getClocktowerRoom,
    joinClocktowerRoom,
    leaveClocktowerRoom,
    startClocktowerGame,
    updateClocktowerSeat,
} from './clocktowerService'
import type {ClocktowerRoomResponse, ClocktowerSeatResponse, ClocktowerUpdateSeatRequest} from './clocktowerTypes'
import {RoleTypeTag} from './components/RoleTypeTag'

type JoinFormValues = {
    seatNo?: number
    displayName?: string
    inviteCode?: string
}

type SeatFormValues = ClocktowerUpdateSeatRequest

function RoomLobbyPage() {
    const {roomId} = useParams()
    const navigate = useNavigate()
    const {message} = App.useApp()
    const [form] = Form.useForm<JoinFormValues>()
    const [seatForm] = Form.useForm<SeatFormValues>()
    const [room, setRoom] = useState<ClocktowerRoomResponse | null>(null)
    const [selectedSeat, setSelectedSeat] = useState<ClocktowerSeatResponse | null>(null)
    const [seatEditorOpen, setSeatEditorOpen] = useState(false)
    const [loading, setLoading] = useState(false)
    const [joining, setJoining] = useState(false)
    const [starting, setStarting] = useState(false)
    const [updatingSeat, setUpdatingSeat] = useState(false)
    const [leaving, setLeaving] = useState(false)
    const numericRoomId = Number(roomId)

    useEffect(() => {
        void loadRoom()
    }, [roomId])

    async function loadRoom() {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        setLoading(true)
        try {
            setRoom(await getClocktowerRoom(numericRoomId))
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setLoading(false)
        }
    }

    async function joinSeat() {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        const values = await form.validateFields()
        setJoining(true)
        try {
            await joinClocktowerRoom(numericRoomId, values)
            message.success('已加入座位')
            await loadRoom()
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setJoining(false)
        }
    }

    async function leaveRoom() {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        setLeaving(true)
        try {
            await leaveClocktowerRoom(numericRoomId)
            message.success('已离开房间')
            await loadRoom()
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setLeaving(false)
        }
    }

    function openSeatEditor(seat: ClocktowerSeatResponse) {
        setSelectedSeat(seat)
        seatForm.setFieldsValue({displayName: seat.displayName, seatNo: seat.seatNo})
        setSeatEditorOpen(true)
    }

    function closeSeatEditor() {
        setSeatEditorOpen(false)
        setSelectedSeat(null)
        seatForm.resetFields()
    }

    async function saveSeatUpdate() {
        if (!selectedSeat || !Number.isFinite(numericRoomId)) {
            return
        }
        const values = await seatForm.validateFields()
        setUpdatingSeat(true)
        try {
            await updateClocktowerSeat(numericRoomId, selectedSeat.seatId, values)
            message.success('座位已更新')
            closeSeatEditor()
            await loadRoom()
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setUpdatingSeat(false)
        }
    }

    async function startGame() {
        if (!room || !Number.isFinite(numericRoomId)) {
            return
        }
        const assignments = room.seats
            .filter((seat) => seat.roleCode)
            .map((seat) => ({seatId: seat.seatId, roleCode: seat.roleCode as string}))
        setStarting(true)
        try {
            await startClocktowerGame(numericRoomId, {assignments, randomize: false})
            message.success('游戏已开始')
            navigate(`/clocktower/rooms/${numericRoomId}/grimoire`)
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setStarting(false)
        }
    }

    const filledCount = useMemo(() => room?.seats.filter((seat) => seat.userId).length ?? 0, [room])
    const seatsFilled = room?.seats.every((seat) => seat.userId) ?? false
    const rolesAssigned = room?.seats.every((seat) => seat.roleCode) ?? false
    const canStart = (room?.status === 'LOBBY' || room?.status === 'SETUP') && seatsFilled && rolesAssigned

    return (
        <>
            <PageToolbar
                actions={
                    <Space wrap>
                        <Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(loadRoom)}>刷新</Button>
                        <Button
                            danger
                            icon={<LogoutOutlined/>}
                            loading={leaving}
                            onClick={voidify(leaveRoom)}
                        >
                            离开房间
                        </Button>
                        <Button
                            icon={<PlayCircleOutlined/>}
                            loading={starting}
                            onClick={voidify(startGame)}
                            type="primary"
                            disabled={!canStart}
                        >
                            开始游戏
                        </Button>
                    </Space>
                }
                description={room ? `${room.roomCode} · ${room.scriptCode}` : '准备座位、加入玩家并开始游戏。'}
                title={room?.name ?? '房间大厅'}
            />
            <Row gutter={[16, 16]}>
                <Col lg={16} xs={24}>
                    <Card
                        extra={room && <Tag color="blue">{filledCount}/{room.playerCount}</Tag>}
                        loading={loading}
                        title="座位"
                    >
                        {room ? (
                            <SeatList seats={room.seats} onEdit={openSeatEditor}/>
                        ) : (
                            <Empty description="暂无房间数据"/>
                        )}
                    </Card>
                </Col>
                <Col lg={8} xs={24}>
                    <Card title="加入座位">
                        <Form form={form} layout="vertical">
                            <Form.Item label="座位号" name="seatNo">
                                <InputNumber min={1} max={room?.playerCount ?? 15} style={{width: '100%'}}/>
                            </Form.Item>
                            <Form.Item label="显示名称" name="displayName" rules={[{required: true, message: '请输入显示名称'}]}>
                                <Input/>
                            </Form.Item>
                            <Form.Item label="邀请码" name="inviteCode">
                                <Input/>
                            </Form.Item>
                            <Button
                                block
                                icon={<LoginOutlined/>}
                                loading={joining}
                                onClick={voidify(joinSeat)}
                                type="primary"
                            >
                                加入
                            </Button>
                        </Form>
                    </Card>
                    <Card style={{marginTop: 16}} title="房间状态">
                        <Space orientation="vertical">
                            <Tag color={room?.status === 'RUNNING' ? 'processing' : 'default'}>{room?.status ?? '-'}</Tag>
                            <Tag color="blue">{room?.phase ?? '-'}</Tag>
                            <Typography.Text type="secondary">说书人：{room?.storytellerUserId ?? '-'}</Typography.Text>
                            <Typography.Text type="secondary">
                                开始条件：{seatsFilled ? '座位已满' : '仍有空座'} · {rolesAssigned ? '角色已分配' : '角色未分配'}
                            </Typography.Text>
                        </Space>
                    </Card>
                </Col>
            </Row>
            <Modal
                confirmLoading={updatingSeat}
                onCancel={closeSeatEditor}
                onOk={voidify(saveSeatUpdate)}
                open={seatEditorOpen}
                title="调整座位"
            >
                <Form form={seatForm} layout="vertical">
                    <Form.Item label="显示名称" name="displayName" rules={[{required: true, message: '请输入显示名称'}]}>
                        <Input/>
                    </Form.Item>
                    <Form.Item label="座位号" name="seatNo">
                        <InputNumber min={1} max={room?.playerCount ?? 15} style={{width: '100%'}}/>
                    </Form.Item>
                </Form>
            </Modal>
        </>
    )
}

export function SeatList({seats, onEdit}: { seats: ClocktowerSeatResponse[]; onEdit: (seat: ClocktowerSeatResponse) => void }) {
    return (
        <List
            dataSource={seats}
            locale={{emptyText: <Empty description="暂无座位"/>}}
            renderItem={(seat) => (
                <List.Item
                    actions={[
                        <Button key="edit" icon={<EditOutlined/>} onClick={() => onEdit(seat)}>
                            调整座位
                        </Button>,
                    ]}
                >
                    <Space align="center" wrap>
                        <Button aria-label={`选择 ${seat.seatNo} 号座位 ${seat.displayName}`} shape="circle">
                            {seat.seatNo}
                        </Button>
                        <Typography.Text strong>{seat.displayName || '未入座'}</Typography.Text>
                        <Tag color={seat.connected ? 'success' : 'default'}>{seat.connected ? '在线' : '离线'}</Tag>
                        <Tag color={seat.lifeStatus === 'ALIVE' ? 'success' : 'error'}>{seat.lifeStatus}</Tag>
                        <RoleTypeTag value={seat.roleType}/>
                        {seat.roleCode && <Tag>{seat.roleCode}</Tag>}
                        {seat.hasDeadVote ? <Tag color="warning">死票可用</Tag> : <Tag>死票已用</Tag>}
                    </Space>
                </List.Item>
            )}
        />
    )
}

export const Component = RoomLobbyPage
