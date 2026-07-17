import {screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {afterEach, beforeEach, describe, expect, test, vi} from 'vitest'
import {
    acknowledgeNutritionMealRisks,
    createNutritionTodayMealPlan,
    generateNutritionAiRecommendation,
    getNutritionAiRecommendationJob,
    listNutritionAiRecommendationJobs,
    listNutritionAiRecommendations,
    listNutritionFamilies,
    listNutritionMealPlanRecipeCandidates,
    listNutritionMealPlans,
    publishNutritionMealPlan,
    regenerateNutritionMealPlan,
    updateNutritionMealPlan,
} from './nutritionService'
import {family, recipe} from './test/nutritionTestData'
import {renderNutritionPage} from './test/renderNutritionPage'
import {Component as AiMenuPage} from './AiMenuPage'

vi.mock('../auth/authStore', () => ({
    useAuth: () => ({roleCodes: [], hasAnyButton: () => true, hasPermission: () => true}),
    canUseRbacButton: () => true,
}))
vi.mock('./nutritionService', () => ({
    listNutritionFamilies: vi.fn(),
    listNutritionMealPlans: vi.fn(),
    listNutritionAiRecommendations: vi.fn(),
    listNutritionMealPlanRecipeCandidates: vi.fn(),
    listNutritionAiRecommendationJobs: vi.fn(),
    createNutritionTodayMealPlan: vi.fn(),
    generateNutritionAiRecommendation: vi.fn(),
    getNutritionAiRecommendationJob: vi.fn(),
    updateNutritionMealPlan: vi.fn(),
    acknowledgeNutritionMealRisks: vi.fn(),
    publishNutritionMealPlan: vi.fn(),
    regenerateNutritionMealPlan: vi.fn(),
}))

const mediumRisk = {
    id: 301,
    memberProfileId: 11,
    ruleCode: 'SODIUM_LIMIT',
    riskLevel: 'MEDIUM' as const,
    riskMessage: '控钠目标接近上限',
    blocking: false,
    requiresConfirmation: true,
    acknowledged: false,
}
const pendingPlan = {
    id: 81,
    familyId: family.id,
    aiRecommendationId: 91,
    planDate: '2026-07-14',
    status: 'PENDING_REVIEW' as const,
    version: 3,
    title: 'AI Tuesday dinner',
    confirmedMemberCount: 0,
    estimatedCost: '50',
    risks: [mediumRisk],
    publishable: true,
    items: [{
        id: 101,
        mealPlanId: 81,
        mealType: 'DINNER' as const,
        recipeId: 52,
        dishName: '番茄意面',
        servingCount: '2',
        sortOrder: 1,
        version: 1,
    }],
    createdAt: '2026-07-01T00:00:00Z',
    updatedAt: '2026-07-01T00:00:00Z',
}
const recommendation = {
    id: 91,
    familyId: family.id,
    aiJobId: 92,
    recommendationDate: '2026-07-14',
    title: '均衡晚餐建议',
    reason: '兼顾控钠与蛋白质',
    mealTypes: ['DINNER' as const],
    status: 'ACTIVE' as const,
    mealPlanId: pendingPlan.id,
    createdAt: '2026-07-01T00:00:00Z',
    updatedAt: '2026-07-01T00:00:00Z',
}

describe('AiMenuPage', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        vi.mocked(listNutritionFamilies).mockResolvedValue([family])
        vi.mocked(listNutritionMealPlans).mockResolvedValue([pendingPlan])
        vi.mocked(listNutritionAiRecommendations).mockResolvedValue([recommendation])
        vi.mocked(listNutritionMealPlanRecipeCandidates).mockResolvedValue([recipe])
        vi.mocked(listNutritionAiRecommendationJobs).mockResolvedValue([])
        vi.mocked(createNutritionTodayMealPlan).mockResolvedValue({
            ...pendingPlan,
            id: 82,
            planDate: new Date().toLocaleDateString('en-CA'),
            status: 'ADJUSTED',
            title: '今日菜单',
        })
        vi.mocked(updateNutritionMealPlan).mockResolvedValue({...pendingPlan, version: 4})
        vi.mocked(acknowledgeNutritionMealRisks).mockResolvedValue({
            ...pendingPlan,
            version: 4,
            risks: [{...mediumRisk, acknowledged: true}],
        })
        vi.mocked(publishNutritionMealPlan).mockResolvedValue({...pendingPlan, status: 'PUBLISHED'})
    })

    afterEach(() => vi.useRealTimers())

    test('cook edits a generated dish, acknowledges medium risk, and publishes', async () => {
        const user = userEvent.setup()
        renderNutritionPage(<AiMenuPage/>)

        await screen.findByText(pendingPlan.title)
        await user.click(screen.getByRole('button', {name: '保存调整'}))
        await user.click(screen.getByRole('checkbox', {name: /确认中风险/}))
        await user.type(screen.getByLabelText('风险确认说明'), '已核对控钠目标')
        await user.click(screen.getByRole('button', {name: /发布菜单/}))

        await waitFor(() => expect(updateNutritionMealPlan).toHaveBeenCalled())
        expect(acknowledgeNutritionMealRisks).toHaveBeenCalledWith(
            family.id,
            pendingPlan.id,
            {riskIds: [mediumRisk.id], note: '已核对控钠目标'},
        )
        expect(publishNutritionMealPlan).toHaveBeenCalledWith(family.id, pendingPlan.id)
    })

    test('cook creates a menu for today using validated recipes', async () => {
        const user = userEvent.setup()
        renderNutritionPage(<AiMenuPage/>)
        await screen.findByText(pendingPlan.title)

        await user.click(screen.getByRole('button', {name: /人工创建今日菜单/}))
        await user.clear(screen.getByLabelText('菜单标题'))
        await user.type(screen.getByLabelText('菜单标题'), '手工今日菜单')
        await user.click(screen.getByRole('button', {name: '创建今日菜单'}))

        await waitFor(() => expect(createNutritionTodayMealPlan).toHaveBeenCalledWith(
            family.id,
            expect.objectContaining({
                title: '手工今日菜单',
                items: [expect.objectContaining({recipeId: recipe.id, mealType: 'DINNER', sortOrder: 0})],
            }),
        ))
    })

    test('restores the latest failed AI job and shows its error', async () => {
        vi.mocked(listNutritionAiRecommendationJobs).mockResolvedValue([{
            id: 401,
            familyId: family.id,
            triggerType: 'MANUAL',
            status: 'FAILED',
            plannedDate: '2026-07-15',
            targetMealTypes: ['DINNER'],
            errorMessage: '菜谱单位换算缺失',
            createdAt: pendingPlan.createdAt,
            updatedAt: pendingPlan.updatedAt,
        }])

        renderNutritionPage(<AiMenuPage/>)

        expect(await screen.findAllByText('菜谱单位换算缺失')).toHaveLength(2)
        expect(screen.getByText('FAILED')).toBeTruthy()
    })

    test('polls a pending AI job to completion with a bounded timer', async () => {
        const user = userEvent.setup()
        const pendingJob = {
            id: 401,
            familyId: family.id,
            triggerType: 'MANUAL' as const,
            status: 'PENDING' as const,
            plannedDate: '2026-07-15',
            targetMealTypes: ['DINNER' as const],
            createdAt: '2026-07-01T00:00:00Z',
            updatedAt: '2026-07-01T00:00:00Z',
        }
        vi.mocked(generateNutritionAiRecommendation).mockResolvedValue(pendingJob)
        const succeededJob = {...pendingJob, status: 'SUCCEEDED' as const, mealPlanId: 81}
        vi.mocked(getNutritionAiRecommendationJob).mockResolvedValue(succeededJob)
        vi.mocked(listNutritionAiRecommendationJobs)
            .mockResolvedValueOnce([])
            .mockResolvedValue([succeededJob])
        renderNutritionPage(<AiMenuPage/>)
        await screen.findByText(pendingPlan.title)

        await user.click(screen.getByRole('button', {name: /生成 AI 建议/}))
        expect(screen.getByText('PENDING')).toBeTruthy()
        await waitFor(
            () => expect(getNutritionAiRecommendationJob).toHaveBeenCalledWith(family.id, pendingJob.id),
            {timeout: 2500},
        )
        expect(await screen.findByText('SUCCEEDED')).toBeTruthy()
    })

    test('blocks high-risk publishing and supports regeneration', async () => {
        const user = userEvent.setup()
        const blockedPlan = {
            ...pendingPlan,
            publishable: false,
            risks: [{...mediumRisk, id: 302, riskLevel: 'HIGH' as const, blocking: true, riskMessage: '花生过敏'}],
        }
        vi.mocked(listNutritionMealPlans).mockResolvedValue([blockedPlan])
        vi.mocked(regenerateNutritionMealPlan).mockResolvedValue({
            id: 402,
            familyId: family.id,
            triggerType: 'REGENERATE',
            status: 'PENDING',
            plannedDate: blockedPlan.planDate,
            targetMealTypes: ['DINNER'],
            createdAt: blockedPlan.createdAt,
            updatedAt: blockedPlan.updatedAt,
        })
        renderNutritionPage(<AiMenuPage/>)
        await screen.findByText('花生过敏')

        expect(screen.getByRole('button', {name: /发布菜单/}).hasAttribute('disabled')).toBe(true)
        await user.click(screen.getByRole('button', {name: /重新生成/}))
        expect(regenerateNutritionMealPlan).toHaveBeenCalledWith(family.id, blockedPlan.id)
    })

    test('prompts an authoritative reload after a stale-version conflict', async () => {
        const user = userEvent.setup()
        vi.mocked(updateNutritionMealPlan).mockRejectedValue({
            code: 'NUTRITION_MEAL_VERSION_CONFLICT',
            message: 'meal plan version conflict',
        })
        renderNutritionPage(<AiMenuPage/>)
        await screen.findByText(pendingPlan.title)

        await user.click(screen.getByRole('button', {name: '保存调整'}))

        expect(await screen.findByText('菜单版本已变化，请重新加载后再编辑')).toBeTruthy()
        await user.click(screen.getByRole('button', {name: '重新加载'}))
        expect(listNutritionMealPlans).toHaveBeenCalledTimes(2)
    })
})
