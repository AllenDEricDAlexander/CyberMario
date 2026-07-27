import {DeleteOutlined, InboxOutlined, ReloadOutlined, RollbackOutlined} from '@ant-design/icons'
import {App, Button, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {reportGlobalError} from '../../app/globalError'
import {DataTable} from '../../components/DataTable'
import {PageToolbar} from '../../components/PageToolbar'
import {RowActions} from '../../components/RowActions'
import {StackedCell} from '../../components/StackedCell'
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
            reportGlobalError(requestError)
        } finally {
            setLoading(false)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    async function restore(record: AgentMemorySessionResponse) {
        try {
            await restoreAgentMemorySession(record.sessionId)
            message.success('会话已恢复')
            await load()
        } catch (requestError) {
            reportGlobalError(requestError)
        }
    }

    async function remove(record: AgentMemorySessionResponse) {
        try {
            await deleteAgentMemorySession(record.sessionId)
            message.success('会话已删除')
            await load()
        } catch (requestError) {
            reportGlobalError(requestError)
        }
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
        {title: '归档时间', dataIndex: 'archivedAt', width: 180, render: (value: string | undefined) => value || '-'},
        {
            title: '操作',
            fixed: 'right',
            width: 160,
            render: (_, record) => (
                <RowActions
                    actions={[
                        {
                            key: 'restore',
                            label: '恢复',
                            icon: <RollbackOutlined/>,
                            hidden: !canRestore,
                            onClick: () => void restore(record),
                        },
                        {
                            key: 'delete',
                            label: '删除',
                            icon: <DeleteOutlined/>,
                            danger: true,
                            hidden: !canDelete,
                            confirm: '删除这个归档会话？删除后无法恢复。',
                            onClick: () => void remove(record),
                        },
                    ]}
                />
            ),
        },
    ], [canDelete, canRestore])

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(load)}>刷新</Button>}
                description="查看和处理已归档的当前用户会话。"
                icon={<InboxOutlined/>}
                title="归档会话"
            />
            <DataTable<AgentMemorySessionResponse>
                columns={columns}
                count={sessions.length}
                dataSource={sessions}
                emptyDescription="在“记忆管理”里归档会话后，它们会出现在这里，可以恢复或彻底删除。"
                emptyTitle="没有归档会话"
                loading={loading}
                pagination={false}
                rowKey="sessionId"
                scroll={{x: 760}}
                title="归档列表"
            />
        </>
    )
}

export const Component = AgentMemoryArchivePage
