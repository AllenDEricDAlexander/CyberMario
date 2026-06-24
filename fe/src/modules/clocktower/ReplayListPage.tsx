import {EyeOutlined, ReloadOutlined} from '@ant-design/icons'
import {Button, Card, Empty, Space, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useEffect, useState} from 'react'
import {useNavigate} from 'react-router'
import {reportGlobalError} from '../../app/globalError'
import {DateTimeText} from '../../components/DateTimeText'
import {PageToolbar} from '../../components/PageToolbar'
import {voidify} from '../../utils/async'
import {listClocktowerGameHistory} from './clocktowerService'
import type {ClocktowerGameHistoryResponse} from './clocktowerTypes'

function ReplayListPage() {
    const navigate = useNavigate()
    const [games, setGames] = useState<ClocktowerGameHistoryResponse[]>([])
    const [loading, setLoading] = useState(false)

    useEffect(() => {
        void loadGames()
    }, [])

    async function loadGames() {
        setLoading(true)
        try {
            setGames(await listClocktowerGameHistory())
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setLoading(false)
        }
    }

    const columns: ColumnsType<ClocktowerGameHistoryResponse> = [
        {title: '游戏编号', dataIndex: 'gameNo', width: 110, render: (value: number) => <Tag>#{value}</Tag>},
        {
            title: '房间 / 游戏',
            key: 'identity',
            width: 180,
            render: (_, record) => (
                <Space orientation="vertical" size={0}>
                    <span>房间 #{record.roomId}</span>
                    <Tag>gameId={record.gameId}</Tag>
                </Space>
            ),
        },
        {title: '剧本', dataIndex: 'scriptCode', width: 180, render: valueOrDash},
        {
            title: '状态',
            dataIndex: 'status',
            width: 120,
            render: (value: string) => <Tag color={statusColor(value)}>{value}</Tag>,
        },
        {title: '阶段', dataIndex: 'phase', width: 130, render: (value: string) => <Tag color="blue">{value}</Tag>},
        {title: '开始时间', dataIndex: 'startedAt', width: 180, render: renderDateTime},
        {title: '结束时间', dataIndex: 'endedAt', width: 180, render: renderDateTime},
        {title: '身份 / 权限', key: 'access', width: 120, render: () => '-'},
        {
            title: '操作',
            fixed: 'right',
            width: 120,
            render: (_, record) => (
                <Button
                    icon={<EyeOutlined/>}
                    onClick={() => void navigate(`/clocktower/games/${record.gameId}/replay`)}
                    size="small"
                    type="primary"
                >
                    查看回放
                </Button>
            ),
        },
    ]

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(loadGames)}>刷新</Button>}
                description="按游戏记录查看钟楼事件回放。房间名称与身份信息当前未包含在历史接口中。"
                title="钟楼回放复盘"
            />
            <Card>
                <Table<ClocktowerGameHistoryResponse>
                    columns={columns}
                    dataSource={games}
                    loading={loading}
                    locale={{emptyText: <Empty description="暂无游戏历史可查看回放"/>}}
                    rowKey="gameId"
                    scroll={{x: 1300}}
                />
            </Card>
        </>
    )
}

function renderDateTime(value?: string | null) {
    return <DateTimeText value={value}/>
}

function valueOrDash(value?: string | number | null) {
    return value ?? '-'
}

function statusColor(status: string) {
    if (status === 'RUNNING') {
        return 'processing'
    }
    if (status === 'ENDED') {
        return 'success'
    }
    if (status === 'CANCELLED' || status === 'FAILED') {
        return 'error'
    }
    return 'default'
}

export const Component = ReplayListPage
