import {screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    confirmNutritionImportJob,
    createNutritionImportJob,
    listNutritionPlatformHealthTags,
    listNutritionPlatformRecipes,
    listNutritionStandardFoods,
} from './nutritionService'
import {recipe, standardFood, healthTag} from './test/nutritionTestData'
import {renderNutritionPage} from './test/renderNutritionPage'
import {Component as PlatformNutritionConfigPage} from './PlatformNutritionConfigPage'

const authAccess = vi.hoisted(() => ({canMutate: true}))
vi.mock('../auth/authStore', () => ({
    useAuth: () => ({
        roleCodes: [],
        hasAnyButton: () => authAccess.canMutate,
        hasPermission: () => authAccess.canMutate,
    }),
    canUseRbacButton: () => authAccess.canMutate,
}))
vi.mock('./nutritionService', () => ({
    listNutritionStandardFoods: vi.fn(),
    listNutritionPlatformHealthTags: vi.fn(),
    listNutritionPlatformRecipes: vi.fn(),
    createNutritionStandardFood: vi.fn(),
    updateNutritionStandardFood: vi.fn(),
    deactivateNutritionStandardFood: vi.fn(),
    createNutritionPlatformHealthTag: vi.fn(),
    updateNutritionPlatformHealthTag: vi.fn(),
    deactivateNutritionPlatformHealthTag: vi.fn(),
    createNutritionPlatformRecipe: vi.fn(),
    updateNutritionPlatformRecipe: vi.fn(),
    deactivateNutritionPlatformRecipe: vi.fn(),
    createNutritionImportJob: vi.fn(),
    confirmNutritionImportJob: vi.fn(),
}))

const previewJob = {
    id: 201,
    familyId: null,
    importType: 'STANDARD_FOOD' as const,
    fileName: 'foods.csv',
    status: 'PREVIEW_READY' as const,
    totalRows: 1,
    successRows: 1,
    failedRows: 0,
    warningRows: 0,
    errors: [],
    createdAt: '2026-07-01T00:00:00Z',
}

describe('PlatformNutritionConfigPage', () => {
    beforeEach(() => {
        authAccess.canMutate = true
        vi.clearAllMocks()
        vi.mocked(listNutritionStandardFoods).mockResolvedValue([standardFood])
        vi.mocked(listNutritionPlatformHealthTags).mockResolvedValue([healthTag])
        vi.mocked(listNutritionPlatformRecipes).mockResolvedValue([{...recipe, familyId: null, sourceType: 'PLATFORM_PUBLIC'}])
    })

    test('creates an import preview and confirms it', async () => {
        const user = userEvent.setup()
        vi.mocked(createNutritionImportJob).mockResolvedValue(previewJob)
        vi.mocked(confirmNutritionImportJob).mockResolvedValue({...previewJob, status: 'COMPLETED'})
        renderNutritionPage(<PlatformNutritionConfigPage/>)

        await screen.findAllByText('鸡胸肉')
        await user.click(screen.getByRole('button', {name: /创建导入任务/}))
        await user.click(screen.getByLabelText('导入类型'))
        await user.click(screen.getByText('标准食材', {selector: '.ant-select-item-option-content'}))
        await user.type(screen.getByLabelText('文件名'), 'foods.csv')
        await user.type(screen.getByLabelText('CSV 内容'), 'nameCn,category\n鸡胸肉,MEAT')
        await user.click(screen.getByRole('button', {name: '生成预览'}))

        expect(createNutritionImportJob).toHaveBeenCalledWith({
            importType: 'STANDARD_FOOD',
            fileName: 'foods.csv',
            csvContent: 'nameCn,category\n鸡胸肉,MEAT',
        })
        await user.click(await screen.findByRole('button', {name: '确认导入'}))
        expect(confirmNutritionImportJob).toHaveBeenCalledWith(previewJob.id)
    })

    test('disables mutation actions without coarse RBAC capability', async () => {
        authAccess.canMutate = false
        renderNutritionPage(<PlatformNutritionConfigPage/>)

        await screen.findAllByText('鸡胸肉')
        expect(screen.getByRole('button', {name: /新增标准食材/}).hasAttribute('disabled')).toBe(true)
        expect(screen.getByRole('button', {name: /创建导入任务/}).hasAttribute('disabled')).toBe(true)
    })
})
