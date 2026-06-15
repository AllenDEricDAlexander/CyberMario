import {ReloadOutlined} from '@ant-design/icons'
import {Button, Space, Table, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback} from 'react'
import {PageToolbar} from '../../../components/PageToolbar'
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
        {title: '创建时间', dataIndex: 'createdAt', width: 190},
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (value: McpToolCallStatus) => <Tag color={statusColor(value)}>{value}</Tag>,
        },
        {title: '服务', dataIndex: 'serverCode', width: 150},
        {
            title: 'Tool Key',
            dataIndex: 'toolKey',
            width: 260,
            render: (value: string) => <Typography.Text copyable ellipsis={{tooltip: value}}>{value}</Typography.Text>,
        },
        {title: '用户 ID', dataIndex: 'userId', width: 100, render: valueOrDash},
        {
            title: '线程',
            dataIndex: 'threadId',
            width: 220,
            render: (value?: string) => value
                ? <Typography.Text copyable ellipsis={{tooltip: value}}>{value}</Typography.Text>
                : '-',
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
                title="MCP 调用日志"
            />
            <Table<McpToolCallLogResponse>
                columns={columns}
                dataSource={records}
                expandable={{
                    expandedRowRender: (record) => (
                        <Space direction="vertical" size={12} style={{width: '100%'}}>
                            <Typography.Text strong>请求摘要</Typography.Text>
                            <Typography.Paragraph copyable style={{marginBottom: 0, whiteSpace: 'pre-wrap'}}>
                                {record.requestArgsSummary || '-'}
                            </Typography.Paragraph>
                            <Typography.Text strong>响应摘要</Typography.Text>
                            <Typography.Paragraph copyable style={{marginBottom: 0, whiteSpace: 'pre-wrap'}}>
                                {record.responseSummary || '-'}
                            </Typography.Paragraph>
                        </Space>
                    ),
                    rowExpandable: (record) => Boolean(record.requestArgsSummary || record.responseSummary),
                }}
                loading={loading}
                pagination={{current: page, pageSize: size, total, showSizeChanger: true, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: 1400}}
            />
        </>
    )
}

function valueOrDash(value?: string | number | null) {
    return value ?? '-'
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
