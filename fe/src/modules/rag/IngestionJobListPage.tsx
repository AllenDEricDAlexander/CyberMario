import {ReloadOutlined, StopOutlined} from '@ant-design/icons'
import {App, Button, Popconfirm, Progress, Space, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {usePageData} from '../../hooks/usePageData'
import {voidify} from '../../utils/async'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {ragButtonCodes} from './ragPermissionCodes'
import {cancelRagIngestionJob, getRagIngestionJobs, retryRagIngestionJob} from './ragService'
import type {RagIngestionJobResponse} from './ragTypes'

function IngestionJobListPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const canRetry = canUseRbacButton(auth, ragButtonCodes.job.retry)
    const canCancel = canUseRbacButton(auth, ragButtonCodes.job.cancel)
    const loadJobs = useCallback(
        (request: { page: number; size: number }) => getRagIngestionJobs(request),
        [],
    )
    const {loading, records, page, size, total, load} = usePageData<RagIngestionJobResponse>(loadJobs)

    async function retry(id: number) {
        await retryRagIngestionJob(id)
        message.success('任务已重试')
        await load()
    }

    async function cancel(id: number) {
        await cancelRagIngestionJob(id)
        message.success('任务已取消')
        await load()
    }

    const columns: ColumnsType<RagIngestionJobResponse> = [
        {title: '任务 ID', dataIndex: 'id', width: 90},
        {title: '文档 ID', dataIndex: 'documentId', width: 100},
        {title: '知识库', dataIndex: 'knowledgeBaseId', width: 100},
        {title: '步骤', dataIndex: 'currentStep', width: 110, render: (_, record) => <Tag>{record.currentStep}</Tag>},
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (_, record) => <Tag
                color={record.status === 'SUCCESS' ? 'success' : record.status === 'FAILED' ? 'error' : 'processing'}>{record.status}</Tag>,
        },
        {
            title: '进度',
            dataIndex: 'progress',
            width: 180,
            render: (_, record) => <Progress percent={record.progress} size="small"/>
        },
        {title: '切片', dataIndex: 'chunkCount', width: 90},
        {title: '错误', dataIndex: 'errorMessage', render: (_, record) => record.errorMessage || '-'},
        {
            title: '操作',
            width: 170,
            render: (_, record) => (
                <Space>
                    {canRetry &&
                        <Button icon={<ReloadOutlined/>} size="small"
                                onClick={() => void retry(record.id)}>重试</Button>}
                    {canCancel && (
                        <Popconfirm title="确认取消该任务？" onConfirm={() => void cancel(record.id)}>
                            <Button icon={<StopOutlined/>} size="small">取消</Button>
                        </Popconfirm>
                    )}
                </Space>
            ),
        },
    ]

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>}
                description="查看文档解析、切片、向量化和入库任务状态。"
                title="入库任务"
            />
            <Table<RagIngestionJobResponse>
                columns={columns}
                dataSource={records}
                loading={loading}
                pagination={{current: page, pageSize: size, total, showSizeChanger: true, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: 1100}}
            />
        </>
    )
}

export const Component = IngestionJobListPage
