import {BulbOutlined, FolderOpenOutlined, InboxOutlined, ReloadOutlined} from '@ant-design/icons'
import {App, Button, Card, Form, Select, Space, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {useNavigate} from 'react-router'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {reportGlobalError} from '../../app/globalError'
import {DataTable} from '../../components/DataTable'
import {EmptyState} from '../../components/EmptyState'
import {FilterBar} from '../../components/FilterBar'
import {PageToolbar} from '../../components/PageToolbar'
import {RowActions} from '../../components/RowActions'
import {StackedCell} from '../../components/StackedCell'
import {canAccessAdminPath} from '../../layouts/AdminLayout/menu'
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

type MemorySessionFilters = {
    entryType?: AgentMemoryEntryType
    status?: AgentMemorySessionStatus
}

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
            reportGlobalError(requestError)
        } finally {
            setLoading(false)
        }
    }, [entryType, status])

    useEffect(() => {
        void load()
    }, [load])

    async function archive(record: AgentMemorySessionResponse) {
        try {
            await archiveAgentMemorySession(record.sessionId)
            message.success('会话已归档')
            await load()
        } catch (requestError) {
            reportGlobalError(requestError)
        }
    }

    function applyFilters(values: MemorySessionFilters) {
        setEntryType(values.entryType)
        setStatus(values.status)
    }

    const columns = useMemo<ColumnsType<AgentMemorySessionResponse>>(() => [
        {
            title: '会话',
            dataIndex: 'title',
            render: (_, record) => (
                <StackedCell
                    primary={record.title || record.sessionId}
                    secondary={record.title ? record.sessionId : undefined}
                />
            ),
        },
        {title: '入口', dataIndex: 'entryType', width: 140, render: (value) => <Tag color="blue">{value}</Tag>},
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (value) => <Tag color={value === 'ACTIVE' ? 'success' : 'default'}>{value}</Tag>,
        },
        {
            title: '长期记忆',
            dataIndex: 'memoryContextEnabled',
            width: 180,
            render: (_, record) => {
                const contextEnabled = record.memoryContextEnabled ?? record.memoryEnabled
                return (
                    <Space size={4} wrap>
                        <Tag color={contextEnabled ? 'green' : 'default'}>
                            上下文{contextEnabled ? '开启' : '关闭'}
                        </Tag>
                        <Tag color={record.longTermExtractionEnabled ? 'purple' : 'default'}>
                            提取{record.longTermExtractionEnabled ? '开启' : '关闭'}
                        </Tag>
                    </Space>
                )
            },
        },
        {title: '最后活跃', dataIndex: 'lastActiveAt', width: 180, render: (value: string | undefined) => value || '-'},
        {
            title: '操作',
            fixed: 'right',
            width: 100,
            render: (_, record) => (
                <RowActions
                    actions={[
                        {
                            key: 'archive',
                            label: '归档',
                            icon: <InboxOutlined/>,
                            hidden: !canArchive,
                            onClick: () => void archive(record),
                        },
                    ]}
                />
            ),
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
                icon={<BulbOutlined/>}
                title="记忆管理"
            />
            <Card loading={loading} title="长期记忆">
                {longTerm?.contentMarkdown ? (
                    <div className="message-content">
                        <ReactMarkdown remarkPlugins={markdownPlugins}>
                            {longTerm.contentMarkdown}
                        </ReactMarkdown>
                    </div>
                ) : (
                    <EmptyState
                        description="继续在 Agent 或 RAG 对话中开启长期提取，系统会在会话结束后写入长期记忆。"
                        inline
                        title="还没有长期记忆"
                    />
                )}
                <Space wrap>
                    <Tag>{longTerm?.status ?? 'UNKNOWN'}</Tag>
                    <Tag>{longTerm?.contentChars ?? 0} chars</Tag>
                    {longTerm?.updatedAt && <Tag>{longTerm.updatedAt}</Tag>}
                </Space>
            </Card>
            <FilterBar<MemorySessionFilters>
                instant
                loading={loading}
                onReset={() => applyFilters({})}
                onSearch={applyFilters}
            >
                <Form.Item label="入口" name="entryType">
                    <Select allowClear options={entryOptions} placeholder="全部入口"/>
                </Form.Item>
                <Form.Item label="状态" name="status">
                    <Select allowClear options={statusOptions} placeholder="全部状态"/>
                </Form.Item>
            </FilterBar>
            <DataTable<AgentMemorySessionResponse>
                columns={columns}
                count={sessions.length}
                dataSource={sessions}
                emptyDescription="换个入口或状态筛选，已归档的会话请到“归档会话”页面查看。"
                emptyTitle="没有未归档的会话"
                loading={loading}
                pagination={false}
                rowKey="sessionId"
                scroll={{x: 1000}}
                title="会话"
            />
        </>
    )
}

export const Component = AgentMemoryPage
