import {ReloadOutlined, StopOutlined} from '@ant-design/icons'
import {App, Button, Popconfirm, Progress, Space, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useEffect, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {ragButtonCodes} from './ragPermissionCodes'
import {cancelRagIngestionJob, getRagIngestionJobs, retryRagIngestionJob} from './ragService'
import type {RagIngestionJobResponse} from './ragTypes'

function IngestionJobListPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const [loading, setLoading] = useState(false)
    const [records, setRecords] = useState<RagIngestionJobResponse[]>([])
    const [page, setPage] = useState(1)
    const [size, setSize] = useState(20)
    const [total, setTotal] = useState(0)
    const canRetry = canUseRbacButton(auth, ragButtonCodes.job.retry)
    const canCancel = canUseRbacButton(auth, ragButtonCodes.job.cancel)

    async function load(nextPage = page, nextSize = size) {
        setLoading(true)
        try {
            const result = await getRagIngestionJobs({page: nextPage, size: nextSize})
            setRecords(result.records)
            setPage(result.page)
            setSize(result.size)
            setTotal(result.total)
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        void load(1, size)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

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
        {title: '步骤', dataIndex: 'currentStep', width: 110, render: (value) => <Tag>{value}</Tag>},
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (value) => <Tag
                color={value === 'SUCCESS' ? 'success' : value === 'FAILED' ? 'error' : 'processing'}>{value}</Tag>,
        },
        {title: '进度', dataIndex: 'progress', width: 180, render: (value) => <Progress percent={value} size="small"/>},
        {title: '切片', dataIndex: 'chunkCount', width: 90},
        {title: '错误', dataIndex: 'errorMessage', render: (value) => value || '-'},
        {
            title: '操作',
            width: 170,
            render: (_, record) => (
                <Space>
                    {canRetry &&
                        <Button icon={<ReloadOutlined/>} size="small" onClick={() => retry(record.id)}>重试</Button>}
                    {canCancel && (
                        <Popconfirm title="确认取消该任务？" onConfirm={() => cancel(record.id)}>
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
                actions={<Button icon={<ReloadOutlined/>} onClick={() => load()}>刷新</Button>}
                description="查看文档解析、切片、向量化和入库任务状态。"
                title="入库任务"
            />
            <Table<RagIngestionJobResponse>
                columns={columns}
                dataSource={records}
                loading={loading}
                pagination={{current: page, pageSize: size, total, showSizeChanger: true, onChange: load}}
                rowKey="id"
                scroll={{x: 1100}}
            />
        </>
    )
}

export const Component = IngestionJobListPage
