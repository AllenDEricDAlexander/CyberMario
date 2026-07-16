import {screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, test, vi} from 'vitest'
import {renderInvestmentPage} from '../test/renderInvestmentPage'
import type {
    CreateInvestmentPaperAccountRequest,
} from '../types/investmentPortfolioTypes'
import {PaperAccountCreateDrawer} from './PaperAccountCreateDrawer'

describe('PaperAccountCreateDrawer', () => {
    test('submits required decimal strings and never exposes initial trading switches', async () => {
        const user = userEvent.setup()
        const onCreate = vi.fn<(
            request: CreateInvestmentPaperAccountRequest
        ) => Promise<unknown>>().mockResolvedValue({})
        renderInvestmentPage(<PaperAccountCreateDrawer onClose={vi.fn()} onCreate={onCreate} open/>)

        expect(screen.getByText(/固定为关闭/)).toBeTruthy()
        expect(screen.queryByRole('switch')).toBeNull()
        expect(screen.getByLabelText('最大杠杆（倍）')).toBeTruthy()
        expect(screen.getByLabelText('最大滑点（基点）')).toBeTruthy()
        await user.type(screen.getByLabelText('账户名称'), '个人模拟')
        await user.click(screen.getByRole('button', {name: '创建模拟账户'}))

        await waitFor(() => expect(onCreate).toHaveBeenCalledTimes(1))
        const request = onCreate.mock.calls[0][0]
        expect(request.initialEquity).toBe('10000')
        expect(request.riskProfile.maxLeverage).toBe('10')
        expect(request.riskProfile.maxDrawdownRatio).toBe('0.2')
        expect(request).not.toHaveProperty('tradingEnabled')
        expect(request).not.toHaveProperty('agentAutoTradeEnabled')
    })
})
