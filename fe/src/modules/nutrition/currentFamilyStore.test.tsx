import {describe, expect, test, vi} from 'vitest'
import {
    clearCurrentNutritionFamilyId,
    getCurrentNutritionFamilyId,
    setCurrentNutritionFamilyId,
    subscribeCurrentNutritionFamily,
} from './currentFamilyStore'

describe('currentFamilyStore', () => {
    test('stores the selected family id and notifies subscribers', () => {
        clearCurrentNutritionFamilyId()
        const listener = vi.fn()
        const unsubscribe = subscribeCurrentNutritionFamily(listener)

        setCurrentNutritionFamilyId(7)

        expect(getCurrentNutritionFamilyId()).toBe(7)
        expect(listener).toHaveBeenCalledTimes(1)

        unsubscribe()
        clearCurrentNutritionFamilyId()

        expect(getCurrentNutritionFamilyId()).toBeNull()
        expect(listener).toHaveBeenCalledTimes(1)
    })
})
