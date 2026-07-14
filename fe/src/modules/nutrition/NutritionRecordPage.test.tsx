import {screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    adjustNutritionRecord,
    createNutritionExtraFoodRecord,
    generateNutritionFamilyMonthlyReport,
    generateNutritionFamilyWeeklyReport,
    getNutritionDailyOverview,
    getNutritionFamilyMonthlyReport,
    getNutritionFamilyWeeklyReport,
    listNutritionFamilies,
    listNutritionMembers,
} from './nutritionService'
import {family, member} from './test/nutritionTestData'
import {renderNutritionPage} from './test/renderNutritionPage'
import {Component as NutritionRecordPage} from './NutritionRecordPage'

vi.mock('../auth/authStore', () => ({
    useAuth: () => ({roleCodes: [], hasAnyButton: () => true, hasPermission: () => true}),
    canUseRbacButton: () => true,
}))
vi.mock('./nutritionService', () => ({
    listNutritionFamilies: vi.fn(),
    listNutritionMembers: vi.fn(),
    getNutritionDailyOverview: vi.fn(),
    getNutritionFamilyMonthlyReport: vi.fn(),
    adjustNutritionRecord: vi.fn(),
    createNutritionExtraFoodRecord: vi.fn(),
    getNutritionFamilyWeeklyReport: vi.fn(),
    generateNutritionFamilyWeeklyReport: vi.fn(),
    generateNutritionFamilyMonthlyReport: vi.fn(),
}))

const nutrients = {calories: '620', protein: '42', fat: '18', carbs: '68', sugar: '9', sodium: '760', fiber: '7', cholesterol: '88'}
const record = {
    id: 901,
    familyId: family.id,
    memberProfileId: member.id,
    mealPlanId: 81,
    sourceMealPlanItemId: 101,
    recordDate: '2026-07-14',
    mealType: 'DINNER' as const,
    sourceType: 'MEAL_PLAN',
    nutrients,
    riskTags: 'MEDIUM',
    createdAt: '2026-07-01T00:00:00Z',
    updatedAt: '2026-07-01T00:00:00Z',
}
const daily = {
    familyId: family.id,
    recordDate: '2026-07-14',
    totalNutrients: nutrients,
    targetNutrients: {...nutrients, calories: '2000'},
    remainingNutrients: {...nutrients, calories: '1380'},
    memberSummaries: [{memberProfileId: member.id, totalNutrients: nutrients, targetNutrients: {...nutrients, calories: '2000'}, remainingNutrients: {...nutrients, calories: '1380'}, records: [record]}],
}
const report = {
    snapshotId: 1001,
    periodType: 'WEEKLY',
    periodStart: '2026-07-13',
    periodEnd: '2026-07-19',
    totalNutrients: nutrients,
    riskCounts: {MEDIUM: 1},
    actualCost: '300',
    estimatedCost: '350',
    perPersonCost: '75',
    commonDishes: [{dishName: '番茄意面', count: 2}],
    nutrientReminders: ['HIGH_SODIUM'],
    trends: [{date: '2026-07-14', nutrients}],
}

describe('NutritionRecordPage', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        vi.mocked(listNutritionFamilies).mockResolvedValue([family])
        vi.mocked(listNutritionMembers).mockResolvedValue([member])
        vi.mocked(getNutritionDailyOverview).mockResolvedValue(daily)
        vi.mocked(getNutritionFamilyWeeklyReport).mockResolvedValue(report)
        vi.mocked(getNutritionFamilyMonthlyReport).mockResolvedValue({...report, periodType: 'MONTHLY'})
        vi.mocked(generateNutritionFamilyWeeklyReport).mockResolvedValue(report)
        vi.mocked(generateNutritionFamilyMonthlyReport).mockResolvedValue({...report, periodType: 'MONTHLY'})
        vi.mocked(adjustNutritionRecord).mockResolvedValue({...record, nutrients: {...nutrients, calories: '500'}})
        vi.mocked(createNutritionExtraFoodRecord).mockResolvedValue({...record, id: 902, sourceType: 'EXTRA_FOOD', mealType: 'SNACK'})
    })

    test('corrects a record and adds an extra food with complete nutrients', async () => {
        const user = userEvent.setup()
        renderNutritionPage(<NutritionRecordPage/>)
        await screen.findByText('620')

        await user.click(screen.getByRole('button', {name: '调整记录 901'}))
        const calories = screen.getByLabelText('调整热量')
        await user.clear(calories)
        await user.type(calories, '500')
        await user.type(screen.getByLabelText('调整原因'), '实际只吃半份')
        await user.click(screen.getByRole('button', {name: /保存调整/}))
        await waitFor(() => expect(adjustNutritionRecord).toHaveBeenCalled())
        const [adjustFamilyId, adjustedRecordId, adjustment] = vi.mocked(adjustNutritionRecord).mock.calls[0]
        expect(adjustFamilyId).toBe(family.id)
        expect(adjustedRecordId).toBe(record.id)
        expect(adjustment.reason).toBe('实际只吃半份')
        expect(adjustment.nutrients?.calories).toBe(500)

        await user.click(screen.getByRole('button', {name: /加餐登记/}))
        await user.type(screen.getByLabelText('食物名称'), '酸奶')
        await user.type(screen.getByLabelText('数量'), '200')
        await user.type(screen.getByLabelText('单位'), 'g')
        await user.click(screen.getByRole('button', {name: /保存加餐/}))
        await waitFor(() => expect(createNutritionExtraFoodRecord).toHaveBeenCalled())
        const [extraFamilyId, extraFood] = vi.mocked(createNutritionExtraFoodRecord).mock.calls[0]
        expect(extraFamilyId).toBe(family.id)
        expect(extraFood).toMatchObject({memberProfileId: member.id, foodName: '酸奶', amount: 200, unit: 'g'})
    })

    test('generates a weekly snapshot with reminders and trend points', async () => {
        const user = userEvent.setup()
        renderNutritionPage(<NutritionRecordPage/>)
        await screen.findByText('HIGH_SODIUM')
        expect(screen.getByText('2026-07-14')).toBeTruthy()

        await user.click(screen.getByRole('button', {name: /生成周报告/}))
        await waitFor(() => expect(generateNutritionFamilyWeeklyReport).toHaveBeenCalled())
        const [reportFamilyId, query] = vi.mocked(generateNutritionFamilyWeeklyReport).mock.calls[0]
        expect(reportFamilyId).toBe(family.id)
        expect(typeof query?.weekStart).toBe('string')
        expect(await screen.findByText('快照 #1001')).toBeTruthy()
    })
})
