import {render, screen} from '@testing-library/react'
import {App} from 'antd'
import {MemoryRouter} from 'react-router'
import {describe, expect, test, vi} from 'vitest'
import {LoginPage} from './LoginPage'

vi.mock('../authStore', () => ({
    useAuth: () => ({
        bootstrapping: false,
        authenticated: false,
        roleCodes: [],
        menus: [],
        buttonCodes: [],
        permissionCodes: [],
        login: vi.fn(),
        register: vi.fn(),
        logout: vi.fn(),
        reload: vi.fn(),
        hasButton: vi.fn(() => false),
        hasAnyButton: vi.fn(() => false),
        hasPermission: vi.fn(() => false),
    }),
}))

describe('LoginPage', () => {
    test('shows the activation success handoff from the query flag', () => {
        render(
            <App>
                <MemoryRouter initialEntries={['/login?activated=1']}>
                    <LoginPage/>
                </MemoryRouter>
            </App>,
        )

        expect(screen.getByText('账号激活成功，请使用新密码登录')).toBeTruthy()
    })
})
