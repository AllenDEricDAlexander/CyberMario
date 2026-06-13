import {Button, Card, Popconfirm, Table, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useEffect, useState} from 'react'
import {useNavigate, useParams} from 'react-router'
import {PageToolbar} from '../../components/PageToolbar'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {ragButtonCodes} from './ragPermissionCodes'
import {getRagChunks, updateRagChunkEnabled} from './ragService'
import type {RagChunkResponse} from './ragTypes'

function DocumentDetailPage() {
    const auth = useAuth()
    const navigate = useNavigate()
    const params = useParams()
    const documentId = Number(params.documentId)
    const [loading, setLoading] = useState(false)
    const [records, setRecords] = useState<RagChunkResponse[]>([])
    const [page, setPage] = useState(1)
    const [size, setSize] = useState(20)
    const [total, setTotal] = useState(0)
    const canToggle = canUseRbacButton(auth, ragButtonCodes.chunk.toggle)

    async function load(nextPage = page, nextSize = size) {
        setLoading(true)
        try {
            const result = await getRagChunks(documentId, {page: nextPage, size: nextSize})
            setRecords(result.records)
            setPage(result.page)
            setSize(result.size)
            setTotal(result.total)
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        if (documentId) {
            void load(1, size)
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [documentId])

    async function toggle(record: RagChunkResponse) {
        await updateRagChunkEnabled(record.id, !record.enabled)
        await load()
    }

    const columns: ColumnsType<RagChunkResponse> = [
        {title: '序号', dataIndex: 'chunkIndex', width: 80},
        {title: 'Token', dataIndex: 'tokenCount', width: 90},
        {
            title: '状态',
            dataIndex: 'enabled',
            width: 90,
            render: (value) => <Tag color={value ? 'success' : 'default'}>{value ? '启用' : '禁用'}</Tag>
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
            width: 100,
            render: (_, record) => canToggle ? (
                <Popconfirm title={`确认${record.enabled ? '禁用' : '启用'}该切片？`} onConfirm={() => toggle(record)}>
                    <Button size="small">{record.enabled ? '禁用' : '启用'}</Button>
                </Popconfirm>
            ) : '-',
        },
    ]

    return (
        <>
            <PageToolbar
                actions={<Button onClick={() => navigate('/rag/documents')}>返回</Button>}
                description="查看文档切片、元数据和检索启用状态。"
                title={`文档切片 #${documentId || '-'}`}
            />
            <Card>
                <Table<RagChunkResponse>
                    columns={columns}
                    dataSource={records}
                    loading={loading}
                    pagination={{current: page, pageSize: size, total, showSizeChanger: true, onChange: load}}
                    rowKey="id"
                />
            </Card>
        </>
    )
}

export const Component = DocumentDetailPage
