import {CloudUploadOutlined, ReloadOutlined, StopOutlined} from '@ant-design/icons'
import {App, Button, Progress, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback} from 'react'
import {DataTable} from '../../components/DataTable'
import {PageToolbar} from '../../components/PageToolbar'
import {RowActions} from '../../components/RowActions'
import {StackedCell} from '../../components/StackedCell'
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
        {
            title: '任务',
            dataIndex: 'id',
            width: 170,
            render: (_, record) => (
                <StackedCell
                    primary={`#${record.id}`}
                    secondary={`文档 ${record.documentId} · 知识库 ${record.knowledgeBaseId}`}
                />
            ),
        },
        {title: '步骤', dataIndex: 'currentStep', width: 130, render: (_, record) => <Tag>{record.currentStep}</Tag>},
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (_, record) => <Tag color={jobStatusColor(record.status)}>{record.status}</Tag>,
        },
        {
            title: '进度',
            dataIndex: 'progress',
            width: 180,
            render: (_, record) => <Progress percent={record.progress} size="small"/>,
        },
        {title: '切片', dataIndex: 'chunkCount', width: 90},
        {title: '错误', dataIndex: 'errorMessage', render: (_, record) => record.errorMessage || '-'},
        {
            title: '操作',
            fixed: 'right',
            width: 160,
            render: (_, record) => (
                <RowActions
                    actions={[
                        {
                            key: 'retry',
                            label: '重试',
                            icon: <ReloadOutlined/>,
                            hidden: !canRetry,
                            onClick: () => void retry(record.id),
                        },
                        {
                            key: 'cancel',
                            label: '取消',
                            icon: <StopOutlined/>,
                            hidden: !canCancel,
                            confirm: '确认取消该任务？已完成的步骤不会回滚。',
                            onClick: () => void cancel(record.id),
                        },
                    ]}
                />
            ),
        },
    ]

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={() => void load()}>刷新</Button>}
                description="查看文档解析、切片、向量化和入库任务状态。"
                icon={<CloudUploadOutlined/>}
                title="入库任务"
            />
            <DataTable<RagIngestionJobResponse>
                columns={columns}
                count={total}
                dataSource={records}
                emptyDescription="上传或导入文档并选择立即解析后，这里会出现对应的入库任务。"
                emptyTitle="还没有入库任务"
                loading={loading}
                pagination={{current: page, pageSize: size, total, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: 1100}}
                title="任务列表"
            />
        </>
    )
}

function jobStatusColor(status: RagIngestionJobResponse['status']) {
    if (status === 'SUCCESS') {
        return 'success'
    }
    if (status === 'FAILED') {
        return 'error'
    }
    return 'processing'
}

export const Component = IngestionJobListPage
