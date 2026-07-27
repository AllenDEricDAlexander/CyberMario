import {FileSearchOutlined, PlusOutlined} from '@ant-design/icons'
import {Alert, App, Button, DatePicker, InputNumber, Select, Tag} from 'antd'
import type {RangePickerProps} from 'antd/es/date-picker'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useRef, useState} from 'react'
import {DataTable} from '../../../components/DataTable'
import {EmptyState} from '../../../components/EmptyState'
import {FormDrawer} from '../../../components/FormDrawer'
import {PageGrid, PageStack} from '../../../components/PageSection'
import {PageToolbar} from '../../../components/PageToolbar'
import {StackedCell} from '../../../components/StackedCell'
import {ApiRequestError} from '../../../types/api'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {InvestmentTableFailure, investmentTableLocale} from '../components/InvestmentAsyncState'
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
        {
            title: '报告',
            dataIndex: 'title',
            render: (_, record) => <StackedCell primary={record.title} secondary={record.summary ?? '暂无摘要'}/>,
        },
        {
            title: '类型',
            dataIndex: 'reportType',
            width: 160,
            render: (_, record) => (
                <StackedCell
                    plain
                    primary={investmentReportTypeLabel(record.reportType)}
                    secondary={`v${record.reportVersion}`}
                />
            ),
        },
        {title: '状态', dataIndex: 'status', width: 110, render: (value: string) => <Tag color={statusColor(value)}>{value}</Tag>},
        {title: '数据截止', dataIndex: 'dataAsOf', width: 230},
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 100,
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
        <PageStack>
            <PageToolbar
                actions={canCreate && (
                    <Button icon={<PlusOutlined/>} onClick={openCreate} type="primary">创建报告</Button>
                )}
                description="报告版本与数据截止时间固定，后续行情不会静默改写历史结果。"
                icon={<FileSearchOutlined/>}
                title="传统分析报告"
            />
            <InvestmentReportFilters
                onReportTypeChange={(value) => {
                    setPage(1)
                    setReportType(value)
                }}
                reportType={reportType}
            />
            <DataTable<InvestmentReportSummaryResponse>
                columns={columns}
                count={result?.total ?? 0}
                dataSource={result?.records ?? []}
                emptyDescription="报告由服务端代码生成器产出；换一个报告类型，或创建一个新的生成任务。"
                emptyTitle={emptyDescription(reportType)}
                loading={loadState === 'loading'}
                locale={investmentTableLocale(
                    loadState,
                    <InvestmentTableFailure error={loadError} onRetry={() => void loadReports()} state={loadState}/>,
                )}
                pagination={{
                    current: result?.page ?? page,
                    pageSize: result?.size ?? PAGE_SIZE,
                    total: result?.total ?? 0,
                    showSizeChanger: false,
                    onChange: setPage,
                }}
                rowKey="reportId"
                scroll={{x: 900}}
                title="报告列表"
            />
            <InvestmentReportDrawer
                onClose={() => setSelectedReportId(undefined)}
                open={selectedReportId !== undefined}
                reportId={selectedReportId}
            />
            <FormDrawer
                footerHint={installed ? undefined : '该报告类型的代码生成器尚未接入'}
                loading={creating}
                onClose={() => setCreateOpen(false)}
                onSubmit={() => void submitCreate()}
                open={createOpen}
                submitDisabled={!createValid}
                submitText="加入生成队列"
                title="创建不可变分析报告"
            >
                <PageStack>
                    <Select
                        aria-label="创建报告类型"
                        className="u-full-width"
                        onChange={(value) => {
                            setCreateReportType(value)
                            setCreateError(undefined)
                            setCreateCapabilityMissing(false)
                        }}
                        options={investmentReportTypeOptions}
                        value={createReportType}
                    />
                    {!installed && (
                        <EmptyState
                            description="请改选一个已接入的报告类型；生成器由服务端 Java 代码声明，前端无法开启。"
                            inline
                            title={`${investmentReportTypeLabel(createReportType)} 的代码生成器尚未接入`}
                        />
                    )}
                    {createReportType === 'INSTRUMENT_ANALYSIS' && installed && (
                        <PageStack>
                            <InputNumber
                                aria-label="合约 ID"
                                className="u-full-width"
                                min={1}
                                onChange={setInstrumentId}
                                placeholder="内部合约 ID"
                                precision={0}
                                value={instrumentId}
                            />
                            <PageGrid minWidth={160}>
                                <Select
                                    aria-label="分析价型"
                                    onChange={setPriceType}
                                    options={['MARKET', 'MARK', 'INDEX'].map((value) => ({label: value, value}))}
                                    value={priceType}
                                />
                                <Select
                                    aria-label="分析周期"
                                    onChange={setInterval}
                                    options={['M1', 'M5', 'M15', 'M30', 'H1', 'H4', 'D1'].map((value) => ({label: value, value}))}
                                    value={interval}
                                />
                            </PageGrid>
                            <RangePicker
                                aria-label="分析时间范围"
                                className="u-full-width"
                                onChange={setRange}
                                showTime
                                value={range}
                            />
                        </PageStack>
                    )}
                    {createError && (
                        <Alert
                            description={createError}
                            showIcon
                            title={createCapabilityMissing ? '报告能力尚未接入' : '报告创建失败'}
                            type={createCapabilityMissing ? 'info' : 'error'}
                        />
                    )}
                </PageStack>
            </FormDrawer>
        </PageStack>
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
