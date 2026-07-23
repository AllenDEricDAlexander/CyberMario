import {render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {afterEach, beforeEach, describe, expect, test, vi} from 'vitest'
import {MemoryRouter, Route, Routes} from 'react-router'
import {completeAccountActivation} from '../authService'
import {ActivationPage} from './ActivationPage'

vi.mock('../authService', () => ({completeAccountActivation: vi.fn()}))

beforeEach(() => {
    vi.mocked(completeAccountActivation).mockResolvedValue(undefined)
})

afterEach(() => {
    window.history.replaceState({}, '', '/')
    vi.clearAllMocks()
    vi.restoreAllMocks()
})

function pageTree() {
    return (
        <App>
            <MemoryRouter initialEntries={['/activate']}>
                <Routes>
                    <Route path="/activate" element={<ActivationPage/>}/>
                    <Route path="/login" element={<div>login-destination</div>}/>
                </Routes>
            </MemoryRouter>
        </App>
    )
}

describe('ActivationPage', () => {
    test('captures then clears the fragment without using browser storage', async () => {
        const storageSpy = vi.spyOn(Storage.prototype, 'setItem')
        window.history.replaceState({}, '', '/activate#token=raw-token')

        render(pageTree())

        await waitFor(() => expect(window.location.hash).toBe(''))
        expect(screen.getByRole('heading', {name: '激活账号'})).toBeTruthy()
        expect(storageSpy).not.toHaveBeenCalled()
    })

    test('validates confirmation and redirects after encrypted activation', async () => {
        window.history.replaceState({}, '', '/activate#token=raw-token')
        const user = userEvent.setup()
        render(pageTree())

        await user.type(screen.getByLabelText('新密码'), 'new-password-123')
        await user.type(screen.getByLabelText('确认密码'), 'new-password-123')
        await user.click(screen.getByRole('button', {name: '完成激活'}))

        await waitFor(() => expect(completeAccountActivation)
            .toHaveBeenCalledWith('raw-token', 'new-password-123'))
        expect(await screen.findByText('login-destination')).toBeTruthy()
    })

    test('uses one uniform message for a rejected activation link', async () => {
        vi.mocked(completeAccountActivation).mockRejectedValueOnce(new Error('backend detail'))
        window.history.replaceState({}, '', '/activate#token=raw-token')
        const user = userEvent.setup()
        render(pageTree())
        await user.type(screen.getByLabelText('新密码'), 'new-password-123')
        await user.type(screen.getByLabelText('确认密码'), 'new-password-123')
        await user.click(screen.getByRole('button', {name: '完成激活'}))

        expect(await screen.findByText('激活链接无效或已过期，请联系管理员重新发送')).toBeTruthy()
        expect(screen.queryByText('backend detail')).toBeNull()
    })

    test('shows the uniform invalid-link message when the fragment is missing', () => {
        window.history.replaceState({}, '', '/activate')
        render(pageTree())
        expect(screen.getByText('激活链接无效或已过期，请联系管理员重新发送')).toBeTruthy()
        expect(screen.getByRole('button', {name: '完成激活'}).hasAttribute('disabled')).toBe(true)
    })

    test('rejects mismatched passwords before calling the backend', async () => {
        window.history.replaceState({}, '', '/activate#token=raw-token')
        const user = userEvent.setup()
        render(pageTree())
        await user.type(screen.getByLabelText('新密码'), 'new-password-123')
        await user.type(screen.getByLabelText('确认密码'), 'different-password')
        await user.click(screen.getByRole('button', {name: '完成激活'}))
        expect(await screen.findByText('两次输入的密码不一致')).toBeTruthy()
        expect(completeAccountActivation).not.toHaveBeenCalled()
    })
})
