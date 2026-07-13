import {screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, test, vi} from 'vitest'
import {family} from '../test/nutritionTestData'
import {renderNutritionPage} from '../test/renderNutritionPage'
import {CurrentFamilySelect} from './CurrentFamilySelect'

describe('CurrentFamilySelect', () => {
    test('selects an accessible family', async () => {
        const onChange = vi.fn()
        renderNutritionPage(<CurrentFamilySelect families={[family]} value={null} onChange={onChange}/>)

        await userEvent.click(screen.getByLabelText('当前营养家庭'))
        await userEvent.click(screen.getByText('Mario Family'))

        expect(onChange).toHaveBeenCalledWith(7)
    })
})
