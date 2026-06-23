import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    changeCurrentUserPassword,
    getCurrentUserSoulMd,
    getCurrentUserSoulMdVersions,
    updateCurrentUserProfile,
    updateCurrentUserSoulMd,
} from './accountService'

vi.mock('../../services/request', () => ({
    requestJson: vi.fn(),
}))

describe('accountService', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    test('builds current user profile and password requests', async () => {
        const {requestJson} = await import('../../services/request')

        void updateCurrentUserProfile({nickname: 'Mario'})
        void changeCurrentUserPassword({
            currentPassword: 'old-password',
            newPassword: 'new-password',
            confirmPassword: 'new-password',
        })

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/me/profile', {
            method: 'PUT',
            body: {nickname: 'Mario'},
        })
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/me/password', {
            method: 'PUT',
            body: {
                currentPassword: 'old-password',
                newPassword: 'new-password',
                confirmPassword: 'new-password',
            },
        })
    })

    test('builds SoulMD requests', async () => {
        const {requestJson} = await import('../../services/request')

        void getCurrentUserSoulMd()
        void updateCurrentUserSoulMd({contentMarkdown: '# Soul', enabled: false})
        void getCurrentUserSoulMdVersions()

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/me/soul-md')
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/me/soul-md', {
            method: 'PUT',
            body: {contentMarkdown: '# Soul', enabled: false},
        })
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/me/soul-md/versions')
    })
})
