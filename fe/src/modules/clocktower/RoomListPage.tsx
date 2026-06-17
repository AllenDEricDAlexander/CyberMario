import {PlusOutlined, ReloadOutlined} from '@ant-design/icons'
import {App, Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useEffect, useState} from 'react'
import {useNavigate} from 'react-router'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {createClocktowerRoom, listClocktowerRooms} from './clocktowerService'
import type {
    ClocktowerRoomCreateRequest,
    ClocktowerRoomResponse,
    ClocktowerRoomStatus,
    ClocktowerScriptCode,
} from './clocktowerTypes'

type RoomFormValues = Omit<ClocktowerRoomCreateRequest, 'roleCodes'> & {
    roleCodesText?: string
}

const scriptOptions: Array<{ label: string; value: ClocktowerScriptCode }> = [
    {label: '暗流涌动', value: 'TROUBLE_BREWING'},
    {label: '黯月初升', value: 'BAD_MOON_RISING'},
    {label: '教派紫罗兰', value: 'SECTS_AND_VIOLETS'},
]

const statusColors: Record<ClocktowerRoomStatus, string> = {
    LOBBY: 'default',
    SETUP: 'warning',
    RUNNING: 'processing',
    ENDED: 'success',
    ARCHIVED: 'default',
}

function RoomListPage() {
    const navigate = useNavigate()
    const {message} = App.useApp()
    const [form] = Form.useForm<RoomFormValues>()
    const [rooms, setRooms] = useState<ClocktowerRoomResponse[]>([])
    const [loading, setLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [creatorOpen, setCreatorOpen] = useState(false)

    useEffect(() => {
        void loadRooms()
    }, [])

    async function loadRooms() {
        setLoading(true)
        try {
            setRooms(await listClocktowerRooms())
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setLoading(false)
        }
    }

    function openCreator() {
        form.setFieldsValue({
            name: '钟楼房间',
            scriptCode: 'TROUBLE_BREWING',
            playerCount: 5,
            storytellerMode: 'HUMAN',
            allowSpectators: true,
            allowPrivateChat: true,
            agentSeatCount: 0,
        })
        setCreatorOpen(true)
    }

    async function saveRoom() {
        const values = await form.validateFields()
        setSaving(true)
        try {
            const {roleCodesText, ...request} = values
            const room = await createClocktowerRoom({
                ...request,
                roleCodes: parseRoleCodes(roleCodesText),
            })
            message.success('房间已创建')
            setCreatorOpen(false)
            await loadRooms()
            navigate(`/clocktower/rooms/${room.roomId}/lobby`)
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setSaving(false)
        }
    }

    const columns: ColumnsType<ClocktowerRoomResponse> = [
        {
            title: '房间',
            dataIndex: 'name',
            width: 220,
            render: (_, record) => (
                <Space orientation="vertical" size={0}>
                    <span>{record.name}</span>
                    <Tag>{record.roomCode}</Tag>
                </Space>
            ),
        },
        {title: '剧本', dataIndex: 'scriptCode', width: 180},
        {
            title: '状态',
            dataIndex: 'status',
            width: 120,
            render: (value: ClocktowerRoomStatus) => <Tag color={statusColors[value]}>{value}</Tag>,
        },
        {title: '阶段', dataIndex: 'phase', width: 130, render: (value) => <Tag color="blue">{value}</Tag>},
        {
            title: '座位',
            width: 120,
            render: (_, record) => `${record.seats.filter((seat) => seat.userId).length}/${record.playerCount}`,
        },
        {title: '说书人', dataIndex: 'storytellerUserId', width: 120, render: (value) => value ?? '-'},
        {
            title: '操作',
            fixed: 'right',
            width: 300,
            render: (_, record) => (
                <Space>
                    <Button size="small" onClick={() => navigate(`/clocktower/rooms/${record.roomId}/lobby`)}>
                        大厅
                    </Button>
                    <Button size="small" onClick={() => navigate(`/clocktower/rooms/${record.roomId}/play`)}>
                        游戏
                    </Button>
                    <Button size="small" onClick={() => navigate(`/clocktower/rooms/${record.roomId}/grimoire`)}>
                        魔典
                    </Button>
                    <Button size="small" onClick={() => navigate(`/clocktower/replays/${record.roomId}`)}>
                        回放
                    </Button>
                    <Popconfirm title="归档接口将在后续计划实现">
                        <Button disabled size="small">归档</Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ]

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
                    <Form.Item label="预设角色代码" name="roleCodesText">
                        <Input.TextArea placeholder="可选，用逗号或换行分隔角色代码" rows={3}/>
                    </Form.Item>
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

function parseRoleCodes(text?: string) {
    return (text ?? '')
        .split(/[,\n]/)
        .map((item) => item.trim())
        .filter(Boolean)
}

export const Component = RoomListPage
