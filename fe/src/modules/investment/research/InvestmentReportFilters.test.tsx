import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import {
    InvestmentReportFilters,
    investmentReportTypeOptions,
} from './InvestmentReportFilters'

describe('InvestmentReportFilters', () => {
    test('keeps all six fixed report types visible and selectable', async () => {
        const onChange = vi.fn()
        render(<App><InvestmentReportFilters onReportTypeChange={onChange}/></App>)

        expect(investmentReportTypeOptions.map(({value}) => value)).toEqual([
            'MARKET_OVERVIEW',
            'INSTRUMENT_ANALYSIS',
            'STRATEGY_ANALYSIS',
            'BACKTEST_REPORT',
            'PORTFOLIO_REPORT',
            'AGENT_ANALYSIS',
        ])

        await userEvent.click(screen.getByLabelText('报告类型筛选'))
        for (const label of ['市场概览', '合约分析', '策略分析', '回测报告', '组合报告', 'Agent 分析']) {
            expect((await screen.findAllByText(label)).length).toBeGreaterThan(0)
        }
        const options = await screen.findAllByText('Agent 分析')
        await userEvent.click(options.at(-1) as HTMLElement)

        expect(onChange).toHaveBeenCalledWith('AGENT_ANALYSIS', expect.anything())
    })
})
