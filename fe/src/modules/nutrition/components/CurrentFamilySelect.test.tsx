import {isValidElement, type ReactElement} from 'react'
import {describe, expect, test, vi} from 'vitest'
import {CurrentFamilySelect} from './CurrentFamilySelect'
import type {NutritionFamilyResponse} from '../nutritionTypes'

type CurrentFamilySelectElementProps = {
    onChange: (familyId: number) => void
    options: Array<{ label: string; value: number }>
}

const families: NutritionFamilyResponse[] = [
    {
        id: 7,
        name: 'Mario 家',
        ownerUserId: 1,
        region: 'Shanghai',
        currency: 'CNY',
        defaultMealTypes: ['BREAKFAST', 'DINNER'],
        aiEnabled: true,
        aiGenerateTime: '07:30:00',
        healthAlertEnabled: true,
        budgetEnabled: true,
        status: 'ACTIVE',
        ownerMemberProfileId: 11,
        createdAt: '2026-07-01T00:00:00Z',
        updatedAt: '2026-07-01T00:00:00Z',
    },
]

describe('CurrentFamilySelect', () => {
    test('CurrentFamilySelect calls onChange with the selected family id', () => {
        const onChange = vi.fn()
        const element = CurrentFamilySelect({families, value: null, onChange})

        expect(isValidElement(element)).toBe(true)
        if (!isValidElement(element)) {
            throw new Error('expected CurrentFamilySelect to return a React element')
        }
        const selectElement = element as ReactElement<CurrentFamilySelectElementProps>

        selectElement.props.onChange(7)

        expect(onChange).toHaveBeenCalledWith(7)
        expect(selectElement.props.options).toEqual([{label: 'Mario 家', value: 7}])
    })
})
