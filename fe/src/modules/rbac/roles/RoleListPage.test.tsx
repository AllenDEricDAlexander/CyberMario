import {render, screen, waitFor, within} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {MemoryRouter} from 'react-router'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {ApiRequestError} from '../../../types/api'
import type {PermissionResponse, RoleResponse} from '../rbacTypes'
import {Component as RoleListPage} from './RoleListPage'

const mocks = vi.hoisted(() => ({
    getRoles: vi.fn(),
    getPermissions: vi.fn(),
    getRoleEffectivePermissions: vi.fn(),
    getRolePermissions: vi.fn(),
    getRoleInheritance: vi.fn(),
}))

vi.mock('../rbacService', () => ({
    createRole: vi.fn(),
    deleteRole: vi.fn(),
    getPermissions: mocks.getPermissions,
    getRoleEffectivePermissions: mocks.getRoleEffectivePermissions,
    getRoleInheritance: mocks.getRoleInheritance,
    getRolePermissions: mocks.getRolePermissions,
    getRoles: mocks.getRoles,
    replaceRoleInheritance: vi.fn(),
    replaceRolePermissions: vi.fn(),
    updateRole: vi.fn(),
}))

vi.mock('../../auth/authStore', () => ({
    useAuth: () => ({roleCodes: [], hasPermission: vi.fn(), hasAnyButton: vi.fn()}),
    canUseRbacButton: () => true,
}))

describe('RoleListPage effective permission rows', () => {
    beforeEach(() => {
        Object.values(mocks).forEach((mock) => mock.mockReset())
        mocks.getRoles.mockResolvedValue({
            records: [role(1, 'ADMIN', '管理员'), role(2, 'AUDITOR', '审计员')],
            page: 1,
            size: 20,
            total: 2,
            totalPages: 1,
        })
        mocks.getPermissions.mockResolvedValue({
            records: [permission(10, '用户管理'), permission(20, '审计查询')],
            page: 1,
            size: 500,
            total: 2,
            totalPages: 1,
        })
        mocks.getRoleEffectivePermissions.mockImplementation((id: number) => (
            Promise.resolve(id === 1 ? [10] : [20])
        ))
    })

    test('each expanded row loads and shows its own role effective permissions', async () => {
        const user = userEvent.setup()
        renderPage()
        expect(await screen.findByText('管理员')).toBeTruthy()

        // Nothing is fetched until a row is actually expanded.
        expect(mocks.getRoleEffectivePermissions).not.toHaveBeenCalled()

        await user.click(expandButtonFor('管理员'))
        expect(await screen.findByText('用户管理')).toBeTruthy()
        expect(mocks.getRoleEffectivePermissions).toHaveBeenCalledWith(1)
        expect(screen.queryByText('审计查询')).toBeNull()

        // The second row must show its own set, not a copy of the first row's.
        await user.click(expandButtonFor('审计员'))
        expect(await screen.findByText('审计查询')).toBeTruthy()
        expect(mocks.getRoleEffectivePermissions).toHaveBeenCalledWith(2)
        expect(screen.getByText('用户管理')).toBeTruthy()
    })

    test('a row with no effective permissions shows the empty state without borrowing another row', async () => {
        mocks.getRoleEffectivePermissions.mockImplementation((id: number) => (
            Promise.resolve(id === 1 ? [10] : [])
        ))
        const user = userEvent.setup()
        renderPage()
        expect(await screen.findByText('管理员')).toBeTruthy()

        await user.click(expandButtonFor('管理员'))
        expect(await screen.findByText('用户管理')).toBeTruthy()

        await user.click(expandButtonFor('审计员'))
        expect(await screen.findByText('暂无有效权限')).toBeTruthy()
    })

    test('a failed row reports the error and retries only that role', async () => {
        mocks.getRoleEffectivePermissions.mockRejectedValueOnce(
            new ApiRequestError('权限服务不可用', {code: 'SERVER_ERROR', status: 500}),
        )
        const user = userEvent.setup()
        renderPage()
        expect(await screen.findByText('管理员')).toBeTruthy()

        await user.click(expandButtonFor('管理员'))
        expect(await screen.findByText('有效权限加载失败')).toBeTruthy()

        await user.click(screen.getByRole('button', {name: /重\s*试/}))
        expect(await screen.findByText('用户管理')).toBeTruthy()
        await waitFor(() => expect(mocks.getRoleEffectivePermissions).toHaveBeenCalledTimes(2))
        expect(mocks.getRoleEffectivePermissions).toHaveBeenNthCalledWith(2, 1)
    })

    test('opening the permission drawer no longer leaks its role into other expanded rows', async () => {
        mocks.getRolePermissions.mockResolvedValue([10])
        const user = userEvent.setup()
        renderPage()
        expect(await screen.findByText('管理员')).toBeTruthy()

        await user.click(within(rowFor('管理员')).getByRole('button', {name: /权\s*限/}))
        await waitFor(() => expect(mocks.getRolePermissions).toHaveBeenCalledWith(1))

        // The drawer must not populate any row's effective summary on its own.
        expect(mocks.getRoleEffectivePermissions).not.toHaveBeenCalled()
    })
})

function renderPage() {
    render(
        <MemoryRouter>
            <App>
                <RoleListPage/>
            </App>
        </MemoryRouter>,
    )
}

function rowFor(roleName: string) {
    const row = screen.getByText(roleName).closest('tr')
    if (!row) {
        throw new Error(`no row rendered for ${roleName}`)
    }
    return row
}

function expandButtonFor(roleName: string) {
    return within(rowFor(roleName)).getByRole('button', {name: /expand row/i})
}

function role(id: number, roleCode: string, roleName: string): RoleResponse {
    return {id, roleCode, roleName, status: 'ENABLED', sortNo: id, builtIn: false}
}

function permission(id: number, permName: string): PermissionResponse {
    return {id, permCode: `perm:${id}`, permName, permType: 'API', status: 'ENABLED', sortNo: id}
}
