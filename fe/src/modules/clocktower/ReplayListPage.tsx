import {ReloadOutlined} from '@ant-design/icons'
import {Button, Card, Empty, Space, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useEffect, useState} from 'react'
import {useNavigate} from 'react-router'
import {reportGlobalError} from '../../app/globalError'
import {PageToolbar} from '../../components/PageToolbar'
import {voidify} from '../../utils/async'
import {listClocktowerRooms} from './clocktowerService'
import type {ClocktowerRoomResponse, ClocktowerRoomStatus} from './clocktowerTypes'

const statusColors: Record<ClocktowerRoomStatus, string> = {
    LOBBY: 'default',
    SETUP: 'warning',
    RUNNING: 'processing',
    ENDED: 'success',
    ARCHIVED: 'default',
}

function ReplayListPage() {
    const navigate = useNavigate()
    const [rooms, setRooms] = useState<ClocktowerRoomResponse[]>([])
    const [loading, setLoading] = useState(false)

    useEffect(() => {
        void loadRooms()
    }, [])

    async function loadRooms() {
        setLoading(true)
        try {
            setRooms(await listClocktowerRooms())
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setLoading(false)
        }
    }

    const columns: ColumnsType<ClocktowerRoomResponse> = [
        {
            title: '房间',
            dataIndex: 'name',
            width: 240,
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
            title: '操作',
            fixed: 'right',
            width: 120,
            render: (_, record) => (
                <Button size="small" type="primary" onClick={() => void navigate(`/clocktower/replays/${record.roomId}`)}>
                    查看回放
                </Button>
            ),
        },
    ]

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(loadRooms)}>刷新</Button>}
                description="从已有房间进入事件回放与投票复盘。"
                title="钟楼回放复盘"
            />
            <Card>
                <Table
                    columns={columns}
                    dataSource={rooms}
                    loading={loading}
                    locale={{emptyText: <Empty description="暂无房间可查看回放"/>}}
                    rowKey="roomId"
                    scroll={{x: 840}}
                />
            </Card>
        </>
    )
}

export const Component = ReplayListPage
