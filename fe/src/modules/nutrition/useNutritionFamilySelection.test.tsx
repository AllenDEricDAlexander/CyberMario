import {act, renderHook, waitFor} from '@testing-library/react'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {clearCurrentNutritionFamilyId, setCurrentNutritionFamilyId} from './currentFamilyStore'
import {listNutritionFamilies} from './nutritionService'
import type {NutritionFamilyResponse} from './nutritionTypes'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

vi.mock('./nutritionService', () => ({listNutritionFamilies: vi.fn()}))

const families: NutritionFamilyResponse[] = [
    {
        id: 7,
        name: 'Mario Family',
        ownerUserId: 1,
        region: 'Shanghai',
        currency: 'CNY',
        defaultMealTypes: ['DINNER'],
        aiEnabled: true,
        healthAlertEnabled: true,
        budgetEnabled: true,
        status: 'ACTIVE',
        createdAt: '2026-07-01T00:00:00Z',
        updatedAt: '2026-07-01T00:00:00Z',
    },
    {
        id: 8,
        name: 'Peach Family',
        ownerUserId: 2,
        defaultMealTypes: ['LUNCH'],
        aiEnabled: false,
        healthAlertEnabled: true,
        budgetEnabled: true,
        status: 'ACTIVE',
        createdAt: '2026-07-01T00:00:00Z',
        updatedAt: '2026-07-01T00:00:00Z',
    },
]

describe('useNutritionFamilySelection', () => {
    beforeEach(() => {
        clearCurrentNutritionFamilyId()
        vi.clearAllMocks()
    })

    test('keeps an accessible selection and falls back when it disappears', async () => {
        setCurrentNutritionFamilyId(8)
        vi.mocked(listNutritionFamilies).mockResolvedValueOnce(families)
        const {result} = renderHook(() => useNutritionFamilySelection())

        await waitFor(() => expect(result.current.state).toBe('ready'))
        expect(result.current.currentFamilyId).toBe(8)
        expect(result.current.currentFamily?.name).toBe('Peach Family')

        vi.mocked(listNutritionFamilies).mockResolvedValueOnce([families[0]])
        await act(() => result.current.reload())

        expect(result.current.currentFamilyId).toBe(7)
        expect(result.current.currentFamily?.name).toBe('Mario Family')
    })

    test('maps forbidden failures and empty family lists to distinct states', async () => {
        vi.mocked(listNutritionFamilies).mockRejectedValueOnce({status: 403, message: 'forbidden'})
        const forbidden = renderHook(() => useNutritionFamilySelection())
        await waitFor(() => expect(forbidden.result.current.state).toBe('forbidden'))

        forbidden.unmount()
        vi.mocked(listNutritionFamilies).mockResolvedValueOnce([])
        const empty = renderHook(() => useNutritionFamilySelection())
        await waitFor(() => expect(empty.result.current.state).toBe('empty'))
        expect(empty.result.current.currentFamilyId).toBeNull()
    })
})
