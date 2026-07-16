import {render, screen} from '@testing-library/react'
import {App} from 'antd'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import type {
    InvestmentAgentDecisionResponse,
    InvestmentAgentRunDetailResponse,
} from '../types/investmentAgentTypes'
import {InvestmentAgentRunDrawer} from './InvestmentAgentRunDrawer'

const mocks = vi.hoisted(() => ({polling: vi.fn()}))

vi.mock('./useAgentRunPolling', () => ({useAgentRunPolling: mocks.polling}))

describe('InvestmentAgentRunDrawer', () => {
    beforeEach(() => {
        mocks.polling.mockReset().mockReturnValue({
            detail: detail(), error: undefined, polling: false, refresh: vi.fn(),
        })
    })

    test('renders explainable decisions and the complete automatic paper execution chain', () => {
        render(<App><InvestmentAgentRunDrawer onClose={vi.fn()} open runId={41}/></App>)

        expect(screen.getByText('仅模拟盘，风控通过后自动执行')).toBeTruthy()
        expect(screen.getAllByText('2026-07-17T00:00:00Z').length).toBeGreaterThan(0)
        expect(screen.getAllByText('等待突破确认')).toHaveLength(4)
        expect(screen.getAllByText('波动率')).toHaveLength(4)
        expect(screen.getAllByText('跌破支撑')).toHaveLength(4)
        expect(screen.getByText('观望')).toBeTruthy()
        expect(screen.getByText('开多')).toBeTruthy()
        expect(screen.getByText('平仓')).toBeTruthy()
        expect(screen.getByText('减仓')).toBeTruthy()
        expect(screen.getByText('模拟交易被风控拒绝，未创建委托')).toBeTruthy()
        expect(screen.getByText(/#81 \/ PENDING_MATCH/)).toBeTruthy()
        expect(screen.getByText(/#91 \/ 0.5 @ 101.2/)).toBeTruthy()
        expect(screen.getByText(/#73 \/ LIQUIDATED/)).toBeTruthy()
        expect(screen.getAllByRole('region', {name: '决策执行链'})).toHaveLength(4)
        expect(screen.queryByRole('button', {name: /批准|确认|执行/})).toBeNull()
    })

    test('shows a terminal run failure instead of inventing a decision or fill', () => {
        const failed = detail()
        failed.run = {...failed.run, status: 'FAILED', errorCode: 'MODEL_TIMEOUT', errorMessage: '模型超时'}
        failed.decisions = []
        mocks.polling.mockReturnValue({detail: failed, error: undefined, polling: false, refresh: vi.fn()})

        render(<App><InvestmentAgentRunDrawer onClose={vi.fn()} open runId={41}/></App>)

        expect(screen.getByText('MODEL_TIMEOUT')).toBeTruthy()
        expect(screen.getByText('模型超时')).toBeTruthy()
        expect(screen.queryByText(/模拟成交：#/)).toBeNull()
    })
})

function detail(): InvestmentAgentRunDetailResponse {
    return {
        run: {
            id: 41, workspaceId: 7, accountId: 21, presetCode: 'INVESTMENT_ANALYST_V1',
            genericAgentRunAuditId: 61, runType: 'AUTO_TRADE', status: 'SUCCEEDED',
            dataAsOf: '2026-07-17T00:00:00Z', reportId: 101,
            startedAt: '2026-07-17T00:00:00Z', finishedAt: '2026-07-17T00:00:10Z',
            errorCode: null, errorMessage: null, createdAt: '2026-07-17T00:00:00Z',
        },
        decisions: [
            decision(51, 'HOLD', null),
            decision(52, 'OPEN_LONG', {
                intentId: 71, intentStatus: 'RISK_REJECTED',
                riskChecks: [{
                    ruleCode: 'AGENT_AUTO_TRADE_ENABLED', passed: false, observedValue: '0', limitValue: '1',
                    message: 'Agent 开关关闭', details: {}, checkedAt: '2026-07-17T00:00:01Z',
                }],
                order: null, fill: null,
            }),
            decision(53, 'CLOSE', {
                intentId: 72, intentStatus: 'ACCEPTED', riskChecks: [],
                order: {id: 81, status: 'PENDING_MATCH', submittedAt: '2026-07-17T00:00:02Z', matchedAt: null},
                fill: null,
            }),
            decision(54, 'REDUCE', {
                intentId: 73, intentStatus: 'LIQUIDATED', riskChecks: [],
                order: {id: 82, status: 'LIQUIDATED', submittedAt: '2026-07-17T00:00:03Z', matchedAt: '2026-07-17T00:00:04Z'},
                fill: {id: 91, price: '101.2', quantity: '0.5', feeAmount: '0.02', filledAt: '2026-07-17T00:00:04Z'},
            }),
        ],
    }
}

function decision(
    id: number,
    action: InvestmentAgentDecisionResponse['action'],
    execution: InvestmentAgentDecisionResponse['execution'],
): InvestmentAgentDecisionResponse {
    const trade = action !== 'HOLD'
    return {
        id, instrumentId: 11, action, confidence: '0.75', horizon: 'INTRADAY', thesis: '等待突破确认',
        risks: ['波动率'], invalidation: ['跌破支撑'], requestedQuantity: trade ? '0.5' : null,
        requestedNotional: trade ? '50.6' : null, requestedLeverage: trade ? '2' : null,
        orderType: trade ? 'MARKET' : null, limitPrice: null,
        intentId: execution?.intentId ?? null,
        executionStatus: trade ? 'SUBMITTED' : 'NOT_APPLICABLE',
        dataAsOf: '2026-07-17T00:00:00Z', expiresAt: null, status: 'VALIDATED',
        createdAt: '2026-07-17T00:00:01Z', execution,
    }
}
