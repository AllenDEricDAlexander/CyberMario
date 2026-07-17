import {fireEvent, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    adjustNutritionConfirmedMenu,
    closeNutritionMealPlanConfirmation,
    getNutritionMealPlanSummary,
    listNutritionFamilies,
    listNutritionMealPlans,
} from './nutritionService'
import {family} from './test/nutritionTestData'
import {renderNutritionPage} from './test/renderNutritionPage'
import {Component as MealSummaryPage} from './MealSummaryPage'

const authAccess = vi.hoisted(() => ({canMutate: true}))
vi.mock('../auth/authStore', () => ({
    useAuth: () => ({roleCodes: [], hasAnyButton: () => authAccess.canMutate, hasPermission: () => authAccess.canMutate}),
    canUseRbacButton: () => authAccess.canMutate,
}))
vi.mock('./nutritionService', () => ({
    listNutritionFamilies: vi.fn(),
    listNutritionMealPlans: vi.fn(),
    getNutritionMealPlanSummary: vi.fn(),
    closeNutritionMealPlanConfirmation: vi.fn(),
    adjustNutritionConfirmedMenu: vi.fn(),
}))

const plan = {
    id: 81,
    familyId: family.id,
    planDate: '2026-07-14',
    status: 'CONFIRMING' as const,
    version: 3,
    title: 'Tuesday dinner',
    confirmedMemberCount: 2,
    risks: [],
    publishable: true,
    items: [],
    createdAt: '2026-07-01T00:00:00Z',
    updatedAt: '2026-07-01T00:00:00Z',
}
const summary = {
    mealPlanId: plan.id,
    activeMemberCount: 4,
    confirmedMemberCount: 2,
    awayMemberCount: 1,
    unconfirmedMemberCount: 1,
    riskCounts: {MEDIUM: 1},
    remarks: ['Mario 少盐'],
    readyForShopping: false,
    dishes: [{
        itemId: 101,
        dishName: '番茄意面',
        mealType: 'DINNER' as const,
        servingCount: '2',
        selectedMemberCount: 2,
        confirmedServingTotal: '2.5',
        finalServingCount: '2.5',
        adjusted: false,
    }],
}

describe('MealSummaryPage', () => {
    beforeEach(() => {
        authAccess.canMutate = true
        vi.clearAllMocks()
        vi.mocked(listNutritionFamilies).mockResolvedValue([family])
        vi.mocked(listNutritionMealPlans).mockResolvedValue([plan])
        vi.mocked(getNutritionMealPlanSummary).mockResolvedValue(summary)
        vi.mocked(closeNutritionMealPlanConfirmation).mockResolvedValue({...plan, status: 'CONFIRM_CLOSED'})
        vi.mocked(adjustNutritionConfirmedMenu).mockResolvedValue(summary)
    })

    test('shows exact participation counts and lets a cook close confirmation', async () => {
        const user = userEvent.setup()
        renderNutritionPage(<MealSummaryPage/>)

        await screen.findByText('番茄意面')
        expect(screen.getByText('已确认 2')).toBeTruthy()
        expect(screen.getByText('不在家 1')).toBeTruthy()
        expect(screen.getByText('未确认 1')).toBeTruthy()
        expect(screen.getAllByText('2.5')).toHaveLength(2)
        await user.click(screen.getByRole('button', {name: /提前关闭确认/}))

        expect(closeNutritionMealPlanConfirmation).toHaveBeenCalledWith(family.id, plan.id, true)
    })

    test('disables close confirmation without cook permission', async () => {
        authAccess.canMutate = false
        renderNutritionPage(<MealSummaryPage/>)
        await screen.findByText('番茄意面')
        expect(screen.getByRole('button', {name: /提前关闭确认/}).hasAttribute('disabled')).toBe(true)
    })

    test('adjusts final servings after confirmation closes', async () => {
        const user = userEvent.setup()
        vi.mocked(listNutritionMealPlans).mockResolvedValue([{...plan, status: 'CONFIRM_CLOSED'}])
        renderNutritionPage(<MealSummaryPage/>)

        const input = await screen.findByLabelText('最终采购份数 番茄意面')
        fireEvent.change(input, {target: {value: '3'}})
        await user.type(screen.getByLabelText('确认后菜单调整说明'), '多准备半份')
        await user.click(screen.getByRole('button', {name: /保存确认后菜单调整/}))

        expect(adjustNutritionConfirmedMenu).toHaveBeenCalledWith(family.id, plan.id, {
            expectedVersion: plan.version,
            note: '多准备半份',
            items: [{mealPlanItemId: 101, finalServingCount: 3}],
        })
    })
})
