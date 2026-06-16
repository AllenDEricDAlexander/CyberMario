import {DeleteOutlined, ReloadOutlined, RollbackOutlined} from '@ant-design/icons'
import {App, Button, Popconfirm, Space, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {deleteAgentMemorySession, getAgentMemorySessions, restoreAgentMemorySession} from './agentService'
import type {AgentMemorySessionResponse} from './agentTypes'
import {memoryButtonCodes} from './memoryPermissionCodes'

function AgentMemoryArchivePage() {
    const {message} = App.useApp()
    const auth = useAuth()
    const [sessions, setSessions] = useState<AgentMemorySessionResponse[]>([])
    const [loading, setLoading] = useState(false)
    const canRestore = canUseRbacButton(auth, memoryButtonCodes.session.restore)
    const canDelete = canUseRbacButton(auth, memoryButtonCodes.session.delete)

    const load = useCallback(async () => {
        setLoading(true)
        try {
            const page = await getAgentMemorySessions({page: 1, size: 100, status: 'ARCHIVED'})
            setSessions(page.records)
        } catch (requestError) {
            message.error(resolveErrorMessage(requestError))
        } finally {
            setLoading(false)
        }
    }, [message])

    useEffect(() => {
        void load()
    }, [load])

    async function restore(record: AgentMemorySessionResponse) {
        try {
            await restoreAgentMemorySession(record.sessionId)
            message.success('会话已恢复')
            await load()
        } catch (requestError) {
            message.error(resolveErrorMessage(requestError))
        }
    }

    async function remove(record: AgentMemorySessionResponse) {
        try {
            await deleteAgentMemorySession(record.sessionId)
            message.success('会话已删除')
            await load()
        } catch (requestError) {
            message.error(resolveErrorMessage(requestError))
        }
    }

    const columns = useMemo<ColumnsType<AgentMemorySessionResponse>>(() => [
        {title: '标题', dataIndex: 'title', render: (_, record) => record.title || record.sessionId},
        {title: '入口', dataIndex: 'entryType', width: 140, render: (value) => <Tag color="blue">{value}</Tag>},
        {title: '归档时间', dataIndex: 'archivedAt', width: 180, render: (value) => value || '-'},
        {
            title: '操作',
            fixed: 'right',
            width: 180,
            render: (_, record) => (
                <Space>
                    {canRestore && (
                        <Button icon={<RollbackOutlined/>} size="small" onClick={() => void restore(record)}>
                            恢复
                        </Button>
                    )}
                    {canDelete && (
                        <Popconfirm title="删除这个归档会话？" onConfirm={() => void remove(record)}>
                            <Button danger icon={<DeleteOutlined/>} size="small">
                                删除
                            </Button>
                        </Popconfirm>
                    )}
                </Space>
            ),
        },
    ], [canDelete, canRestore])

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(load)}>刷新</Button>}
                description="查看和处理已归档的当前用户会话。"
                title="归档会话"
            />
            <Table<AgentMemorySessionResponse>
                columns={columns}
                dataSource={sessions}
                loading={loading}
                pagination={false}
                rowKey="sessionId"
                scroll={{x: 760}}
            />
        </>
    )
}

export const Component = AgentMemoryArchivePage
