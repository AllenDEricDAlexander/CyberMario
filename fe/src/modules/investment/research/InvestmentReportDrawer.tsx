import {Alert, Descriptions, Drawer, Space, Table, Tag, Typography} from 'antd'
import {useCallback, useEffect, useRef, useState} from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {InvestmentAsyncState} from '../components/InvestmentAsyncState'
import {getInvestmentReport} from '../services/investmentResearchService'
import type {InvestmentLoadState} from '../types/investmentCommonTypes'
import type {
    InvestmentReportDetailResponse,
    InvestmentReportEvidenceResponse,
} from '../types/investmentResearchTypes'
import {investmentReportTypeLabel} from './InvestmentReportFilters'

const markdownPlugins = [remarkGfm]

type InvestmentReportDrawerProps = {
    open: boolean
    reportId?: number
    onClose: () => void
}

export function InvestmentReportDrawer({open, reportId, onClose}: InvestmentReportDrawerProps) {
    const [detail, setDetail] = useState<InvestmentReportDetailResponse>()
    const [loadState, setLoadState] = useState<InvestmentLoadState>('idle')
    const [loadError, setLoadError] = useState<string>()
    const generationRef = useRef(0)

    const load = useCallback(async () => {
        const generation = ++generationRef.current
        setDetail(undefined)
        if (!open || !reportId) {
            setLoadState('idle')
            setLoadError(undefined)
            return
        }
        setLoadState('loading')
        setLoadError(undefined)
        try {
            const response = await getInvestmentReport(reportId)
            if (generation === generationRef.current) {
                setDetail(response)
                setLoadState('ready')
            }
        } catch (reason) {
            if (generation === generationRef.current) {
                setLoadState('error')
                setLoadError(errorMessage(reason, '报告详情加载失败'))
            }
        }
    }, [open, reportId])

    useEffect(() => {
        void load()
        return () => {
            generationRef.current += 1
        }
    }, [load])

    return (
        <Drawer
            destroyOnHidden
            onClose={onClose}
            open={open}
            title={detail?.report.title ?? '分析报告详情'}
            width={880}
        >
            <InvestmentAsyncState error={loadError} onRetry={() => void load()} state={loadState}>
                {detail && <ReportDetail detail={detail}/>}
            </InvestmentAsyncState>
        </Drawer>
    )
}

function ReportDetail({detail}: {detail: InvestmentReportDetailResponse}) {
    const {report} = detail
    return (
        <Space orientation="vertical" size={16} style={{width: '100%'}}>
            <Descriptions bordered column={{xs: 1, sm: 2}} size="small">
                <Descriptions.Item label="报告类型">{investmentReportTypeLabel(report.reportType)}</Descriptions.Item>
                <Descriptions.Item label="状态"><Tag color={statusColor(report.status)}>{report.status}</Tag></Descriptions.Item>
                <Descriptions.Item label="版本">v{report.reportVersion}</Descriptions.Item>
                <Descriptions.Item label="来源">{detail.sourceType}</Descriptions.Item>
                <Descriptions.Item label="数据截止">{report.dataAsOf}</Descriptions.Item>
                <Descriptions.Item label="创建时间">{report.createdAt}</Descriptions.Item>
            </Descriptions>
            {report.summary && <Typography.Paragraph>{report.summary}</Typography.Paragraph>}
            {report.status === 'PENDING' && <Alert showIcon title="报告已进入生成队列" type="info"/>}
            {report.status === 'FAILED' && (
                <Alert description={report.summary} showIcon title="报告生成失败" type="error"/>
            )}
            {report.status === 'READY' && detail.contentMarkdown && (
                <article aria-label="报告正文" className="message-content">
                    <ReactMarkdown remarkPlugins={markdownPlugins}>{detail.contentMarkdown}</ReactMarkdown>
                </article>
            )}
            {report.status === 'READY' && !detail.contentMarkdown && (
                <Alert showIcon title="报告正文暂不可用" type="warning"/>
            )}
            <div>
                <Typography.Title level={5}>数据证据</Typography.Title>
                <Typography.Paragraph type="secondary">
                    以下证据范围和数据截止时间属于当前不可变报告版本。
                </Typography.Paragraph>
                <Table
                    columns={evidenceColumns}
                    dataSource={detail.evidence}
                    locale={{emptyText: '当前报告尚无证据记录'}}
                    pagination={false}
                    rowKey="evidenceId"
                    scroll={{x: 1100}}
                    size="small"
                />
            </div>
            <details>
                <summary>指标与结构化结果</summary>
                <Typography.Text copyable={{text: detail.metricsJson}}>复制结构化结果</Typography.Text>
                <pre>{prettyJson(detail.metricsJson)}</pre>
            </details>
        </Space>
    )
}

const evidenceColumns = [
    {title: '证据类型', dataIndex: 'evidenceType'},
    {title: '标的 ID', dataIndex: 'instrumentId', render: (value: number | null) => value ?? '-'},
    {title: '数据起点', dataIndex: 'dataStartTime'},
    {title: '数据终点', dataIndex: 'dataEndTime'},
    {title: '数据截止', dataIndex: 'dataAsOf'},
    {title: '来源引用', dataIndex: 'sourceReference'},
    {title: '载荷哈希', dataIndex: 'payloadHash'},
] satisfies import('antd/es/table').ColumnsType<InvestmentReportEvidenceResponse>

function statusColor(status: string) {
    if (status === 'READY') {
        return 'success'
    }
    if (status === 'FAILED') {
        return 'error'
    }
    return 'processing'
}

function prettyJson(value: string) {
    try {
        return JSON.stringify(JSON.parse(value) as unknown, null, 2)
    } catch {
        return value
    }
}

function errorMessage(reason: unknown, fallback: string) {
    return reason instanceof Error ? reason.message : fallback
}
