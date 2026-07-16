import {screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, test, vi} from 'vitest'
import {renderInvestmentPage} from '../test/renderInvestmentPage'
import type {InvestmentPaperTradeResult} from '../types/investmentPortfolioTypes'
import {TradeIntentDrawer} from './TradeIntentDrawer'

describe('TradeIntentDrawer', () => {
    test('blocks duplicate submits and renders a risk rejection as a domain result', async () => {
        const user = userEvent.setup()
        let resolve!: (value: InvestmentPaperTradeResult) => void
        const onSubmit = vi.fn().mockReturnValue(new Promise<InvestmentPaperTradeResult>((done) => { resolve = done }))
        renderInvestmentPage(<TradeIntentDrawer onClose={vi.fn()} onSubmit={onSubmit} open/>)

        await user.type(screen.getByLabelText('内部合约 ID'), '501')
        await user.type(screen.getByLabelText('数量'), '1')
        await user.type(screen.getByLabelText('请求名义价值（USDT）'), '100')
        await user.type(screen.getByLabelText('杠杆（倍）'), '3')
        const submit = screen.getByRole('button', {name: '提交模拟委托'})
        await user.click(submit)
        await user.click(submit)

        expect(onSubmit).toHaveBeenCalledTimes(1)
        resolve({
            intentId: 31,
            intentStatus: 'REJECTED',
            order: null,
            fill: null,
            riskResults: [{
                ruleCode: 'MAX_ORDER_NOTIONAL', passed: false, observedValue: '100', limitValue: '50',
                message: '单笔名义价值超过限制', details: {}, checkedAt: '2026-07-17T00:00:00Z',
            }],
        })

        expect(await screen.findByText('风险校验未通过')).toBeTruthy()
        expect(screen.getByText('MAX_ORDER_NOTIONAL')).toBeTruthy()
        expect(screen.getByText(/观察值 100 \/ 限制 50/)).toBeTruthy()
    })

    test('distinguishes an accepted pending order from a synchronous fill', async () => {
        const user = userEvent.setup()
        const onSubmit = vi.fn().mockResolvedValue({
            intentId: 32, intentStatus: 'ACCEPTED', riskResults: [], fill: null,
            order: {orderId: 41, status: 'PENDING_MATCH', submittedAt: '2026-07-17T00:00:00Z', matchedAt: null},
        })
        renderInvestmentPage(<TradeIntentDrawer onClose={vi.fn()} onSubmit={onSubmit} open/>)
        await user.type(screen.getByLabelText('内部合约 ID'), '501')
        await user.type(screen.getByLabelText('数量'), '1')
        await user.type(screen.getByLabelText('请求名义价值（USDT）'), '100')
        await user.type(screen.getByLabelText('杠杆（倍）'), '3')
        await user.click(screen.getByRole('button', {name: '提交模拟委托'}))

        await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
        expect(await screen.findByText(/#41 \/ PENDING_MATCH/)).toBeTruthy()
        expect(screen.getByText(/保持待撮合状态/)).toBeTruthy()
    })
})
