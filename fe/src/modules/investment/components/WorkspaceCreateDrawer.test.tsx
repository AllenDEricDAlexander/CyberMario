import {screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, test, vi} from 'vitest'
import {renderInvestmentPage} from '../test/renderInvestmentPage'
import {WorkspaceCreateDrawer} from './WorkspaceCreateDrawer'

describe('WorkspaceCreateDrawer', () => {
    test('validates and submits a trimmed workspace name through the parent action', async () => {
        const user = userEvent.setup()
        const onCreate = vi.fn().mockResolvedValue({id: 7})
        const onClose = vi.fn()
        renderInvestmentPage(
            <WorkspaceCreateDrawer onClose={onClose} onCreate={onCreate} open/>,
        )

        await user.type(screen.getByLabelText('工作区名称'), '合约研究')
        await user.click(screen.getByRole('button', {name: /创\s*建/}))

        await waitFor(() => expect(onCreate).toHaveBeenCalledWith('合约研究'))
        expect(onClose).toHaveBeenCalled()
    })

    test('keeps the drawer open and exposes a service error', async () => {
        const user = userEvent.setup()
        const onCreate = vi.fn().mockRejectedValue(new Error('名称已存在'))
        const onClose = vi.fn()
        renderInvestmentPage(
            <WorkspaceCreateDrawer onClose={onClose} onCreate={onCreate} open/>,
        )

        await user.type(screen.getByLabelText('工作区名称'), '重复名称')
        await user.click(screen.getByRole('button', {name: /创\s*建/}))

        expect(await screen.findByText('名称已存在')).toBeTruthy()
        expect(onClose).not.toHaveBeenCalled()
    })
})
