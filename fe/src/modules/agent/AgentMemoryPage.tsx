import {FolderOpenOutlined, InboxOutlined, ReloadOutlined} from '@ant-design/icons'
import {App, Button, Card, Select, Space, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {useNavigate} from 'react-router'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {PageToolbar} from '../../components/PageToolbar'
import {canAccessAdminPath} from '../../layouts/AdminLayout/menu'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {canUseRbacButton, hasAdminPermissionBypass, useAuth} from '../auth/authStore'
import {archiveAgentMemorySession, getAgentLongTermMemory, getAgentMemorySessions} from './agentService'
import type {
    AgentLongTermMemoryResponse,
    AgentMemoryEntryType,
    AgentMemorySessionResponse,
    AgentMemorySessionStatus,
} from './agentTypes'
import {memoryButtonCodes} from './memoryPermissionCodes'

const markdownPlugins = [remarkGfm]
const entryOptions: Array<{ label: string; value: AgentMemoryEntryType }> = [
    {label: 'Agent Chat', value: 'AGENT_CHAT'},
    {label: 'Agent Debug', value: 'AGENT_DEBUG'},
    {label: 'RAG Chat', value: 'RAG_CHAT'},
]
const statusOptions: Array<{ label: string; value: AgentMemorySessionStatus }> = [
    {label: '活跃', value: 'ACTIVE'},
    {label: '已释放', value: 'RELEASED'},
]

function AgentMemoryPage() {
    const {message} = App.useApp()
    const navigate = useNavigate()
    const auth = useAuth()
    const [longTerm, setLongTerm] = useState<AgentLongTermMemoryResponse>()
    const [sessions, setSessions] = useState<AgentMemorySessionResponse[]>([])
    const [entryType, setEntryType] = useState<AgentMemoryEntryType | undefined>()
    const [status, setStatus] = useState<AgentMemorySessionStatus | undefined>()
    const [loading, setLoading] = useState(false)
    const canArchive = canUseRbacButton(auth, memoryButtonCodes.session.archive)
    const canOpenArchive = canAccessAdminPath(
        '/agent/memory/archive',
        auth.menus,
        hasAdminPermissionBypass(auth),
        auth.roleCodes,
    )

    const load = useCallback(async () => {
        setLoading(true)
        try {
            const [memory, sessionPage] = await Promise.all([
                getAgentLongTermMemory(),
                getAgentMemorySessions({page: 1, size: 100, entryType, status}),
            ])
            setLongTerm(memory)
            setSessions(sessionPage.records)
        } catch (requestError) {
            message.error(resolveErrorMessage(requestError))
        } finally {
            setLoading(false)
        }
    }, [entryType, message, status])

    useEffect(() => {
        void load()
    }, [load])

    async function archive(record: AgentMemorySessionResponse) {
        try {
            await archiveAgentMemorySession(record.sessionId)
            message.success('会话已归档')
            await load()
        } catch (requestError) {
            message.error(resolveErrorMessage(requestError))
        }
    }

    const columns = useMemo<ColumnsType<AgentMemorySessionResponse>>(() => [
        {title: '标题', dataIndex: 'title', render: (_, record) => record.title || record.sessionId},
        {title: '入口', dataIndex: 'entryType', width: 140, render: (value) => <Tag color="blue">{value}</Tag>},
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (value) => <Tag color={value === 'ACTIVE' ? 'success' : 'default'}>{value}</Tag>,
        },
        {
            title: 'Memory',
            dataIndex: 'memoryEnabled',
            width: 100,
            render: (value) => <Tag color={value ? 'green' : 'default'}>{value ? 'ON' : 'OFF'}</Tag>,
        },
        {
            title: '长期提取',
            dataIndex: 'longTermExtractionEnabled',
            width: 110,
            render: (value) => <Tag color={value ? 'purple' : 'default'}>{value ? 'ON' : 'OFF'}</Tag>,
        },
        {title: '最后活跃', dataIndex: 'lastActiveAt', width: 180, render: (value) => value || '-'},
        {
            title: '操作',
            fixed: 'right',
            width: 120,
            render: (_, record) => canArchive ? (
                <Button icon={<InboxOutlined/>} size="small" onClick={() => void archive(record)}>
                    归档
                </Button>
            ) : '-',
        },
    ], [canArchive])

    return (
        <>
            <PageToolbar
                actions={(
                    <>
                        {canOpenArchive && (
                            <Button icon={<FolderOpenOutlined/>}
                                    onClick={() => void navigate('/agent/memory/archive')}>
                                归档会话
                            </Button>
                        )}
                        <Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(load)}>刷新</Button>
                    </>
                )}
                description="查看当前用户的长期记忆和未归档会话。"
                title="记忆管理"
            />
            <Card loading={loading} title="长期记忆">
                <div className="message-content">
                    <ReactMarkdown remarkPlugins={markdownPlugins}>
                        {longTerm?.contentMarkdown || ''}
                    </ReactMarkdown>
                </div>
                <Space wrap>
                    <Tag>{longTerm?.status ?? 'UNKNOWN'}</Tag>
                    <Tag>{longTerm?.contentChars ?? 0} chars</Tag>
                    {longTerm?.updatedAt && <Tag>{longTerm.updatedAt}</Tag>}
                </Space>
            </Card>
            <Card
                style={{marginTop: 16}}
                title="会话"
                extra={(
                    <Space wrap>
                        <Select
                            allowClear
                            options={entryOptions}
                            placeholder="入口"
                            style={{width: 150}}
                            value={entryType}
                            onChange={setEntryType}
                        />
                        <Select
                            allowClear
                            options={statusOptions}
                            placeholder="状态"
                            style={{width: 130}}
                            value={status}
                            onChange={setStatus}
                        />
                    </Space>
                )}
            >
                <Table<AgentMemorySessionResponse>
                    columns={columns}
                    dataSource={sessions}
                    loading={loading}
                    pagination={false}
                    rowKey="sessionId"
                    scroll={{x: 1000}}
                />
            </Card>
        </>
    )
}

export const Component = AgentMemoryPage
