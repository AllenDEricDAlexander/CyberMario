import {beforeEach, describe, expect, test, vi} from 'vitest'
import {getApiPermissions, getPermissions, getRoles, getUsers} from './rbacService'

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
})
