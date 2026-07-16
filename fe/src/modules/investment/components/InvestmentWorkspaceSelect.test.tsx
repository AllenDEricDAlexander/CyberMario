import {screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, test, vi} from 'vitest'
import {renderInvestmentPage} from '../test/renderInvestmentPage'
import {InvestmentWorkspaceSelect} from './InvestmentWorkspaceSelect'

const workspace = {
    id: 7,
    name: '合约研究',
    baseCurrency: 'USDT',
    timezone: 'UTC',
    status: 'ACTIVE',
    createdAt: '2026-07-16T00:00:00Z',
}

describe('InvestmentWorkspaceSelect', () => {
    test('selects and clears only known workspace ids', async () => {
        const user = userEvent.setup()
        const onChange = vi.fn()
        renderInvestmentPage(
            <InvestmentWorkspaceSelect onChange={onChange} value={null} workspaces={[workspace]}/>,
        )

        await user.click(screen.getByLabelText('当前投资工作区'))
        await user.click(screen.getByText('合约研究 · USDT'))
        expect(onChange).toHaveBeenCalledWith(7)
    })
})
