import {act, fireEvent, render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import InvestmentPlatformPage, {
    createMarketDataPullRequest,
    isStaleRunningJob,
} from './InvestmentPlatformPage'

const mocks = vi.hoisted(() => ({
    canPull: true,
    canRetry: true,
    canResolve: true,
    subscriptions: vi.fn(),
    jobs: vi.fn(),
    createPull: vi.fn(),
    retry: vi.fn(),
    issues: vi.fn(),
    resolve: vi.fn(),
    rangeStart: '2026-07-18T00:00:00.000Z',
    rangeEnd: '2026-07-19T00:00:00.000Z',
}))

vi.mock('antd', async (importOriginal) => {
    const actual = await importOriginal<typeof import('antd')>()
    return {
        ...actual,
        DatePicker: {
            ...actual.DatePicker,
            RangePicker: (props: {onChange?: (value: unknown, dateString: unknown) => void}) => (
                <button
                    aria-label="拉取时间范围"
                    onClick={() => props.onChange?.([
                        testDate(mocks.rangeStart),
                        testDate(mocks.rangeEnd),
                    ], ['', ''])}
                    type="button"
                >
                    选择测试时间
                </button>
            ),
        },
    }
})

vi.mock('../../auth/authStore', () => ({
    useAuth: () => ({roleCodes: [], hasAnyButton: vi.fn(), hasPermission: vi.fn()}),
    canUseRbacButton: (_auth: unknown, code: string) => {
        if (code.endsWith('pull-market-data')) return mocks.canPull
        if (code.endsWith('retry-job')) return mocks.canRetry
        return mocks.canResolve
    },
}))

vi.mock('../services/investmentPlatformService', () => ({
    listInvestmentPlatformSubscriptions: mocks.subscriptions,
    listInvestmentPlatformJobs: mocks.jobs,
    createInvestmentMarketDataPull: mocks.createPull,
    retryInvestmentPlatformJob: mocks.retry,
    listInvestmentDataQualityIssues: mocks.issues,
    resolveInvestmentDataQualityIssue: mocks.resolve,
}))

describe('InvestmentPlatformPage', () => {
    beforeEach(() => {
        mocks.canPull = true
        mocks.canRetry = true
        mocks.canResolve = true
        mocks.subscriptions.mockReset().mockResolvedValue([])
        mocks.jobs.mockReset().mockResolvedValue(page([failedJob]))
        mocks.createPull.mockReset().mockResolvedValue(pullResponse)
        mocks.retry.mockReset().mockResolvedValue(undefined)
        mocks.issues.mockReset().mockResolvedValue(page([openIssue]))
        mocks.resolve.mockReset().mockResolvedValue(undefined)
        mocks.rangeStart = '2026-07-18T00:00:00.000Z'
        mocks.rangeEnd = '2026-07-19T00:00:00.000Z'
    })

    test('renders an empty code registry as an explicit read-only state without subscription forms', async () => {
        renderPage()

        expect(await screen.findByText('尚未在代码中接入任何行情订阅')).toBeTruthy()
        expect(screen.getByText(/页面不提供新增、修改或删除订阅/)).toBeTruthy()
        expect(screen.queryByRole('button', {name: /新增|修改|删除/})).toBeNull()
        expect(mocks.subscriptions).toHaveBeenCalledTimes(1)
    })

    test('hides manual pull without permission', async () => {
        mocks.canPull = false
        mocks.subscriptions.mockResolvedValue([btcSubscription])

        renderPage()

        await waitFor(() => expect(mocks.subscriptions).toHaveBeenCalledTimes(1))
        expect(screen.queryByRole('button', {name: '手动拉取'})).toBeNull()
    })

    test('offers only BTC and SOL with server-subscribed candle intervals', async () => {
        mocks.subscriptions.mockResolvedValue([
            btcSubscription,
            solSubscription,
            {...btcSubscription, symbol: 'ETHUSDT'},
        ])
        renderPage()

        const openButton = await screen.findByRole('button', {name: '手动拉取'})
        await waitFor(() => expect(openButton.hasAttribute('disabled')).toBe(false))
        await userEvent.click(openButton)
        const selects = screen.getAllByRole('combobox')

        await userEvent.click(selects[0])
        expect(await screen.findByRole('option', {name: 'SOLUSDT'})).toBeTruthy()
        expect(screen.queryByRole('option', {name: 'ETHUSDT'})).toBeNull()
        await userEvent.click(screen.getByRole('option', {name: 'BTCUSDT'}))

        await userEvent.click(screen.getAllByRole('combobox')[2])
        expect(await screen.findByRole('option', {name: 'M1'})).toBeTruthy()
        expect(await screen.findByRole('option', {name: 'M5'})).toBeTruthy()
        expect(await screen.findByRole('option', {name: 'M15'})).toBeTruthy()
        expect(await screen.findByRole('option', {name: 'M30'})).toBeTruthy()
        expect(await screen.findByRole('option', {name: 'H1'})).toBeTruthy()
        expect(await screen.findByRole('option', {name: 'H4'})).toBeTruthy()
        expect(await screen.findByRole('option', {name: 'D1'})).toBeTruthy()
    })

    test('submits funding without an interval once, then switches to refreshed job history', async () => {
        const pending = deferred<typeof pullResponse>()
        mocks.subscriptions.mockResolvedValue([btcSubscription, solSubscription])
        mocks.createPull.mockReturnValue(pending.promise)
        renderPage()
        const openButton = await screen.findByRole('button', {name: '手动拉取'})
        await waitFor(() => expect(openButton.hasAttribute('disabled')).toBe(false))
        await userEvent.click(openButton)

        const capabilitySelect = screen.getAllByRole('combobox')[1]
        await userEvent.click(capabilitySelect)
        await userEvent.click(await screen.findByText('资金费率'))
        expect(screen.queryByText('K 线周期')).toBeNull()
        await userEvent.click(screen.getByRole('button', {name: '拉取时间范围'}))

        const submitButton = screen.getByRole('button', {name: '创建拉取任务'})
        await userEvent.click(submitButton)
        await waitFor(() => expect(mocks.createPull).toHaveBeenCalledTimes(1))
        expect(mocks.createPull).toHaveBeenCalledWith({
            symbol: 'BTCUSDT',
            capability: 'FUNDING_RATE',
            interval: null,
            startInclusive: '2026-07-18T00:00:00.000Z',
            endExclusive: '2026-07-19T00:00:00.000Z',
        })
        await userEvent.click(submitButton)
        expect(mocks.createPull).toHaveBeenCalledTimes(1)

        act(() => pending.resolve(pullResponse))
        await waitFor(() => expect(mocks.jobs).toHaveBeenCalledTimes(2))
        expect(screen.getByRole('tab', {name: '同步任务'}).getAttribute('aria-selected')).toBe('true')
        expect(screen.queryByRole('dialog', {name: '手动拉取 Bitget 行情数据'})).toBeNull()
    })

    test('suppresses a second same-tick submit while form validation is pending', async () => {
        const pending = deferred<typeof pullResponse>()
        mocks.subscriptions.mockResolvedValue([btcSubscription])
        mocks.createPull.mockReturnValue(pending.promise)
        renderPage()
        const openButton = await screen.findByRole('button', {name: '手动拉取'})
        await waitFor(() => expect(openButton.hasAttribute('disabled')).toBe(false))
        await userEvent.click(openButton)
        await userEvent.click(screen.getByRole('button', {name: '拉取时间范围'}))

        const submitButton = screen.getByRole('button', {name: '创建拉取任务'})
        fireEvent.click(submitButton)
        fireEvent.click(submitButton)
        expect(mocks.createPull).not.toHaveBeenCalled()

        await waitFor(() => expect(mocks.createPull).toHaveBeenCalledTimes(1))
        act(() => pending.resolve(pullResponse))
        await waitFor(() => expect(screen.queryByRole(
            'dialog',
            {name: '手动拉取 Bitget 行情数据'},
        )).toBeNull())
    })

    test('releases the submit guard after validation and local request failures', async () => {
        mocks.subscriptions.mockResolvedValue([btcSubscription])
        renderPage()
        const openButton = await screen.findByRole('button', {name: '手动拉取'})
        await waitFor(() => expect(openButton.hasAttribute('disabled')).toBe(false))
        await userEvent.click(openButton)

        const submitButton = screen.getByRole('button', {name: '创建拉取任务'})
        await userEvent.click(submitButton)
        expect(mocks.createPull).not.toHaveBeenCalled()

        mocks.rangeStart = '2023-03-01T00:00:00.000Z'
        mocks.rangeEnd = '2025-03-01T00:00:00.000Z'
        await userEvent.click(screen.getByRole('button', {name: '拉取时间范围'}))
        await userEvent.click(submitButton)
        expect(await screen.findByText(/730 天/)).toBeTruthy()
        expect(mocks.createPull).not.toHaveBeenCalled()

        mocks.rangeStart = '2026-07-18T00:00:00.000Z'
        mocks.rangeEnd = '2026-07-19T00:00:00.000Z'
        await userEvent.click(screen.getByRole('button', {name: '拉取时间范围'}))
        await userEvent.click(submitButton)
        await waitFor(() => expect(mocks.createPull).toHaveBeenCalledTimes(1))
    })

    test('shows structured manual job history without rendering raw job JSON', async () => {
        mocks.jobs.mockResolvedValue(page([{
            ...failedJob,
            inputJson: 'RAW_INPUT_SECRET',
            resultJson: 'RAW_RESULT_SECRET',
        }]))
        renderPage()
        await userEvent.click(await screen.findByRole('tab', {name: '同步任务'}))

        expect(await screen.findByText('MANUAL')).toBeTruthy()
        expect(screen.getByText('BITGET')).toBeTruthy()
        expect(screen.getByText('BTCUSDT')).toBeTruthy()
        expect(screen.getByText('MARKET_CANDLE')).toBeTruthy()
        expect(screen.getByText('2026-07-15T00:00:00Z')).toBeTruthy()
        expect(screen.getByText('2026-07-16T00:00:00Z')).toBeTruthy()
        expect(screen.getByText('120 / 118')).toBeTruthy()
        expect(screen.queryByText('RAW_INPUT_SECRET')).toBeNull()
        expect(screen.queryByText('RAW_RESULT_SECRET')).toBeNull()
    })

    test('validates range boundaries and serializes UTC request timestamps', () => {
        const exactly730Days = createMarketDataPullRequest({
            symbol: 'SOLUSDT',
            capability: 'MARKET_CANDLE',
            interval: 'D1',
            range: [
                testDate('2024-07-19T01:02:03.000Z'),
                testDate('2026-07-19T01:02:03.000Z'),
            ] as never,
        }, Date.parse('2026-07-19T02:00:00.000Z'))
        expect(exactly730Days.request).toEqual({
            symbol: 'SOLUSDT',
            capability: 'MARKET_CANDLE',
            interval: 'D1',
            startInclusive: '2024-07-19T01:02:03.000Z',
            endExclusive: '2026-07-19T01:02:03.000Z',
        })

        expect(createMarketDataPullRequest({
            symbol: 'BTCUSDT',
            capability: 'FUNDING_RATE',
            interval: null,
            range: [
                testDate('2023-03-01T00:00:00.000Z'),
                testDate('2025-03-01T00:00:00.000Z'),
            ] as never,
        }, Date.parse('2025-03-02T00:00:00.000Z')).error).toContain('730 天')
        expect(createMarketDataPullRequest({
            symbol: 'BTCUSDT',
            capability: 'MARKET_CANDLE',
            interval: 'M1',
            range: [
                testDate('2026-07-19T01:00:00.000Z'),
                testDate('2026-07-19T01:00:00.000Z'),
            ] as never,
        }).error).toContain('早于')
        expect(createMarketDataPullRequest({
            symbol: 'BTCUSDT',
            capability: 'FUNDING_RATE',
            interval: 'D1',
            range: [
                testDate('2026-07-18T00:00:00.000Z'),
                testDate('2026-07-20T00:00:00.000Z'),
            ] as never,
        }, Date.parse('2026-07-19T00:00:00.000Z')).error).toContain('当前时间')
        expect(createMarketDataPullRequest({
            symbol: 'BTCUSDT',
            capability: 'FUNDING_RATE',
            interval: null,
            range: [
                testDate('2024-07-18T00:00:00.000Z'),
                testDate('2026-07-19T00:00:00.000Z'),
            ] as never,
        }, Date.parse('2026-07-19T00:00:00.000Z')).error).toContain('2 年')
    })

    test('marks a running job stale from its last server update', () => {
        expect(isStaleRunningJob({
            ...failedJob,
            status: 'RUNNING',
            updatedAt: '2026-07-16T00:00:00Z',
        }, Date.parse('2026-07-16T00:06:00Z'))).toBe(true)
        expect(isStaleRunningJob(failedJob, Date.parse('2026-07-16T00:06:00Z'))).toBe(false)
    })

    test('requires Popconfirm and suppresses duplicate retry clicks while the request is pending', async () => {
        const pending = deferred<void>()
        mocks.retry.mockReturnValue(pending.promise)
        renderPage()
        await userEvent.click(await screen.findByRole('tab', {name: '同步任务'}))
        await screen.findByText('BAR_BACKFILL')

        await userEvent.click(screen.getByRole('button', {name: /重\s*试/}))
        expect(mocks.retry).not.toHaveBeenCalled()
        await userEvent.click(await screen.findByRole('button', {name: /OK|确\s*定/}))
        await waitFor(() => expect(mocks.retry).toHaveBeenCalledTimes(1))
        expect(screen.getByRole('button', {name: /重\s*试/}).hasAttribute('disabled')).toBe(true)

        act(() => pending.resolve())
        await waitFor(() => expect(mocks.jobs).toHaveBeenCalledTimes(2))
        expect(mocks.retry).toHaveBeenCalledTimes(1)
    })

    test('enforces resolve button permission before showing the quality confirmation', async () => {
        mocks.canResolve = false
        renderPage()
        await userEvent.click(await screen.findByRole('tab', {name: '数据质量'}))
        await screen.findByText('PRICE_GAP')

        const button = screen.getByRole('button', {name: '标记解决'})
        expect(button.textContent).toBe('标记解决')
        expect(button.hasAttribute('disabled')).toBe(true)
        expect(mocks.resolve).not.toHaveBeenCalled()
    })
})

const failedJob = {
    id: 7,
    jobType: 'BAR_BACKFILL',
    status: 'FAILED',
    triggerSource: 'MANUAL' as const,
    sourceCode: 'BITGET',
    symbol: 'BTCUSDT',
    capability: 'MARKET_CANDLE',
    priceType: 'MARKET',
    interval: 'M1',
    startInclusive: '2026-07-15T00:00:00Z',
    endExclusive: '2026-07-16T00:00:00Z',
    priority: 10,
    attempts: 3,
    maxAttempts: 3,
    availableAt: '2026-07-16T00:00:00Z',
    startedAt: '2026-07-16T00:01:00Z',
    finishedAt: '2026-07-16T00:02:00Z',
    fetchedCount: 120,
    writtenCount: 118,
    lastErrorCode: 'PROVIDER_TIMEOUT',
    lastErrorMessage: 'timeout',
    createdAt: '2026-07-16T00:00:00Z',
    updatedAt: '2026-07-16T00:00:00Z',
}

const btcSubscription = {
    sourceCode: 'BITGET',
    productType: 'USDT_FUTURES',
    symbol: 'BTCUSDT',
    status: 'ACTIVE',
    capabilities: ['MARKET_CANDLE', 'FUNDING_RATE'],
    priceTypes: ['MARKET'],
    intervals: ['M1', 'M5', 'M15', 'M30', 'H1', 'H4', 'D1'],
    refreshIntervals: {},
    backfillWindows: {},
    retention: {},
}

const solSubscription = {...btcSubscription, symbol: 'SOLUSDT'}

const pullResponse = {
    jobId: 21,
    jobType: 'FUNDING_RATE_BACKFILL' as const,
    status: 'PENDING' as const,
    createdAt: '2026-07-19T00:00:01Z',
}

const openIssue = {
    id: 9,
    instrumentId: 11,
    dataType: 'MARKET_CANDLE',
    priceType: 'MARKET',
    interval: 'M1',
    pointTime: '2026-07-16T00:00:00Z',
    issueCode: 'PRICE_GAP',
    severity: 'ERROR',
    resolutionStatus: 'OPEN',
    resolvedAt: null,
    createdAt: '2026-07-16T00:00:00Z',
}

function renderPage() {
    return render(<App><InvestmentPlatformPage/></App>)
}

function page<T>(records: T[]) {
    return {records, page: 1, size: 20, total: records.length, totalPages: records.length ? 1 : 0}
}

function deferred<T>() {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((resolver) => {
        resolve = resolver
    })
    return {promise, resolve}
}

function testDate(iso: string) {
    return {
        valueOf: () => Date.parse(iso),
        toISOString: () => iso,
    }
}
