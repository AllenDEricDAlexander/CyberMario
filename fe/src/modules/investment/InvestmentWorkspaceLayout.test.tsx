import {render, screen, waitFor, within} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import {MemoryRouter, Route, Routes, useLocation} from 'react-router'
import {investmentApiCodes, investmentButtonCodes, investmentMenuCodes} from './investmentPermissionCodes'
import {InvestmentWorkspaceLayout} from './InvestmentWorkspaceLayout'

const mocks = vi.hoisted(() => ({
    canCreate: true,
    list: vi.fn(),
    create: vi.fn(),
}))

vi.mock('../auth/authStore', () => ({
    useAuth: () => ({roleCodes: [], hasAnyButton: () => mocks.canCreate, hasPermission: () => false}),
    canUseRbacButton: () => mocks.canCreate,
}))

vi.mock('./services/investmentWorkspaceService', () => ({
    listInvestmentWorkspaces: mocks.list,
    createInvestmentWorkspace: mocks.create,
}))

describe('InvestmentWorkspaceLayout', () => {
    test('freezes the Investment RBAC codes used by later pages', () => {
        expect(investmentMenuCodes).toEqual({
            root: 'menu:investment',
            workspace: 'menu:investment:workspace',
            platform: 'menu:investment:platform',
        })
        expect(investmentButtonCodes.workspaceCreate).toBe('btn:investment:workspace:create')
        expect(investmentApiCodes).toEqual({
            market: 'api:investment:market:*',
            strategyRead: 'api:investment:strategy:read',
            workspace: 'api:investment:workspace:*',
            privateDetail: 'api:investment:private-detail:*',
            platform: 'api:investment:platform:*',
        })
    })

    test('renders public market content without a workspace and navigates in the approved order', async () => {
        mocks.list.mockReset().mockResolvedValue(page([]))
        renderWorkspace('/investment/market')

        expect(await screen.findByText('/investment/market')).toBeTruthy()
        const navigation = screen.getByRole('navigation', {name: '投资工作区导航'})
        expect(within(navigation).getAllByRole('tab').map((tab) => tab.textContent)).toEqual([
            '投资总览',
            '合约行情',
            '分析报告',
            '量化回测',
            '模拟盘',
            'Agent 交易',
        ])

        await userEvent.click(within(navigation).getByRole('tab', {name: '量化回测'}))
        await waitFor(() => expect(screen.getByText('请先选择或创建一个私人投资工作区')).toBeTruthy())
        expect(screen.queryByText('/investment/quant')).toBeNull()
    })

    test('does not expose workspace create actions without the button permission', async () => {
        mocks.canCreate = false
        mocks.list.mockReset().mockResolvedValue(page([]))
        renderWorkspace('/investment/overview')

        expect(await screen.findByText('请先选择或创建一个私人投资工作区')).toBeTruthy()
        expect(screen.queryByRole('button', {name: '创建工作区'})).toBeNull()
        mocks.canCreate = true
    })
})

function PathProbe() {
    const location = useLocation()
    return <div>{location.pathname}</div>
}

function renderWorkspace(initialEntry: string) {
    return render(
        <App>
            <MemoryRouter initialEntries={[initialEntry]}>
                <Routes>
                    <Route element={<InvestmentWorkspaceLayout/>}>
                        <Route element={<PathProbe/>} path="investment/*"/>
                    </Route>
                </Routes>
            </MemoryRouter>
        </App>,
    )
}

function page(records: never[]) {
    return {records, page: 1, size: 100, total: 0, totalPages: 0}
}
