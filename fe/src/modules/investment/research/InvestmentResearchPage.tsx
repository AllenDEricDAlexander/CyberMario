import {Alert, App, Button, Card, DatePicker, Empty, Flex, InputNumber, Modal, Select, Space, Table, Tag, Typography} from 'antd'
import type {RangePickerProps} from 'antd/es/date-picker'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useRef, useState} from 'react'
import {ApiRequestError} from '../../../types/api'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {InvestmentAsyncState} from '../components/InvestmentAsyncState'
import {useInvestmentWorkspace} from '../hooks/useInvestmentWorkspace'
import {investmentButtonCodes} from '../investmentPermissionCodes'
import {
    createInvestmentReport,
    listInvestmentReports,
} from '../services/investmentResearchService'
import type {InvestmentLoadState} from '../types/investmentCommonTypes'
import type {InvestmentBarInterval, InvestmentPriceType} from '../types/investmentMarketTypes'
import type {
    CreateInvestmentReportRequest,
    InvestmentReportPage,
    InvestmentReportSummaryResponse,
    InvestmentReportType,
} from '../types/investmentResearchTypes'
import {
    InvestmentReportFilters,
    investmentReportTypeLabel,
    investmentReportTypeOptions,
} from './InvestmentReportFilters'
import {InvestmentReportDrawer} from './InvestmentReportDrawer'

const PAGE_SIZE = 20
const {RangePicker} = DatePicker
type RangeValue = Parameters<NonNullable<RangePickerProps['onChange']>>[0]

