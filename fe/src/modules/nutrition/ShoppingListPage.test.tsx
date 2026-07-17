import {screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    closeNutritionMealPlanConfirmation,
    createNutritionPriceRecord,
    generateNutritionShoppingList,
    listNutritionFamilies,
    listNutritionMealPlans,
    listNutritionPriceRecords,
    listNutritionShoppingLists,
    previewNutritionShoppingList,
    transitionNutritionShoppingList,
    updateNutritionShoppingListItem,
} from './nutritionService'
import {family} from './test/nutritionTestData'
import {renderNutritionPage} from './test/renderNutritionPage'
import {Component as ShoppingListPage} from './ShoppingListPage'

vi.mock('../auth/authStore', () => ({
    useAuth: () => ({roleCodes: [], hasAnyButton: () => true, hasPermission: () => true}),
    canUseRbacButton: () => true,
}))
vi.mock('./nutritionService', () => ({
    listNutritionFamilies: vi.fn(),
    listNutritionPriceRecords: vi.fn(),
    listNutritionMealPlans: vi.fn(),
    previewNutritionShoppingList: vi.fn(),
    listNutritionShoppingLists: vi.fn(),
    closeNutritionMealPlanConfirmation: vi.fn(),
    generateNutritionShoppingList: vi.fn(),
    updateNutritionShoppingListItem: vi.fn(),
    createNutritionPriceRecord: vi.fn(),
    transitionNutritionShoppingList: vi.fn(),
}))

const confirmingPlan = {
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
const shoppingList = {
    id: 601,
    familyId: family.id,
    mealPlanId: confirmingPlan.id,
    listDate: '2026-07-14',
    status: 'ACTIVE' as const,
    title: 'Tuesday shopping',
    estimatedTotalPrice: '50',
    actualTotalPrice: '42',
    items: [{
        id: 611,
        shoppingListId: 601,
        standardFoodId: 41,
        rawFoodName: '番茄',
        category: 'VEGETABLE',
        plannedAmount: '500',
        plannedUnit: 'g',
        itemStatus: 'PENDING',
        createdAt: '2026-07-01T00:00:00Z',
        updatedAt: '2026-07-01T00:00:00Z',
    }],
    createdAt: '2026-07-01T00:00:00Z',
    updatedAt: '2026-07-01T00:00:00Z',
}

describe('ShoppingListPage', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        vi.mocked(listNutritionFamilies).mockResolvedValue([family])
        vi.mocked(listNutritionPriceRecords).mockResolvedValue([])
        vi.mocked(listNutritionMealPlans).mockResolvedValue([confirmingPlan])
        vi.mocked(previewNutritionShoppingList).mockResolvedValue({...shoppingList, id: 0, status: 'DRAFT'})
        vi.mocked(listNutritionShoppingLists).mockResolvedValue([shoppingList])
        vi.mocked(closeNutritionMealPlanConfirmation).mockResolvedValue({...confirmingPlan, status: 'CONFIRM_CLOSED'})
        vi.mocked(generateNutritionShoppingList).mockResolvedValue(shoppingList)
        vi.mocked(updateNutritionShoppingListItem).mockResolvedValue({...shoppingList.items[0], itemStatus: 'CHECKED'})
        vi.mocked(transitionNutritionShoppingList).mockResolvedValue({...shoppingList, status: 'PURCHASING'})
        vi.mocked(createNutritionPriceRecord).mockResolvedValue({
            id: 701,
            familyId: family.id,
            shoppingListItemId: shoppingList.items[0].id,
            standardFoodId: 41,
            rawFoodName: '番茄',
            totalPrice: '12.5',
            normalizedUnitPrice: '25',
            createdAt: shoppingList.createdAt,
            updatedAt: shoppingList.updatedAt,
        })
    })

    test('shows preview before close and generates the persisted final list', async () => {
        const user = userEvent.setup()
        renderNutritionPage(<ShoppingListPage/>)

        await screen.findByText('预览清单')
        expect(screen.getByText('预估成本')).toBeTruthy()
        await user.click(screen.getByRole('button', {name: /关闭确认并生成正式清单/}))

        await waitFor(() => expect(closeNutritionMealPlanConfirmation).toHaveBeenCalledWith(family.id, confirmingPlan.id, true))
        expect(generateNutritionShoppingList).toHaveBeenCalledWith(family.id, confirmingPlan.id)
    })

    test('updates item state, records a price, and advances shopping state', async () => {
        const user = userEvent.setup()
        vi.mocked(listNutritionMealPlans).mockResolvedValue([{...confirmingPlan, status: 'CONFIRM_CLOSED'}])
        renderNutritionPage(<ShoppingListPage/>)
        await screen.findByText('正式清单')

        await user.click(screen.getByRole('checkbox', {name: '勾选番茄'}))
        await waitFor(() => expect(updateNutritionShoppingListItem).toHaveBeenCalledWith(
            family.id,
            shoppingList.id,
            shoppingList.items[0].id,
            {checked: true, itemStatus: 'CHECKED'},
        ))

        await user.click(screen.getByRole('button', {name: '记录番茄价格'}))
        await user.type(screen.getByLabelText('采购渠道'), '菜市场')
        await user.type(screen.getByLabelText('总价'), '12.5')
        await user.click(screen.getByRole('button', {name: /保存价格/}))
        await waitFor(() => expect(createNutritionPriceRecord).toHaveBeenCalledWith(
            family.id,
            expect.objectContaining({shoppingListItemId: 611, totalPrice: 12.5}),
        ))

        await user.click(screen.getByRole('button', {name: /开始采购/}))
        expect(transitionNutritionShoppingList).toHaveBeenCalledWith(
            family.id,
            shoppingList.id,
            {targetStatus: 'PURCHASING'},
        )
    })
})
