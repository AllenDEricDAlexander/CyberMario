import {BlockOutlined} from '@ant-design/icons'
import {Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback} from 'react'
import {useParams} from 'react-router'
import {DataTable} from '../../components/DataTable'
import {PageToolbar} from '../../components/PageToolbar'
import {RowActions} from '../../components/RowActions'
import {StackedCell} from '../../components/StackedCell'
import {usePageData} from '../../hooks/usePageData'
import {voidify} from '../../utils/async'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {ragButtonCodes} from './ragPermissionCodes'
import {getRagChunks, updateRagChunkEnabled} from './ragService'
import type {RagChunkResponse} from './ragTypes'

function DocumentDetailPage() {
    const auth = useAuth()
    const params = useParams()
    const documentId = Number(params.documentId)
    const canToggle = canUseRbacButton(auth, ragButtonCodes.chunk.toggle)
    const loadChunks = useCallback(
        (request: { page: number; size: number }) => getRagChunks(documentId, request),
        [documentId],
    )
    const {loading, records, page, size, total, load} = usePageData<RagChunkResponse>(loadChunks, {
        enabled: Boolean(documentId),
    })

    async function toggle(record: RagChunkResponse) {
        await updateRagChunkEnabled(record.id, !record.enabled)
        await load()
    }

    const columns: ColumnsType<RagChunkResponse> = [
        {
            title: '切片',
            dataIndex: 'chunkIndex',
            width: 130,
            render: (_, record) => (
                <StackedCell primary={`#${record.chunkIndex}`} secondary={`${record.tokenCount ?? 0} token`}/>
            ),
        },
        {
            title: '标题路径',
            dataIndex: 'headingPath',
            width: 220,
            render: (_, record) => (
                <StackedCell
                    plain
                    primary={record.headingPath || '-'}
                    secondary={record.contentHash ? record.contentHash.slice(0, 8) : undefined}
                />
            ),
        },
        {
            title: '状态',
            dataIndex: 'enabled',
            width: 90,
            render: (value: RagChunkResponse['enabled']) => (
                <Tag color={value ? 'success' : 'default'}>{value ? '启用' : '禁用'}</Tag>
            ),
        },
        {
            title: '内容',
            dataIndex: 'contentPreview',
            render: (_, record) => (
                <Typography.Paragraph copyable ellipsis={{rows: 3, expandable: true}}>
                    {record.content}
                </Typography.Paragraph>
            ),
        },
        {
            title: '操作',
            fixed: 'right',
            width: 100,
            render: (_, record) => (
                <RowActions
                    actions={[
                        {
                            key: 'toggle',
                            label: record.enabled ? '禁用' : '启用',
                            hidden: !canToggle,
                            confirm: `确认${record.enabled ? '禁用' : '启用'}该切片？`,
                            onClick: () => void toggle(record),
                        },
                    ]}
                />
            ),
        },
    ]

    return (
        <>
            <PageToolbar
                back="/rag/documents"
                breadcrumb={[{title: '文档管理', to: '/rag/documents'}, {title: `#${documentId || '-'}`}]}
                description="查看文档切片、元数据和检索启用状态。禁用的切片不会参与召回。"
                icon={<BlockOutlined/>}
                title={`文档切片 #${documentId || '-'}`}
            />
            <DataTable<RagChunkResponse>
                columns={columns}
                count={total}
                dataSource={records}
                emptyDescription="文档还没有解析出切片，可以回到文档列表触发一次重建索引。"
                emptyTitle="暂无切片"
                loading={loading}
                pagination={{current: page, pageSize: size, total, onChange: voidify(load)}}
                rowKey="id"
                title="切片列表"
            />
        </>
    )
}

export const Component = DocumentDetailPage
