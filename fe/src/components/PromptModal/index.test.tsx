import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import {PromptModal, type PromptField} from './index'

const passwordFields: PromptField[] = [
    {
        name: 'password',
        label: '新密码',
        type: 'password',
        rules: [
            {required: true, message: '请输入新密码'},
            {min: 8, message: '密码至少 8 位'},
        ],
    },
]

function renderPrompt(overrides: Partial<Parameters<typeof PromptModal>[0]> = {}) {
    const onSubmit = vi.fn()
    const onCancel = vi.fn()
    render(
        <App>
            <PromptModal
                fields={passwordFields}
                onCancel={onCancel}
                onSubmit={onSubmit}
                open
                title="重置密码"
                {...overrides}
            />
        </App>,
    )
    return {onSubmit, onCancel}
}

async function submit() {
    await userEvent.click(screen.getByRole('button', {name: /确\s*认/}))
}

describe('PromptModal', () => {
    test('blocks submit and shows the field message when empty', async () => {
        const {onSubmit} = renderPrompt()

        await submit()

        expect(await screen.findByText('请输入新密码')).toBeTruthy()
        expect(onSubmit).not.toHaveBeenCalled()
    })

    test('enforces field rules before calling onSubmit', async () => {
        const {onSubmit} = renderPrompt()

        await userEvent.type(screen.getByLabelText('新密码'), 'short')
        await submit()

        expect(await screen.findByText('密码至少 8 位')).toBeTruthy()
        expect(onSubmit).not.toHaveBeenCalled()
    })

    test('submits the collected values once valid', async () => {
        const {onSubmit} = renderPrompt()

        await userEvent.type(screen.getByLabelText('新密码'), 'LongEnough1')
        await submit()

        expect(onSubmit).toHaveBeenCalledWith({password: 'LongEnough1'})
    })

    test('keeps the dialog open and surfaces a server rejection inline', async () => {
        const onSubmit = vi.fn().mockRejectedValue(new Error('旧密码不正确'))
        render(
            <App>
                <PromptModal
                    fields={passwordFields}
                    onCancel={vi.fn()}
                    onSubmit={onSubmit}
                    open
                    title="重置密码"
                />
            </App>,
        )

        await userEvent.type(screen.getByLabelText('新密码'), 'LongEnough1')
        await submit()

        expect(await screen.findByText('旧密码不正确')).toBeTruthy()
        // The form is still mounted, so the user can correct and retry.
        expect(screen.getByLabelText('新密码')).toBeTruthy()
    })
})
