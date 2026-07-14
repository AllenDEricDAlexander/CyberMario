import {screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    createNutritionMealConfirmation,
    listNutritionFamilies,
    listNutritionMealConfirmations,
    listNutritionMembers,
    listTodayNutritionMealPlans,
} from './nutritionService'
import {family, member} from './test/nutritionTestData'
import {renderNutritionPage} from './test/renderNutritionPage'
import {Component as MealConfirmationPage} from './MealConfirmationPage'

const authAccess = vi.hoisted(() => ({canMutate: true}))
vi.mock('../auth/authStore', () => ({
    useAuth: () => ({roleCodes: [], hasAnyButton: () => authAccess.canMutate, hasPermission: () => authAccess.canMutate}),
    canUseRbacButton: () => authAccess.canMutate,
}))
vi.mock('./nutritionService', () => ({
    listNutritionFamilies: vi.fn(),
    listTodayNutritionMealPlans: vi.fn(),
    listNutritionMembers: vi.fn(),
    listNutritionMealConfirmations: vi.fn(),
    createNutritionMealConfirmation: vi.fn(),
}))

const plan = {
    id: 81,
    familyId: family.id,
    planDate: '2026-07-14',
    status: 'PUBLISHED' as const,
    version: 3,
    title: 'Tuesday dinner',
    confirmedMemberCount: 0,
    risks: [{id: 301, riskLevel: 'MEDIUM' as const, riskMessage: '控钠目标接近上限', blocking: false, requiresConfirmation: true, acknowledged: true}],
    publishable: true,
    items: [{id: 101, mealPlanId: 81, mealType: 'DINNER' as const, recipeId: 52, dishName: '番茄意面', servingCount: '2', sortOrder: 1, version: 1}],
    createdAt: '2026-07-01T00:00:00Z',
    updatedAt: '2026-07-01T00:00:00Z',
}

describe('MealConfirmationPage', () => {
    beforeEach(() => {
        authAccess.canMutate = true
        vi.clearAllMocks()
        vi.mocked(listNutritionFamilies).mockResolvedValue([family])
        vi.mocked(listTodayNutritionMealPlans).mockResolvedValue([plan])
        vi.mocked(listNutritionMembers).mockResolvedValue([member])
        vi.mocked(listNutritionMealConfirmations).mockResolvedValue([])
        vi.mocked(createNutritionMealConfirmation).mockResolvedValue({
            id: 501,
            familyId: family.id,
            mealPlanId: plan.id,
            memberProfileId: member.id,
            confirmationStatus: 'CONFIRMED',
            eatAtHome: true,
            items: [],
            createdAt: plan.createdAt,
            updatedAt: plan.updatedAt,
        })
    })

    test('member submits dish-level serving selections', async () => {
        const user = userEvent.setup()
        renderNutritionPage(<MealConfirmationPage/>)
        await screen.findByText('番茄意面')

        const serving = screen.getByLabelText('番茄意面份数')
        await user.clear(serving)
        await user.type(serving, '1.5')
        await user.click(screen.getByRole('checkbox', {name: '确认番茄意面中风险'}))
        await user.click(screen.getByRole('button', {name: /提交确认/}))

        await waitFor(() => {
            expect(createNutritionMealConfirmation).toHaveBeenCalledWith(
                family.id,
                plan.id,
                expect.objectContaining({
                    memberProfileId: member.id,
                    items: [expect.objectContaining({mealPlanItemId: 101, selected: true, servingCount: '1.5'})],
                }),
            )
        })
    })

    test('high risk and missing mutation permission disable dish submission', async () => {
        authAccess.canMutate = false
        vi.mocked(listTodayNutritionMealPlans).mockResolvedValue([{
            ...plan,
            risks: [{id: 302, riskLevel: 'HIGH', riskMessage: '花生过敏', blocking: true, requiresConfirmation: false, acknowledged: false}],
        }])
        renderNutritionPage(<MealConfirmationPage/>)
        await screen.findByText('花生过敏')

        expect(screen.getByRole('checkbox', {name: '选择番茄意面'}).hasAttribute('disabled')).toBe(true)
        expect(screen.getByRole('button', {name: /提交确认/}).hasAttribute('disabled')).toBe(true)
    })
})
