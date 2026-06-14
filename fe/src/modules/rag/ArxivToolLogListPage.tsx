import {ReloadOutlined} from '@ant-design/icons'
import {Button, Table, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {usePageData} from '../../hooks/usePageData'
import {voidify} from '../../utils/async'
import {getArxivToolLogs} from './ragService'
import type {ArxivToolLogResponse, ArxivToolLogStatus} from './ragTypes'

function ArxivToolLogListPage() {
    const loadLogs = useCallback(
        (request: { page: number; size: number }) => getArxivToolLogs(request),
        [],
    )
    const {loading, records, page, size, total, load} = usePageData<ArxivToolLogResponse>(loadLogs)

    const columns: ColumnsType<ArxivToolLogResponse> = [
        {title: '日志 ID', dataIndex: 'id', width: 90},
        {title: '请求用户', dataIndex: 'requestUsername', width: 130, render: valueOrDash},
        {
            title: '查询',
            dataIndex: 'query',
            width: 240,
            render: (_, record) => (
                <Typography.Text ellipsis={{tooltip: record.query}}>{record.query}</Typography.Text>
            ),
        },
        {title: '结果数', dataIndex: 'resultCount', width: 90},
        {
            title: '全文',
            dataIndex: 'includeFullText',
            width: 80,
            render: (_, record) => (
                <Tag color={record.includeFullText ? 'processing' : 'default'}>
                    {record.includeFullText ? '是' : '否'}
                </Tag>
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 140,
            render: (value: ArxivToolLogStatus) => <Tag color={statusColor(value)}>{value}</Tag>,
        },
        {
            title: '论文',
            dataIndex: 'title',
            width: 260,
            render: (_, record) => {
                if (!record.title) {
                    return '-'
                }
                if (!record.pdfUrl) {
                    return <Typography.Text ellipsis={{tooltip: record.title}}>{record.title}</Typography.Text>
                }
                return (
                    <Typography.Link ellipsis href={record.pdfUrl} rel="noreferrer" target="_blank"
                                     title={record.title}>
                        {record.title}
                    </Typography.Link>
                )
            },
        },
        {title: '文档 ID', dataIndex: 'documentId', width: 100, render: valueOrDash},
        {title: '入库任务', dataIndex: 'ragIngestionJobId', width: 100, render: valueOrDash},
        {title: '错误', dataIndex: 'errorMessage', render: valueOrDash},
        {title: '创建时间', dataIndex: 'createdAt', width: 190, render: valueOrDash},
        {title: '完成时间', dataIndex: 'finishedAt', width: 190, render: valueOrDash},
    ]

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>}
                description="查看 arXiv 检索、PDF 下载和导入 super-admin-arxiv 知识库的后台任务记录。"
                title="arXiv 日志"
            />
            <Table<ArxivToolLogResponse>
                columns={columns}
                dataSource={records}
                loading={loading}
                pagination={{current: page, pageSize: size, total, showSizeChanger: true, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: 1600}}
            />
        </>
    )
}

function valueOrDash(value?: string | number | null) {
    return value ?? '-'
}

function statusColor(status: ArxivToolLogStatus) {
    if (status === 'SEARCHED' || status === 'IMPORT_SUCCESS' || status === 'IMPORT_SKIPPED') {
        return 'success'
    }
    if (status === 'IMPORT_FAILED') {
        return 'error'
    }
    return 'processing'
}

export const Component = ArxivToolLogListPage
