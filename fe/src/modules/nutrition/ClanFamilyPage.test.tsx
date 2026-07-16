import {screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    deleteNutritionFamily,
    listNutritionClanFamilyRelations,
    listNutritionClans,
    listNutritionDataGrants,
    listNutritionFamilies,
    listNutritionRoleBindings,
    revokeNutritionDataGrant,
    revokeNutritionRoleBinding,
    updateNutritionFamilySettings,
} from './nutritionService'
import {family} from './test/nutritionTestData'
import {renderNutritionPage} from './test/renderNutritionPage'
import {Component as ClanFamilyPage} from './ClanFamilyPage'

vi.mock('../auth/authStore', () => ({
    useAuth: () => ({user: {id: 1}, roleCodes: [], hasAnyButton: () => true, hasPermission: () => true}),
    canUseRbacButton: () => true,
}))
vi.mock('./nutritionService', () => ({
    listNutritionFamilies: vi.fn(),
    listNutritionClans: vi.fn(),
    listNutritionClanFamilyRelations: vi.fn(),
    listNutritionRoleBindings: vi.fn(),
    listNutritionDataGrants: vi.fn(),
    updateNutritionFamilySettings: vi.fn(),
    revokeNutritionRoleBinding: vi.fn(),
    revokeNutritionDataGrant: vi.fn(),
    createNutritionClan: vi.fn(),
    createNutritionFamily: vi.fn(),
    associateNutritionClanFamily: vi.fn(),
    createNutritionRoleBinding: vi.fn(),
    createNutritionDataGrant: vi.fn(),
    deleteNutritionFamily: vi.fn(),
    removeNutritionClanFamilyRelation: vi.fn(),
}))

describe('ClanFamilyPage', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        vi.mocked(listNutritionFamilies).mockResolvedValue([family])
        vi.mocked(listNutritionClans).mockResolvedValue([{
            id: 91,
            name: '三代同堂',
            ownerUserId: 1,
            status: 'ACTIVE',
            createdAt: family.createdAt,
            updatedAt: family.updatedAt,
        }])
        vi.mocked(listNutritionClanFamilyRelations).mockResolvedValue([])
        vi.mocked(listNutritionRoleBindings).mockResolvedValue([{
            id: 101,
            subjectType: 'USER',
            subjectId: 2,
            roleCode: 'COOK',
            scopeType: 'FAMILY',
            scopeId: family.id,
            status: 'ACTIVE',
            createdAt: family.createdAt,
            updatedAt: family.updatedAt,
        }])
        vi.mocked(listNutritionDataGrants).mockResolvedValue([{
            id: 102,
            familyId: family.id,
            granteeType: 'USER',
            granteeId: 3,
            dataScope: 'HEALTH_PROFILE',
            permissionLevel: 'READ',
            status: 'ACTIVE',
            createdAt: family.createdAt,
            updatedAt: family.updatedAt,
        }])
        vi.mocked(updateNutritionFamilySettings).mockResolvedValue({...family, aiEnabled: true})
    })

    test('loads accessible families and saves family settings', async () => {
        const user = userEvent.setup()
        renderNutritionPage(<ClanFamilyPage/>)

        expect(screen.getByText('加载中')).toBeTruthy()
        await screen.findByText('Mario Family')
        await user.click(screen.getByRole('button', {name: '编辑设置'}))
        await user.click(screen.getByRole('switch', {name: '启用 AI'}))
        await user.click(screen.getByRole('button', {name: /保\s*存/}))

        expect(updateNutritionFamilySettings).toHaveBeenCalledWith(
            family.id,
            expect.objectContaining({aiEnabled: true}),
        )
    })

    test('revokes role and data grant entries', async () => {
        const user = userEvent.setup()
        vi.mocked(revokeNutritionRoleBinding).mockResolvedValue(undefined)
        vi.mocked(revokeNutritionDataGrant).mockResolvedValue(undefined)
        renderNutritionPage(<ClanFamilyPage/>)

        await screen.findByText('COOK')
        await user.click(screen.getByRole('button', {name: '撤销角色 101'}))
        await user.click(screen.getByRole('button', {name: '撤销授权 102'}))

        expect(revokeNutritionRoleBinding).toHaveBeenCalledWith(family.id, 101)
        expect(revokeNutritionDataGrant).toHaveBeenCalledWith(family.id, 102)
    })

    test('deletes the selected family after destructive confirmation', async () => {
        const user = userEvent.setup()
        vi.mocked(deleteNutritionFamily).mockResolvedValue(undefined)
        renderNutritionPage(<ClanFamilyPage/>)

        await screen.findAllByText('Mario Family')
        vi.mocked(listNutritionFamilies).mockResolvedValue([])
        await user.click(screen.getByRole('button', {name: /删除家庭/}))
        await user.click(screen.getByRole('button', {name: /确认删除/}))

        expect(deleteNutritionFamily).toHaveBeenCalledWith(family.id)
        expect(await screen.findByText('暂无营养数据')).toBeTruthy()
    })
})
