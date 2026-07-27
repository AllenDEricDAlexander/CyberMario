import {HistoryOutlined, ReloadOutlined} from '@ant-design/icons'
import {Button, Space, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback} from 'react'
import {DataTable} from '../../../components/DataTable'
import {DateTimeText} from '../../../components/DateTimeText'
import {PageToolbar} from '../../../components/PageToolbar'
import {StackedCell} from '../../../components/StackedCell'
import {usePageData} from '../../../hooks/usePageData'
import {voidify} from '../../../utils/async'
import {getMcpToolCallLogs} from './mcpService'
import type {McpToolCallLogResponse, McpToolCallStatus} from './mcpTypes'

function McpToolCallLogListPage() {
    const loadLogs = useCallback(
        (request: { page: number; size: number }) => getMcpToolCallLogs(request),
        [],
    )
    const {loading, records, page, size, total, load} = usePageData<McpToolCallLogResponse>(loadLogs)

    const columns: ColumnsType<McpToolCallLogResponse> = [
        {title: '创建时间', dataIndex: 'createdAt', width: 190, render: renderDateTime},
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (value: McpToolCallStatus) => <Tag color={statusColor(value)}>{value}</Tag>,
        },
        {
            title: '工具',
            dataIndex: 'toolKey',
            width: 300,
            render: (_, record) => <StackedCell primary={record.toolKey} secondary={record.serverCode}/>,
        },
        {
            title: '调用方',
            dataIndex: 'threadId',
            width: 240,
            render: (_, record) => (
                <StackedCell
                    plain
                    primary={record.threadId || '-'}
                    secondary={`用户 ID ${record.userId ?? '-'}`}
                />
            ),
        },
        {title: '耗时', dataIndex: 'costMs', width: 100, render: (value: number) => `${value}ms`},
        {
            title: '错误',
            dataIndex: 'errorMsg',
            render: (value?: string) => value
                ? <Typography.Text ellipsis={{tooltip: value}} type="danger">{value}</Typography.Text>
                : '-',
        },
    ]

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={() => void load()}>刷新</Button>}
                description="查看 ReactAgent 调用 MCP 工具的状态、耗时和请求响应摘要。"
                icon={<HistoryOutlined/>}
                title="MCP 调用日志"
            />
            <DataTable<McpToolCallLogResponse>
                columns={columns}
                count={total}
                dataSource={records}
                emptyDescription="ReactAgent 调用任意 MCP 工具后，这里会记录状态、耗时和载荷摘要。"
                emptyTitle="还没有调用记录"
                expandable={{
                    expandedRowRender: (record) => (
                        <Space className="u-full-width" direction="vertical" size={12}>
                            <Typography.Text strong>请求摘要</Typography.Text>
                            <Typography.Paragraph className="payload-text" copyable>
                                {record.requestArgsSummary || '-'}
                            </Typography.Paragraph>
                            <Typography.Text strong>响应摘要</Typography.Text>
                            <Typography.Paragraph className="payload-text" copyable>
                                {record.responseSummary || '-'}
                            </Typography.Paragraph>
                        </Space>
                    ),
                    rowExpandable: (record) => Boolean(record.requestArgsSummary || record.responseSummary),
                }}
                loading={loading}
                pagination={{current: page, pageSize: size, total, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: 1200}}
                title="调用记录"
            />
        </>
    )
}

function renderDateTime(value?: string | number | null) {
    return <DateTimeText value={value}/>
}

function statusColor(status: McpToolCallStatus) {
    if (status === 'SUCCESS') {
        return 'success'
    }
    if (status === 'BLOCKED') {
        return 'warning'
    }
    return 'error'
}

export const Component = McpToolCallLogListPage
