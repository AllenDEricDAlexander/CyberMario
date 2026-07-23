import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    createUser,
    getApiPermissions,
    getPermissions,
    getRoles,
    getUsers,
    reissueUserActivation,
} from './rbacService'

vi.mock('../../services/request', () => ({
    requestJson: vi.fn(),
}))

describe('rbacService', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    test('builds encoded page query strings for RBAC list APIs', async () => {
        const {requestJson} = await import('../../services/request')

        void getUsers({page: 2, size: 30})
        void getRoles({page: 3, size: 40})
        void getPermissions({})
        void getApiPermissions({})

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/admin/users?page=2&size=30')
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/admin/roles?page=3&size=40')
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/admin/permissions?page=1&size=50')
        expect(requestJson).toHaveBeenNthCalledWith(4, '/api/admin/api-permissions?page=1&size=20')
    })

    test('sends the pending-user create contract without password or status', async () => {
        const {requestJson} = await import('../../services/request')

        void createUser({
            accountNo: 'mario',
            username: 'mario',
            email: 'mario@example.com',
            roleIds: [1, 2],
        })

        expect(requestJson).toHaveBeenCalledWith('/api/admin/users', {
            method: 'POST',
            body: {
                accountNo: 'mario',
                username: 'mario',
                email: 'mario@example.com',
                roleIds: [1, 2],
            },
        })
        expect(vi.mocked(requestJson).mock.calls[0]?.[1]?.body).not.toHaveProperty('initialPassword')
        expect(vi.mocked(requestJson).mock.calls[0]?.[1]?.body).not.toHaveProperty('status')
    })

    test('posts the explicit activation reissue endpoint without a request body', async () => {
        const {requestJson} = await import('../../services/request')

        void reissueUserActivation(42)

        expect(requestJson).toHaveBeenCalledWith('/api/admin/users/42/activation-token', {
            method: 'POST',
        })
    })
})
