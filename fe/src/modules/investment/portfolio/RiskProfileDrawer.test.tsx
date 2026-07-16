import {screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, test, vi} from 'vitest'
import {renderInvestmentPage} from '../test/renderInvestmentPage'
import type {InvestmentRiskProfile} from '../types/investmentPortfolioTypes'
import {RiskProfileDrawer} from './RiskProfileDrawer'

describe('RiskProfileDrawer', () => {
    test('shows explicit units and keeps the optimistic version in a full replacement', async () => {
        const user = userEvent.setup()
        const profile = riskProfile()
        const onSave = vi.fn().mockResolvedValue(profile)
        renderInvestmentPage(<RiskProfileDrawer onClose={vi.fn()} onSave={onSave} open profile={profile}/>)

        expect(screen.getByLabelText('单笔最大名义价值（USDT）')).toBeTruthy()
        expect(screen.getByLabelText('行情最大年龄（秒）')).toBeTruthy()
        expect(screen.getByLabelText('最大回撤比例（0-1）')).toBeTruthy()
        await user.click(screen.getByRole('button', {name: '保存风险限制'}))

        await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1))
        expect(onSave.mock.calls[0][0]).toMatchObject({
            version: 9,
            maxLeverage: '10',
            maxOrderNotional: '1000',
            maxOpenPositions: 5,
        })
    })
})

export function riskProfile(): InvestmentRiskProfile {
    return {
        id: 31, accountId: 21, maxLeverage: '10', maxOrderNotional: '1000',
        maxPositionNotional: '5000', maxGrossExposureNotional: '10000', maxOpenPositions: 5,
        maxDailyLossAmount: '500', maxDrawdownRatio: '0.2', maxOrdersPerHour: 60,
        cooldownSeconds: 0, maxMarketDataAgeSeconds: 30, maxSlippageBps: '20', version: 9,
    }
}