export default function InvestmentResearchPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const {currentWorkspace} = useInvestmentWorkspace()
    const canCreate = canUseRbacButton(auth, investmentButtonCodes.reportCreate)
    const [reportType, setReportType] = useState<InvestmentReportType>()
    const [page, setPage] = useState(1)
    const [result, setResult] = useState<InvestmentReportPage>()
    const [loadState, setLoadState] = useState<InvestmentLoadState>('loading')
    const [loadError, setLoadError] = useState<string>()
    const [selectedReportId, setSelectedReportId] = useState<number>()
    const [createOpen, setCreateOpen] = useState(false)
    const [createReportType, setCreateReportType] = useState<InvestmentReportType>('MARKET_OVERVIEW')
    const [instrumentId, setInstrumentId] = useState<number | null>(null)
    const [priceType, setPriceType] = useState<InvestmentPriceType>('MARKET')
    const [interval, setInterval] = useState<InvestmentBarInterval>('H1')
    const [range, setRange] = useState<RangeValue>(null)
    const [creating, setCreating] = useState(false)
    const [createError, setCreateError] = useState<string>()
    const [createCapabilityMissing, setCreateCapabilityMissing] = useState(false)
    const listGenerationRef = useRef(0)
    const createGenerationRef = useRef(0)

    const loadReports = useCallback(async (
        nextPage = page,
        nextReportType = reportType,
    ) => {
        const generation = ++listGenerationRef.current
        if (!currentWorkspace) {
            setResult(undefined)
            setLoadState('empty')
            setLoadError(undefined)
            return
        }
        setLoadState('loading')
        setLoadError(undefined)
        try {
            const response = await listInvestmentReports(currentWorkspace.id, {
                reportType: nextReportType,
                page: nextPage,
                size: PAGE_SIZE,
            })
            if (generation === listGenerationRef.current) {
                setResult(response)
                setLoadState(response.records.length === 0 ? 'empty' : 'ready')
            }
        } catch (reason) {
            if (generation === listGenerationRef.current) {
                setResult(undefined)
                setLoadState('error')
                setLoadError(errorMessage(reason, '分析报告加载失败'))
            }
        }
    }, [currentWorkspace, page, reportType])

    useEffect(() => {
        setResult(undefined)
        setSelectedReportId(undefined)
        void loadReports()
        return () => {
            listGenerationRef.current += 1
        }
    }, [loadReports])

    useEffect(() => () => {
        createGenerationRef.current += 1
    }, [currentWorkspace?.id])

    function openCreate() {
        setCreateReportType('MARKET_OVERVIEW')
        setInstrumentId(null)
        setPriceType('MARKET')
        setInterval('H1')
        setRange(null)
        setCreateError(undefined)
        setCreateCapabilityMissing(false)
        setCreateOpen(true)
    }

    async function submitCreate() {
        if (!currentWorkspace || !isCreateInputValid(createReportType, instrumentId, range)) {
            return
        }
        const generation = ++createGenerationRef.current
        const workspaceId = currentWorkspace.id
        setCreating(true)
        setCreateError(undefined)
        setCreateCapabilityMissing(false)
        try {
            const response = await createInvestmentReport(
                workspaceId,
                createRequest(createReportType, instrumentId, priceType, interval, range),
            )
            if (generation !== createGenerationRef.current || currentWorkspace.id !== workspaceId) {
                return
            }
            void message.success(`报告 v${response.report.reportVersion} 已进入任务队列（Job ${response.jobId}）`)
            setCreateOpen(false)
            setPage(1)
            await loadReports(1, reportType)
        } catch (reason) {
            if (generation !== createGenerationRef.current) {
                return
            }
            const capabilityMissing = reason instanceof ApiRequestError
                && reason.code === 'INVESTMENT_CAPABILITY_UNAVAILABLE'
            setCreateCapabilityMissing(capabilityMissing)
            setCreateError(errorMessage(reason, '报告创建失败'))
        } finally {
            if (generation === createGenerationRef.current) {
                setCreating(false)
            }
        }
    }

    const columns: ColumnsType<InvestmentReportSummaryResponse> = [
        {title: '报告', dataIndex: 'title'},
        {title: '类型', dataIndex: 'reportType', render: (value: InvestmentReportType) => investmentReportTypeLabel(value)},
        {title: '状态', dataIndex: 'status', render: (value: string) => <Tag color={statusColor(value)}>{value}</Tag>},
        {title: '版本', dataIndex: 'reportVersion', render: (value: number) => `v${value}`},
        {title: '数据截止', dataIndex: 'dataAsOf'},
        {title: '摘要', dataIndex: 'summary', render: (value: string | null) => value ?? '-'},
        {
            title: '操作',
            key: 'action',
            render: (_, record) => (
                <Button
                    aria-label={`查看报告 ${record.title}`}
                    onClick={() => setSelectedReportId(record.reportId)}
                    size="small"
                >
                    查看
                </Button>
            ),
        },
    ]
    const installed = isGeneratorInstalled(createReportType)
    const createValid = installed && isCreateInputValid(createReportType, instrumentId, range)

    return (
        <Space orientation="vertical" size={16} style={{width: '100%'}}>
            <Card>
                <Flex align="center" gap={12} justify="space-between" wrap>
                    <div>
                        <Typography.Title level={4}>传统分析报告</Typography.Title>
                        <Typography.Text type="secondary">报告版本与数据截止时间固定，后续行情不会静默改写历史结果。</Typography.Text>
                    </div>
                    {canCreate && <Button onClick={openCreate} type="primary">创建报告</Button>}
                </Flex>
                <InvestmentReportFilters
                    onReportTypeChange={(value) => {
                        setPage(1)
                        setReportType(value)
                    }}
                    reportType={reportType}
                />
            </Card>
            <Card>
                <InvestmentAsyncState
                    emptyDescription={emptyDescription(reportType)}
                    error={loadError}
                    onRetry={() => void loadReports()}
                    state={loadState}
                >
                    <Table
                        columns={columns}
                        dataSource={result?.records ?? []}
                        pagination={{
                            current: result?.page ?? page,
                            pageSize: result?.size ?? PAGE_SIZE,
                            total: result?.total ?? 0,
                            showSizeChanger: false,
                            onChange: setPage,
                        }}
                        rowKey="reportId"
                        scroll={{x: 1100}}
                    />
                </InvestmentAsyncState>
            </Card>
            <InvestmentReportDrawer
                onClose={() => setSelectedReportId(undefined)}
                open={selectedReportId !== undefined}
                reportId={selectedReportId}
            />
            <Modal
                destroyOnHidden
                okButtonProps={{disabled: !createValid}}
                okText="加入生成队列"
                confirmLoading={creating}
                onCancel={() => setCreateOpen(false)}
                onOk={() => void submitCreate()}
                open={createOpen}
                title="创建不可变分析报告"
            >
                <Space orientation="vertical" size={16} style={{width: '100%'}}>
                    <Select
                        aria-label="创建报告类型"
                        onChange={(value) => {
                            setCreateReportType(value)
                            setCreateError(undefined)
                            setCreateCapabilityMissing(false)
                        }}
                        options={investmentReportTypeOptions}
                        style={{width: '100%'}}
                        value={createReportType}
                    />
                    {!installed && (
                        <Empty
                            description={`${investmentReportTypeLabel(createReportType)} 的代码生成器尚未接入`}
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                        />
                    )}
                    {createReportType === 'INSTRUMENT_ANALYSIS' && installed && (
                        <Space orientation="vertical" size={12} style={{width: '100%'}}>
                            <InputNumber
                                aria-label="合约 ID"
                                min={1}
                                onChange={setInstrumentId}
                                placeholder="内部合约 ID"
                                precision={0}
                                style={{width: '100%'}}
                                value={instrumentId}
                            />
                            <Flex gap={12}>
                                <Select
                                    aria-label="分析价型"
                                    onChange={setPriceType}
                                    options={['MARKET', 'MARK', 'INDEX'].map((value) => ({label: value, value}))}
                                    style={{flex: 1}}
                                    value={priceType}
                                />
                                <Select
                                    aria-label="分析周期"
                                    onChange={setInterval}
                                    options={['M1', 'M5', 'M15', 'M30', 'H1', 'H4', 'D1'].map((value) => ({label: value, value}))}
                                    style={{flex: 1}}
                                    value={interval}
                                />
                            </Flex>
                            <RangePicker
                                aria-label="分析时间范围"
                                onChange={setRange}
                                showTime
                                style={{width: '100%'}}
                                value={range}
                            />
                        </Space>
                    )}
                    {createError && (
                        <Alert
                            description={createError}
                            showIcon
                            title={createCapabilityMissing ? '报告能力尚未接入' : '报告创建失败'}
                            type={createCapabilityMissing ? 'info' : 'error'}
                        />
                    )}
                </Space>
            </Modal>
        </Space>
    )
}

