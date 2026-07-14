import {screen, waitFor, within} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    createNutritionFamilyRecipe,
    listNutritionFamilies,
    listNutritionFamilyRecipes,
    listNutritionFamilyStandardFoods,
    updateNutritionRecipeIngredientMapping,
    validateNutritionRecipe,
} from './nutritionService'
import {family, recipe, standardFood} from './test/nutritionTestData'
import {renderNutritionPage} from './test/renderNutritionPage'
import {Component as RecipeLibraryPage} from './RecipeLibraryPage'

vi.mock('../auth/authStore', () => ({
    useAuth: () => ({roleCodes: [], hasAnyButton: () => true, hasPermission: () => true}),
    canUseRbacButton: () => true,
}))
vi.mock('./nutritionService', () => ({
    listNutritionFamilies: vi.fn(),
    listNutritionFamilyRecipes: vi.fn(),
    listNutritionFamilyStandardFoods: vi.fn(),
    createNutritionFamilyRecipe: vi.fn(),
    updateNutritionFamilyRecipe: vi.fn(),
    deactivateNutritionFamilyRecipe: vi.fn(),
    updateNutritionRecipeIngredientMapping: vi.fn(),
    validateNutritionRecipe: vi.fn(),
}))

describe('RecipeLibraryPage', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        vi.mocked(listNutritionFamilies).mockResolvedValue([family])
        vi.mocked(listNutritionFamilyRecipes).mockResolvedValue([recipe])
        vi.mocked(listNutritionFamilyStandardFoods).mockResolvedValue([standardFood])
        vi.mocked(createNutritionFamilyRecipe).mockResolvedValue(recipe)
    })

    test('creates a recipe with ingredients through the live editor', async () => {
        const user = userEvent.setup()
        renderNutritionPage(<RecipeLibraryPage/>)

        await screen.findAllByText('鸡胸肉沙拉')
        expect(screen.getByText('存在未映射食材')).toBeTruthy()
        await user.click(screen.getByRole('button', {name: /新建菜谱/}))
        await user.type(screen.getByLabelText('菜谱名称'), '番茄意面')
        await user.type(screen.getByLabelText('食材名称 1'), '番茄')
        await user.type(screen.getByLabelText('数量 1'), '200')
        await user.click(screen.getByRole('button', {name: /保\s*存菜谱/}))

        await waitFor(() => {
            expect(createNutritionFamilyRecipe).toHaveBeenCalledWith(
                family.id,
                expect.objectContaining({
                    name: '番茄意面',
                    ingredients: [expect.objectContaining({foodName: '番茄', amount: 200, unit: 'g'})],
                }),
            )
        })
    })

    test('maps an ingredient and shows backend validation warnings', async () => {
        const user = userEvent.setup()
        vi.mocked(updateNutritionRecipeIngredientMapping).mockResolvedValue({
            ...recipe.ingredients[0],
            standardFoodId: standardFood.id,
            mappingStatus: 'MAPPED',
        })
        vi.mocked(validateNutritionRecipe).mockResolvedValue({
            publishable: false,
            errors: [],
            warnings: ['营养信息需要复核'],
        })
        renderNutritionPage(<RecipeLibraryPage/>)
        await screen.findAllByText('鸡胸肉沙拉')

        await user.click(screen.getByRole('button', {name: '映射 鸡胸肉'}))
        const mappingDialog = screen.getByRole('dialog')
        await user.click(within(mappingDialog).getByLabelText('标准食材'))
        await user.click(screen.getByText('鸡胸肉', {selector: '.ant-select-item-option-content'}))
        expect(mappingDialog.querySelector('.ant-select-content')?.getAttribute('title')).toBe('鸡胸肉')
        await user.click(within(mappingDialog).getByRole('button', {name: /保\s*存映射/}))
        await waitFor(() => {
            expect(updateNutritionRecipeIngredientMapping).toHaveBeenCalledWith(
                family.id,
                recipe.id,
                recipe.ingredients[0].id,
                {standardFoodId: standardFood.id, gramsPerUnit: undefined},
            )
        })

        await user.click(screen.getByRole('button', {name: '校验 鸡胸肉沙拉'}))
        expect(await screen.findByText('营养信息需要复核')).toBeTruthy()
    })
})
