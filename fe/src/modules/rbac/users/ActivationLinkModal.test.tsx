import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import {ActivationLinkModal} from './ActivationLinkModal'

describe('ActivationLinkModal', () => {
    test('shows expiry, copies the mock URL, and delegates close', async () => {
        const onClose = vi.fn()
        const user = userEvent.setup()
        const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue(undefined)
        const url = 'http://localhost:5173/activate#token=raw-token'
        render(
            <App>
                <ActivationLinkModal
                    onClose={onClose}
                    title="账号已创建"
                    value={{
                        mode: 'MOCK',
                        expiresAt: '2026-07-23T00:00:00Z',
                        mockActivationUrl: url,
                    }}
                />
            </App>,
        )

        expect(screen.getAllByDisplayValue(url))
            .toContainEqual(expect.objectContaining({ariaHidden: null}))
        expect(screen.getByText(/2026/)).toBeTruthy()
        await user.click(screen.getByRole('button', {name: '复制激活链接'}))
        expect(writeText).toHaveBeenCalledWith(url)
        await user.click(screen.getByRole('button', {name: /关\s*闭/}))
        expect(onClose).toHaveBeenCalledTimes(1)
    })
})
