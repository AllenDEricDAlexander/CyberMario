import {PlusOutlined, ReloadOutlined} from '@ant-design/icons'
import {App, Button, Card, Form, Input, InputNumber, Modal, Select, Space, Switch, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useState} from 'react'
import {useNavigate} from 'react-router'
import {reportGlobalError} from '../../app/globalError'
import {PageToolbar} from '../../components/PageToolbar'
import {voidify} from '../../utils/async'
import {createClocktowerRoom, listClocktowerBoards, listClocktowerRooms} from './clocktowerService'
import {RoleSummaryTags} from './components/RoleSummaryTags'
import type {
    ClocktowerBoardConfigResponse,
    ClocktowerRoomCreateRequest,
    ClocktowerRoomResponse,
    ClocktowerRoomStatus,
    ClocktowerScriptCode,
    ClocktowerVisibility,
} from './clocktowerTypes'

const scriptOptions: Array<{ label: string; value: ClocktowerScriptCode }> = [
    {label: '暗流涌动', value: 'TROUBLE_BREWING'},
    {label: '黯月初升', value: 'BAD_MOON_RISING'},
    {label: '教派紫罗兰', value: 'SECTS_AND_VIOLETS'},
]

const seatingPolicyOptions = [
    {label: '自由入座 / OPEN_SEATING', value: 'OPEN_SEATING'},
]

export const roomCreateInitialValues: ClocktowerRoomCreateRequest = {
    name: '钟楼房间',
    scriptCode: 'TROUBLE_BREWING',
    playerCount: 5,
    boardConfigId: null,
    boardCode: null,
    roleCodes: [],
    storytellerMode: 'HUMAN',
    seatingPolicy: 'OPEN_SEATING',
    allowSpectators: true,
    allowPrivateChat: true,
    agentSeatCount: 0,
}

const statusColors: Record<ClocktowerRoomStatus, string> = {
    LOBBY: 'default',
    SETUP: 'warning',
    RUNNING: 'processing',
    ENDED: 'success',
    ARCHIVED: 'default',
}

const visibilityLabels: Record<ClocktowerVisibility, string> = {
    PUBLIC: '公开',
    PRIVATE: '私有',
    STORYTELLER: '说书人',
    AUDIT: '审计',
}

function RoomListPage() {
    const navigate = useNavigate()
    const {message} = App.useApp()
    const [form] = Form.useForm<ClocktowerRoomCreateRequest>()
    const selectedBoardId = Form.useWatch('boardConfigId', form)
    const [rooms, setRooms] = useState<ClocktowerRoomResponse[]>([])
    const [boards, setBoards] = useState<ClocktowerBoardConfigResponse[]>([])
    const [loading, setLoading] = useState(false)
    const [boardLoading, setBoardLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [creatorOpen, setCreatorOpen] = useState(false)
    const selectedBoard = boards.find((board) => board.boardId === selectedBoardId)

    const loadRooms = useCallback(async () => {
        setLoading(true)
        try {
            setRooms(await listClocktowerRooms())
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setLoading(false)
        }
    }, [])

    useEffect(() => {
        void loadRooms()
    }, [loadRooms])

    function openCreator() {
        form.setFieldsValue({...roomCreateInitialValues, roleCodes: [...roomCreateInitialValues.roleCodes]})
        setBoards([])
        setCreatorOpen(true)
        void loadValidBoards()
    }

    async function loadValidBoards() {
        setBoardLoading(true)
        try {
            const page = await listClocktowerBoards({page: 1, size: 200, valid: true})
            setBoards(page.records)
        } catch (caught) {
            setBoards([])
            reportGlobalError(caught)
        } finally {
            setBoardLoading(false)
        }
    }

    function selectBoard(boardConfigId?: number | null) {
        if (boardConfigId == null) {
            form.setFieldsValue({
                boardConfigId: null,
                boardCode: null,
                roleCodes: [],
            })
            return
        }

        const board = boards.find((item) => item.boardId === boardConfigId)
        if (!board) {
            return
        }

        form.setFieldsValue({
            boardConfigId: board.boardId,
            boardCode: board.boardCode,
            scriptCode: board.scriptCode,
            playerCount: board.playerCount,
            roleCodes: [],
        })
    }

    async function saveRoom() {
        const values = await form.validateFields()
        setSaving(true)
        try {
            const room = await createClocktowerRoom({
                ...values,
                roleCodes: values.roleCodes ?? [],
                seatingPolicy: values.seatingPolicy ?? roomCreateInitialValues.seatingPolicy,
            })
            message.success('房间已创建')
            setCreatorOpen(false)
            await loadRooms()
            void navigate(`/clocktower/rooms/${room.roomId}/lobby`)
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setSaving(false)
        }
    }

    const columns = createRoomListColumns((path) => {
        void navigate(path)
    })

    return (
        <>
            <PageToolbar
                actions={
                    <Space wrap>
                        <Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(loadRooms)}>刷新</Button>
                        <Button icon={<PlusOutlined/>} onClick={openCreator} type="primary">创建房间</Button>
                    </Space>
                }
                description="创建房间、进入大厅并跳转到玩家或说书人视图。"
                title="钟楼房间"
            />
            <Card>
                <Table
                    columns={columns}
                    dataSource={rooms}
                    loading={loading}
                    rowKey="roomId"
                    scroll={{x: 1200}}
                />
            </Card>
            <Modal
                confirmLoading={saving}
                onCancel={() => setCreatorOpen(false)}
                onOk={voidify(saveRoom)}
                open={creatorOpen}
                title="创建钟楼房间"
            >
                <Form form={form} layout="vertical">
                    <Form.Item hidden name="boardCode">
                        <Select options={[]}/>
                    </Form.Item>
                    <Form.Item hidden name="roleCodes">
                        <Select mode="multiple" options={[]}/>
                    </Form.Item>
                    <Form.Item label="房间名称" name="name" rules={[{required: true, message: '请输入房间名称'}]}>
                        <Input/>
                    </Form.Item>
                    <Form.Item label="剧本" name="scriptCode" rules={[{required: true, message: '请选择剧本'}]}>
                        <Select options={scriptOptions}/>
                    </Form.Item>
                    <Space align="start" wrap>
                        <Form.Item label="人数" name="playerCount" rules={[{required: true}]}>
                            <InputNumber min={5} max={15}/>
                        </Form.Item>
                        <Form.Item label="Agent 座位数" name="agentSeatCount">
                            <InputNumber min={0} max={15}/>
                        </Form.Item>
                    </Space>
                    <Form.Item label="说书人模式" name="storytellerMode">
                        <Select options={[{label: '人工说书人', value: 'HUMAN'}]}/>
                    </Form.Item>
                    <Form.Item label="入座策略" name="seatingPolicy" rules={[{required: true, message: '请选择入座策略'}]}>
                        <Select options={seatingPolicyOptions}/>
                    </Form.Item>
                    <Form.Item label="通过配板" name="boardConfigId">
                        <Select
                            allowClear
                            loading={boardLoading}
                            onChange={selectBoard}
                            options={boards.map((board) => ({
                                label: `${board.boardCode} · ${board.scriptCode} · ${board.playerCount}人`,
                                value: board.boardId,
                            }))}
                            placeholder="可选，只展示校验通过的配板"
                        />
                    </Form.Item>
                    {selectedBoard && (
                        <div style={{marginTop: -12, marginBottom: 16}}>
                            <RoleSummaryTags roleCodes={selectedBoard.roleCodes} roles={selectedBoard.roles}/>
                        </div>
                    )}
                    <Space align="start" wrap>
                        <Form.Item label="允许旁观" name="allowSpectators" valuePropName="checked">
                            <Switch/>
                        </Form.Item>
                        <Form.Item label="允许私聊" name="allowPrivateChat" valuePropName="checked">
                            <Switch/>
                        </Form.Item>
                    </Space>
                </Form>
            </Modal>
        </>
    )
}

