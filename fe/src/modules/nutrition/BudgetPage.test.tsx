import {screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    createNutritionBudgetRule,
    deactivateNutritionBudgetRule,
    getNutritionMonthlyBudget,
    getNutritionWeeklyBudget,
    listNutritionBudgetRules,
    listNutritionFamilies,
    updateNutritionBudgetRule,
} from './nutritionService'
import {family} from './test/nutritionTestData'
import {renderNutritionPage} from './test/renderNutritionPage'
import {Component as BudgetPage} from './BudgetPage'

vi.mock('../auth/authStore', () => ({
    useAuth: () => ({roleCodes: [], hasAnyButton: () => true, hasPermission: () => true}),
    canUseRbacButton: () => true,
}))
vi.mock('./nutritionService', () => ({
    listNutritionFamilies: vi.fn(),
    getNutritionWeeklyBudget: vi.fn(),
    getNutritionMonthlyBudget: vi.fn(),
    listNutritionBudgetRules: vi.fn(),
    createNutritionBudgetRule: vi.fn(),
    updateNutritionBudgetRule: vi.fn(),
    deactivateNutritionBudgetRule: vi.fn(),
}))

const weekly = {
    periodType: 'WEEKLY', periodStart: '2026-07-13', periodEnd: '2026-07-19',
    totalAmount: '420', totalActualAmount: '300', totalEstimatedAmount: '120',
    mealPlanCount: 3, mealCount: 6, confirmedMemberCount: 4, perPersonCost: '75',
    budgetLimit: '700', usageRate: '60', shoppingCompletionRate: '40',
    dailySummaries: [{
        date: '2026-07-14', totalAmount: '120', actualAmount: '80', estimatedAmount: '40',
        mealPlanCount: 1, mealCount: 2, confirmedMemberCount: 4, perPersonCost: '20',
    }],
    dishSummaries: [{
        mealPlanId: 81, itemId: 101, planDate: '2026-07-14', mealType: 'DINNER' as const,
        dishName: '番茄意面', servingCount: '2', confirmedServingCount: '2.5',
        finalServingCount: '3', amount: '45',
    }],
    ingredientSummaries: [{
        standardFoodId: 41, rawFoodName: '番茄', unit: 'g',
        plannedAmount: '500', purchasedAmount: '400', totalAmount: '18',
    }],
    channelSummaries: [{channel: '菜市场', totalAmount: '80', itemCount: 3}],
}
const monthly = {...weekly, periodType: 'MONTHLY', periodStart: '2026-07-01', periodEnd: '2026-07-31', budgetLimit: '2600', usageRate: '35', shoppingCompletionRate: '80'}
const rule = {
    id: 801,
    familyId: family.id,
    ruleName: '周预算',
    periodType: 'WEEKLY',
    amountLimit: '700',
    currency: 'CNY',
    warningThreshold: '0.8',
    enabled: true,
    status: 'ACTIVE' as const,
    version: 1,
    createdAt: '2026-07-01T00:00:00Z',
    updatedAt: '2026-07-01T00:00:00Z',
}

describe('BudgetPage', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        vi.mocked(listNutritionFamilies).mockResolvedValue([family])
        vi.mocked(getNutritionWeeklyBudget).mockResolvedValue(weekly)
        vi.mocked(getNutritionMonthlyBudget).mockResolvedValue(monthly)
        vi.mocked(listNutritionBudgetRules).mockResolvedValue([rule])
        vi.mocked(createNutritionBudgetRule).mockResolvedValue(rule)
        vi.mocked(updateNutritionBudgetRule).mockResolvedValue({...rule, amountLimit: '750'})
        vi.mocked(deactivateNutritionBudgetRule).mockResolvedValue(undefined)
    })

    test('labels budget usage separately from shopping completion', async () => {
        renderNutritionPage(<BudgetPage/>)
        await screen.findByText('周预算')

        expect(screen.getAllByText('预算使用率').length).toBeGreaterThan(0)
        expect(screen.getAllByText('采购完成率').length).toBeGreaterThan(0)
        expect(screen.getByText('60%')).toBeTruthy()
        expect(screen.getByText('40%')).toBeTruthy()
        expect(screen.getByText('本周确认后菜单成本')).toBeTruthy()
        expect(screen.getByText('番茄意面')).toBeTruthy()
        expect(screen.getByText('菜市场')).toBeTruthy()
    })

    test('creates, updates, and deactivates budget rules', async () => {
        const user = userEvent.setup()
        renderNutritionPage(<BudgetPage/>)
        await screen.findByText('周预算')

        await user.click(screen.getByRole('button', {name: /新增预算规则/}))
        await user.type(screen.getByLabelText('规则名称'), '月度提醒')
        await user.click(screen.getByLabelText('周期类型'))
        await user.click(screen.getByText('MONTHLY', {selector: '.ant-select-item-option-content'}))
        await user.type(screen.getByLabelText('预算上限'), '2600')
        await user.click(screen.getByRole('button', {name: /保存规则/}))
        await waitFor(() => expect(createNutritionBudgetRule).toHaveBeenCalled())

        await user.click(screen.getByRole('button', {name: '编辑预算规则 801'}))
        const amount = screen.getByLabelText('预算上限')
        await user.clear(amount)
        await user.type(amount, '750')
        await user.click(screen.getByRole('button', {name: /保存规则/}))
        await waitFor(() => expect(updateNutritionBudgetRule).toHaveBeenCalledWith(
            family.id,
            rule.id,
            expect.objectContaining({amountLimit: 750}),
        ))

        await user.click(screen.getByRole('button', {name: '停用预算规则 801'}))
        expect(deactivateNutritionBudgetRule).toHaveBeenCalledWith(family.id, rule.id)
    })

    test('explains that cost analysis remains available before a budget rule is configured', async () => {
        vi.mocked(listNutritionBudgetRules).mockResolvedValue([])
        renderNutritionPage(<BudgetPage/>)

        expect(await screen.findByText(/尚未设置预算规则/)).toBeTruthy()
        expect(screen.getByText('本周每日成本')).toBeTruthy()
    })
})
