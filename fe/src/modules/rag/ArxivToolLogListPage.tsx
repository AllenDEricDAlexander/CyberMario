import {FileSearchOutlined, ReloadOutlined} from '@ant-design/icons'
import {Button, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback} from 'react'
import {DataTable} from '../../components/DataTable'
import {DateTimeText} from '../../components/DateTimeText'
import {PageToolbar} from '../../components/PageToolbar'
import {StackedCell} from '../../components/StackedCell'
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
        {
            title: '查询',
            dataIndex: 'query',
            fixed: 'left',
            width: 260,
            render: (_, record) => (
                <StackedCell
                    primary={<Typography.Text ellipsis={{tooltip: record.query}}>{record.query}</Typography.Text>}
                    secondary={`#${record.id} · ${record.requestUsername ?? '未知用户'}`}
                />
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 140,
            render: (value: ArxivToolLogStatus) => <Tag color={statusColor(value)}>{value}</Tag>,
        },
        {
            title: '结果',
            dataIndex: 'resultCount',
            width: 130,
            render: (_, record) => (
                <StackedCell
                    plain
                    primary={`${record.resultCount} 条`}
                    secondary={record.includeFullText ? '含全文' : '仅摘要'}
                />
            ),
        },
        {
            title: '论文',
            dataIndex: 'title',
            width: 280,
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
        {
            title: '入库',
            dataIndex: 'documentId',
            width: 150,
            render: (_, record) => (
                <StackedCell
                    plain
                    primary={record.documentId ? `文档 ${record.documentId}` : '未入库'}
                    secondary={record.ragIngestionJobId ? `任务 ${record.ragIngestionJobId}` : undefined}
                />
            ),
        },
        {title: '错误', dataIndex: 'errorMessage', render: valueOrDash},
        {
            title: '时间',
            dataIndex: 'createdAt',
            width: 210,
            render: (_, record) => (
                <StackedCell
                    plain
                    primary={<DateTimeText value={record.createdAt}/>}
                    secondary={<>完成 <DateTimeText value={record.finishedAt}/></>}
                />
            ),
        },
    ]

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={() => void load()}>刷新</Button>}
                description="查看 arXiv 检索、PDF 下载和导入 super-admin-arxiv 知识库的后台任务记录。"
                icon={<FileSearchOutlined/>}
                title="arXiv 日志"
            />
            <DataTable<ArxivToolLogResponse>
                columns={columns}
                count={total}
                dataSource={records}
                emptyDescription="Agent 调用 arXiv 工具检索或导入论文后，这里会留下记录。"
                emptyTitle="还没有 arXiv 记录"
                loading={loading}
                pagination={{current: page, pageSize: size, total, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: 1500}}
                title="arXiv 调用记录"
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
