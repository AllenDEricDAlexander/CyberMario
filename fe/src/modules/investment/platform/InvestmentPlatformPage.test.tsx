import {act, render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import InvestmentPlatformPage, {isStaleRunningJob} from './InvestmentPlatformPage'

const mocks = vi.hoisted(() => ({
    canRetry: true,
    canResolve: true,
    subscriptions: vi.fn(),
    jobs: vi.fn(),
    retry: vi.fn(),
    issues: vi.fn(),
    resolve: vi.fn(),
}))

vi.mock('../../auth/authStore', () => ({
    useAuth: () => ({roleCodes: [], hasAnyButton: vi.fn(), hasPermission: vi.fn()}),
    canUseRbacButton: (_auth: unknown, code: string) => code.endsWith('retry-job')
        ? mocks.canRetry
        : mocks.canResolve,
}))

vi.mock('../services/investmentPlatformService', () => ({
    listInvestmentPlatformSubscriptions: mocks.subscriptions,
    listInvestmentPlatformJobs: mocks.jobs,
    retryInvestmentPlatformJob: mocks.retry,
    listInvestmentDataQualityIssues: mocks.issues,
    resolveInvestmentDataQualityIssue: mocks.resolve,
}))

describe('InvestmentPlatformPage', () => {
    beforeEach(() => {
        mocks.canRetry = true
        mocks.canResolve = true
        mocks.subscriptions.mockReset().mockResolvedValue([])
        mocks.jobs.mockReset().mockResolvedValue(page([failedJob]))
        mocks.retry.mockReset().mockResolvedValue(undefined)
        mocks.issues.mockReset().mockResolvedValue(page([openIssue]))
        mocks.resolve.mockReset().mockResolvedValue(undefined)
    })

    test('renders an empty code registry as an explicit read-only state without subscription forms', async () => {
        renderPage()

        expect(await screen.findByText('尚未在代码中接入任何行情订阅')).toBeTruthy()
        expect(screen.getByText(/页面不提供新增、修改或删除订阅/)).toBeTruthy()
        expect(screen.queryByRole('button', {name: /新增|修改|删除/})).toBeNull()
        expect(mocks.subscriptions).toHaveBeenCalledTimes(1)
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
        await screen.findByText('QUOTE_REFRESH')

        await userEvent.click(screen.getByRole('button', {name: /重\s*试/}))
        expect(mocks.retry).not.toHaveBeenCalled()
        await userEvent.click(await screen.findByRole('button', {name: /OK|确\s*定/}))
        await waitFor(() => expect(mocks.retry).toHaveBeenCalledTimes(1))
        expect(screen.getByRole('button', {name: /重\s*试/}).hasAttribute('disabled')).toBe(true)

        await act(async () => pending.resolve())
        await waitFor(() => expect(mocks.jobs).toHaveBeenCalledTimes(2))
        expect(mocks.retry).toHaveBeenCalledTimes(1)
    })

    test('enforces resolve button permission before showing the quality confirmation', async () => {
        mocks.canResolve = false
        renderPage()
        await userEvent.click(await screen.findByRole('tab', {name: '数据质量'}))
        await screen.findByText('PRICE_GAP')

        const button = screen.getByRole('button', {name: /标记解决/})
        expect(button.hasAttribute('disabled')).toBe(true)
        expect(mocks.resolve).not.toHaveBeenCalled()
    })
})

const failedJob = {
    id: 7,
    jobType: 'QUOTE_REFRESH',
    status: 'FAILED',
    priority: 10,
    attempts: 3,
    maxAttempts: 3,
    availableAt: '2026-07-16T00:00:00Z',
    lastErrorCode: 'PROVIDER_TIMEOUT',
    lastErrorMessage: 'timeout',
    createdAt: '2026-07-16T00:00:00Z',
    updatedAt: '2026-07-16T00:00:00Z',
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
