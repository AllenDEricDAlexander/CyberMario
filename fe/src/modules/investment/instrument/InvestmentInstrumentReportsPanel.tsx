import {Alert, Button, Card, Empty, Space, Table, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useRef, useState} from 'react'
import {InvestmentAsyncState} from '../components/InvestmentAsyncState'
import {InvestmentReportDrawer} from '../research/InvestmentReportDrawer'
import {investmentReportTypeLabel} from '../research/InvestmentReportFilters'
import {listInvestmentReports} from '../services/investmentResearchService'
import type {InvestmentLoadState} from '../types/investmentCommonTypes'
import type {InvestmentReportSummaryResponse} from '../types/investmentResearchTypes'

type InvestmentInstrumentReportsPanelProps = {
    workspaceId?: number
    instrumentId: number
}

export function InvestmentInstrumentReportsPanel({
    workspaceId,
    instrumentId,
}: InvestmentInstrumentReportsPanelProps) {
    const [reports, setReports] = useState<InvestmentReportSummaryResponse[]>([])
    const [loadState, setLoadState] = useState<InvestmentLoadState>('idle')
    const [loadError, setLoadError] = useState<string>()
    const [selectedReportId, setSelectedReportId] = useState<number>()
    const generationRef = useRef(0)

    const load = useCallback(async () => {
        const generation = ++generationRef.current
        setReports([])
        setSelectedReportId(undefined)
        setLoadError(undefined)
        if (workspaceId === undefined) {
            setLoadState('idle')
            return
        }
        setLoadState('loading')
        try {
            const response = await listInvestmentReports(workspaceId, {page: 1, size: 100})
            if (generation !== generationRef.current) return
            setReports(response.records.filter((report) => report.instrumentId === instrumentId))
            setLoadState('ready')
        } catch (reason) {
            if (generation !== generationRef.current) return
            setLoadState('error')
            setLoadError(reason instanceof Error ? reason.message : '合约报告加载失败')
        }
    }, [instrumentId, workspaceId])

    useEffect(() => {
        void load()
        return () => {
            generationRef.current += 1
        }
    }, [load])

    if (workspaceId === undefined) {
        return (
            <Card title="合约分析报告">
                <Alert showIcon title="选择私人投资工作区后可查看固定版本的传统与 Agent 报告" type="info"/>
            </Card>
        )
    }
    const traditional = reports.filter((report) => report.reportType !== 'AGENT_ANALYSIS')
    const agent = reports.filter((report) => report.reportType === 'AGENT_ANALYSIS')

    return (
        <Card title="合约分析报告">
            <InvestmentAsyncState error={loadError} onRetry={() => void load()} state={loadState}>
                <Space orientation="vertical" size={20} style={{width: '100%'}}>
                    <ReportGroup
                        emptyText="当前合约暂无传统分析报告"
                        onOpen={setSelectedReportId}
                        reports={traditional}
                        title="传统分析"
                    />
                    <ReportGroup
                        emptyText="当前合约暂无 Agent 分析报告"
                        onOpen={setSelectedReportId}
                        reports={agent}
                        title="Agent 分析"
                    />
                </Space>
            </InvestmentAsyncState>
            <InvestmentReportDrawer
                onClose={() => setSelectedReportId(undefined)}
                open={selectedReportId !== undefined}
                reportId={selectedReportId}
            />
        </Card>
    )
}

function ReportGroup({
    emptyText,
    onOpen,
    reports,
    title,
}: {
    emptyText: string
    onOpen: (reportId: number) => void
    reports: InvestmentReportSummaryResponse[]
    title: string
}) {
    return (
        <section aria-label={title}>
            <Typography.Title level={5}>{title}</Typography.Title>
            {reports.length === 0 ? <Empty description={emptyText} image={Empty.PRESENTED_IMAGE_SIMPLE}/> : (
                <Table
                    columns={reportColumns(onOpen)}
                    dataSource={reports}
                    pagination={false}
                    rowKey="reportId"
                    scroll={{x: 900}}
                    size="small"
                />
            )}
        </section>
    )
}

function reportColumns(onOpen: (reportId: number) => void): ColumnsType<InvestmentReportSummaryResponse> {
    return [
        {title: '报告', dataIndex: 'title'},
        {title: '类型', dataIndex: 'reportType', render: investmentReportTypeLabel},
        {title: '状态', dataIndex: 'status', render: (value: string) => <Tag color={statusColor(value)}>{value}</Tag>},
        {title: '固定版本', dataIndex: 'reportVersion', render: (value: number) => `v${value}`},
        {title: '数据截止', dataIndex: 'dataAsOf'},
        {
            title: '操作',
            key: 'action',
            render: (_, report) => (
                <Button onClick={() => onOpen(report.reportId)} size="small">查看报告与证据</Button>
            ),
        },
    ]
}

function statusColor(status: string) {
    if (status === 'READY') return 'success'
    if (status === 'FAILED') return 'error'
    return 'processing'
}
