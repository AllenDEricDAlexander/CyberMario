import {describe, expect, test} from 'vitest'
import type {NutritionMealPlanResponse} from './nutritionTypes'
import {selectNearestNutritionMealPlan} from './mealPlanSelection'

const plan = {
    id: 81,
    familyId: 9,
    planDate: '2026-07-18',
    status: 'PUBLISHED',
    version: 1,
    title: 'Tomorrow dinner',
    confirmedMemberCount: 0,
    risks: [],
    publishable: true,
    items: [],
    createdAt: '2026-07-17T00:00:00Z',
    updatedAt: '2026-07-17T00:00:00Z',
} satisfies NutritionMealPlanResponse

describe('selectNearestNutritionMealPlan', () => {
    test('selects the nearest actionable plan instead of a draft for today', () => {
        const selected = selectNearestNutritionMealPlan([
            {...plan, id: 82, planDate: '2026-07-17', status: 'PENDING_REVIEW'},
            {...plan, id: 83, planDate: '2026-07-15'},
            plan,
        ], ['PUBLISHED', 'CONFIRMING'], '2026-07-17')

        expect(selected?.id).toBe(plan.id)
    })

    test('prefers a future plan when dates are equally close', () => {
        const selected = selectNearestNutritionMealPlan([
            {...plan, id: 84, planDate: '2026-07-16'},
            plan,
        ], ['PUBLISHED'], '2026-07-17')

        expect(selected?.id).toBe(plan.id)
    })
})
