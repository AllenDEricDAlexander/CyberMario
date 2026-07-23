import {render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import type {UserResponse} from '../rbacTypes'
import {UserEditorDrawer} from './UserEditorDrawer'

describe('UserEditorDrawer', () => {
    test('new-user mode requires email and omits password and status', async () => {
        const user = userEvent.setup()
        render(
            <App>
                <UserEditorDrawer
                    onClose={vi.fn()}
                    onSubmit={vi.fn().mockResolvedValue(undefined)}
                    open
                />
            </App>,
        )

        expect(screen.queryByLabelText('初始密码')).toBeNull()
        expect(screen.queryByLabelText('状态')).toBeNull()
        await user.type(screen.getByLabelText('账号'), 'mario')
        await user.type(screen.getByLabelText('用户名'), 'mario')
        await user.click(screen.getByRole('button', {name: /保\s*存/}))
        expect(await screen.findByText('请输入邮箱')).toBeTruthy()
    })

    test('edit mode keeps status and allows an existing empty email', async () => {
        const onSubmit = vi.fn().mockResolvedValue(undefined)
        const user = userEvent.setup()
        const value: UserResponse = {
            id: 7,
            accountNo: 'mario',
            username: 'mario',
            status: 'ENABLED',
            locked: false,
            passwordExpired: false,
            activationStatus: 'ACTIVATED',
        }
        render(
            <App>
                <UserEditorDrawer onClose={vi.fn()} onSubmit={onSubmit} open value={value}/>
            </App>,
        )

        expect(screen.getByLabelText('状态')).toBeTruthy()
        await user.click(screen.getByRole('button', {name: /保\s*存/}))
        await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
        expect(screen.queryByText('请输入邮箱')).toBeNull()
    })
})