export const Component = RoomListPage

export function createRoomListColumns(navigate: (path: string) => void): ColumnsType<ClocktowerRoomResponse> {
    return [
        {
            title: '房间',
            dataIndex: 'name',
            key: 'room',
            width: 220,
            render: (_, record) => (
                <Space orientation="vertical" size={0}>
                    <span>{record.name}</span>
                    <Tag>{record.roomCode}</Tag>
                </Space>
            ),
        },
        {title: '剧本', dataIndex: 'scriptCode', key: 'script', width: 180},
        {
            title: '可见性',
            dataIndex: 'visibility',
            key: 'visibility',
            width: 120,
            render: (value: ClocktowerRoomResponse['visibility']) => {
                if (!value) {
                    return <Tag>未提供</Tag>
                }
                return <Tag color={value === 'PRIVATE' ? 'warning' : 'success'}>{visibilityLabels[value]}</Tag>
            },
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            width: 120,
            render: (value: ClocktowerRoomStatus) => <Tag color={statusColors[value]}>{value}</Tag>,
        },
        {
            title: '玩家',
            key: 'players',
            width: 120,
            render: (_, record) => {
                const counts = roomListCounts(record)
                return `${counts.occupied}/${counts.required}`
            },
        },
        {
            title: '预留',
            key: 'reserved',
            width: 100,
            render: (_, record) => roomListCounts(record).reserved,
        },
        {
            title: '说书人',
            dataIndex: 'storytellerUserId',
            key: 'storyteller',
            width: 120,
            render: (value: ClocktowerRoomResponse['storytellerUserId']) => value ?? '-',
        },
        {
            title: '操作',
            fixed: 'right',
            key: 'actions',
            width: 260,
            render: (_, record) => (
                <Space>
                    <Button size="small" onClick={() => {
                        navigate(`/clocktower/rooms/${record.roomId}/lobby`)
                    }}>
                        大厅
                    </Button>
                    <Button size="small" onClick={() => {
                        navigate(`/clocktower/rooms/${record.roomId}/play`)
                    }}>
                        游戏
                    </Button>
                    <Button size="small" onClick={() => {
                        navigate(`/clocktower/replays/${record.roomId}`)
                    }}>
                        回放
                    </Button>
                </Space>
            ),
        },
    ]
}

export function roomListCounts(room: ClocktowerRoomResponse) {
    return {
        occupied: room.seats.filter((seat) => Boolean(seat.userId)).length,
        reserved: room.reservations?.length ?? 0,
        required: room.playerCount,
    }
}
