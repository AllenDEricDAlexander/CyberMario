import {screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    assignNutritionProfileGuardian,
    bindNutritionMemberUser,
    listNutritionFamilies,
    listNutritionFamilyHealthTags,
    listNutritionHealthProfiles,
    listNutritionMembers,
    updateNutritionHealthProfile,
} from './nutritionService'
import {family, healthProfile, healthTag, member} from './test/nutritionTestData'
import {renderNutritionPage} from './test/renderNutritionPage'
import {Component as MemberHealthPage} from './MemberHealthPage'

vi.mock('../auth/authStore', () => ({
    useAuth: () => ({roleCodes: [], hasAnyButton: () => true, hasPermission: () => true}),
    canUseRbacButton: () => true,
}))
vi.mock('./nutritionService', () => ({
    listNutritionFamilies: vi.fn(),
    listNutritionMembers: vi.fn(),
    listNutritionHealthProfiles: vi.fn(),
    listNutritionFamilyHealthTags: vi.fn(),
    updateNutritionHealthProfile: vi.fn(),
    createNutritionMemberProfile: vi.fn(),
    updateNutritionMemberProfile: vi.fn(),
    deactivateNutritionMemberProfile: vi.fn(),
    bindNutritionMemberUser: vi.fn(),
    unbindNutritionMemberUser: vi.fn(),
    assignNutritionProfileGuardian: vi.fn(),
    revokeNutritionProfileGuardian: vi.fn(),
}))

describe('MemberHealthPage', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        vi.mocked(listNutritionFamilies).mockResolvedValue([family])
        vi.mocked(listNutritionMembers).mockResolvedValue([member])
        vi.mocked(listNutritionHealthProfiles).mockResolvedValue([healthProfile])
        vi.mocked(listNutritionFamilyHealthTags).mockResolvedValue([healthTag])
        vi.mocked(updateNutritionHealthProfile).mockResolvedValue(healthProfile)
    })

    test('updates a member health profile with live tag dictionaries', async () => {
        const user = userEvent.setup()
        renderNutritionPage(<MemberHealthPage/>)

        await screen.findAllByText('Mario')
        await user.click(screen.getByRole('button', {name: '健康档案 Mario'}))
        const calories = screen.getByLabelText('目标热量')
        await user.clear(calories)
        await user.type(calories, '2100')
        await user.click(screen.getByRole('button', {name: /保\s*存健康档案/}))

        expect(updateNutritionHealthProfile).toHaveBeenCalledWith(
            family.id,
            member.id,
            expect.objectContaining({targetCalories: 2100}),
        )
    })

    test('binds a login user and assigns a guardian', async () => {
        const user = userEvent.setup()
        const regularMember = {
            ...member,
            id: 12,
            boundUserId: null,
            boundUsername: null,
            ownerProfile: false,
            nickname: 'Luigi',
            loginEnabled: false,
        }
        vi.mocked(listNutritionMembers).mockResolvedValue([regularMember])
        vi.mocked(bindNutritionMemberUser).mockResolvedValue(regularMember)
        vi.mocked(assignNutritionProfileGuardian).mockResolvedValue({
            id: 120,
            subjectType: 'USER',
            subjectId: 9,
            roleCode: 'PROFILE_GUARDIAN',
            scopeType: 'MEMBER_PROFILE',
            scopeId: regularMember.id,
            status: 'ACTIVE',
            createdAt: family.createdAt,
            updatedAt: family.updatedAt,
        })
        renderNutritionPage(<MemberHealthPage/>)
        await screen.findAllByText('Luigi')

        await user.click(screen.getByRole('button', {name: '绑定用户 Luigi'}))
        await user.type(screen.getByLabelText('用户 ID'), '8')
        await user.click(screen.getByRole('button', {name: /确\s*认绑定/}))
        expect(bindNutritionMemberUser).toHaveBeenCalledWith(family.id, regularMember.id, {userId: 8})

        await user.click(screen.getByRole('button', {name: '添加监护人 Luigi'}))
        await user.type(screen.getByLabelText('监护用户 ID'), '9')
        await user.click(screen.getByRole('button', {name: /确\s*认添加/}))
        expect(assignNutritionProfileGuardian).toHaveBeenCalledWith(family.id, regularMember.id, {userId: 9})
    })

    test('shows the family owner username without account binding actions', async () => {
        renderNutritionPage(<MemberHealthPage/>)

        await screen.findByText('家庭所有者')

        expect(screen.getByText('mario')).toBeTruthy()
        expect(screen.queryByRole('button', {name: '绑定用户 Mario'})).toBeNull()
        expect(screen.queryByRole('button', {name: '解绑'})).toBeNull()
        expect(screen.queryByRole('button', {name: '停用'})).toBeNull()
    })

    test('shows backend tag validation failures without closing the health editor', async () => {
        const user = userEvent.setup()
        vi.mocked(updateNutritionHealthProfile).mockRejectedValue(new Error('标签编码不存在'))
        renderNutritionPage(<MemberHealthPage/>)
        await screen.findAllByText('Mario')

        await user.click(screen.getByRole('button', {name: '健康档案 Mario'}))
        await user.click(screen.getByRole('button', {name: /保\s*存健康档案/}))

        expect(await screen.findByText('标签编码不存在')).toBeTruthy()
        expect(screen.getByLabelText('目标热量')).toBeTruthy()
    })
})
