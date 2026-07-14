import {render, screen, within} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {describe, expect, test} from 'vitest'
import {MemoryRouter, Route, Routes, useLocation} from 'react-router'
import {NutritionWorkspaceLayout} from './NutritionWorkspaceLayout'

const expectedLabels = [
    '营养首页',
    '家庭管理',
    '成员健康',
    '家庭菜谱',
    'AI 菜单',
    '用餐确认',
    '餐食汇总',
    '采购清单',
    '预算分析',
    '营养记录',
]

function PathProbe() {
    const location = useLocation()
    return <div data-testid="current-path">{location.pathname}</div>
}

function renderWorkspace(initialEntry: string) {
    return render(
        <App>
            <MemoryRouter initialEntries={[initialEntry]}>
                <Routes>
                    <Route element={<NutritionWorkspaceLayout/>}>
                        <Route element={<PathProbe/>} path="nutrition/*"/>
                    </Route>
                </Routes>
            </MemoryRouter>
        </App>,
    )
}

describe('NutritionWorkspaceLayout', () => {
    test('renders every family workflow tab in the approved order and navigates between pages', async () => {
        const user = userEvent.setup()
        renderWorkspace('/nutrition/home')
        const navigation = screen.getByRole('navigation', {name: '家庭营养导航'})

        expect(within(navigation).getAllByRole('tab').map((tab) => tab.textContent)).toEqual(expectedLabels)
        expect(within(navigation).queryByRole('tab', {name: '营养平台'})).toBeNull()

        await user.click(within(navigation).getByRole('tab', {name: '预算分析'}))

        expect(screen.getByTestId('current-path').textContent).toBe('/nutrition/budget')
    })

    test('selects the owning tab for a nested family route', () => {
        renderWorkspace('/nutrition/recipes/42')
        const navigation = screen.getByRole('navigation', {name: '家庭营养导航'})

        expect(within(navigation).getByRole('tab', {name: '家庭菜谱'}).getAttribute('aria-selected'))
            .toBe('true')
    })
})