function isGeneratorInstalled(reportType: InvestmentReportType) {
    return reportType === 'MARKET_OVERVIEW' || reportType === 'INSTRUMENT_ANALYSIS'
}

function isCreateInputValid(
    reportType: InvestmentReportType,
    instrumentId: number | null,
    range: RangeValue,
) {
    if (!isGeneratorInstalled(reportType)) {
        return false
    }
    if (reportType === 'MARKET_OVERVIEW') {
        return true
    }
    const from = range?.[0]
    const to = range?.[1]
    return Boolean(instrumentId && instrumentId > 0 && from && to
        && to.isAfter(from) && to.valueOf() <= Date.now())
}

function createRequest(
    reportType: InvestmentReportType,
    instrumentId: number | null,
    priceType: InvestmentPriceType,
    interval: InvestmentBarInterval,
    range: RangeValue,
): CreateInvestmentReportRequest {
    if (reportType === 'MARKET_OVERVIEW') {
        return {reportType}
    }
    return {
        reportType,
        instrumentId: instrumentId as number,
        priceType,
        interval,
        fromInclusive: range?.[0]?.toISOString(),
        toExclusive: range?.[1]?.toISOString(),
    }
}

function emptyDescription(reportType?: InvestmentReportType) {
    if (reportType && !isGeneratorInstalled(reportType)) {
        return `${investmentReportTypeLabel(reportType)} 暂无报告；对应代码生成器尚未接入`
    }
    return `${investmentReportTypeLabel(reportType)} 暂无报告`
}

function statusColor(status: string) {
    if (status === 'READY') {
        return 'success'
    }
    if (status === 'FAILED') {
        return 'error'
    }
    return 'processing'
}

function errorMessage(reason: unknown, fallback: string) {
    return reason instanceof Error ? reason.message : fallback
}

export const Component = InvestmentResearchPage
