import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    acceptPlatformFriendRequest,
    cancelPlatformFriendRequest,
    createPlatformFriendRequest,
    createPlatformGroup,
    getPlatformImBootstrap,
    listPlatformFriendRequests,
    listPlatformFriends,
    listPlatformGroups,
    listPlatformImConversations,
    listPlatformJoinRequests,
    listPlatformSurfaceMembers,
    rejectPlatformFriendRequest,
    removePlatformFriend,
    removePlatformSurfaceMember,
    searchPlatformUsers,
    updatePlatformFriendRemark,
} from './platformImService'

vi.mock('../../services/request', () => ({
    requestJson: vi.fn(),
}))

describe('platformImService', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    test('builds platform bootstrap, conversation, discovery, and page endpoints', async () => {
        const {requestJson} = await import('../../services/request')

        void getPlatformImBootstrap()
        void listPlatformImConversations()
        void searchPlatformUsers({keyword: 'Mario & Luigi', page: 0, size: 99})
        void listPlatformFriends({page: 2, size: 10})
        void listPlatformFriendRequests({box: 'OUTGOING', page: 3, size: 15})
        void listPlatformGroups()
        void listPlatformSurfaceMembers('GROUP', 8, {page: 2, size: 100})
        void listPlatformJoinRequests('CHANNEL', 9)

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/im/platform/bootstrap')
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/im/platform/conversations')
        expect(requestJson).toHaveBeenNthCalledWith(
            3,
            '/api/im/platform/users?keyword=Mario+%26+Luigi&page=1&size=20',
        )
        expect(requestJson).toHaveBeenNthCalledWith(4, '/api/im/platform/friends?page=2&size=10')
        expect(requestJson).toHaveBeenNthCalledWith(
            5,
            '/api/im/platform/friend-requests?box=OUTGOING&page=3&size=15',
        )
        expect(requestJson).toHaveBeenNthCalledWith(6, '/api/im/platform/groups')
        expect(requestJson).toHaveBeenNthCalledWith(7, '/api/im/surfaces/GROUP/8/members?page=2&size=50')
        expect(requestJson).toHaveBeenNthCalledWith(8, '/api/im/surfaces/CHANNEL/9/join-requests?page=1&size=50')
    })

    test('sends friendship, group, and member mutations to exact routes', async () => {
        const {requestJson} = await import('../../services/request')

        void createPlatformFriendRequest({targetUserId: 21, message: 'Hi'})
        void acceptPlatformFriendRequest(3, {reason: 'Welcome'})
        void rejectPlatformFriendRequest(4)
        void cancelPlatformFriendRequest(5)
        void updatePlatformFriendRemark(21, {remark: 'Luigi'})
        void removePlatformFriend(21)
        void createPlatformGroup({groupKey: 'mushroom', name: 'Mushroom', joinPolicy: 'APPROVAL'})
        void removePlatformSurfaceMember('GROUP', 8, 21)

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/im/platform/friend-requests', {
            method: 'POST',
            body: {targetUserId: 21, message: 'Hi'},
        })
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/im/platform/friend-requests/3/accept', {
            method: 'POST',
            body: {reason: 'Welcome'},
        })
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/im/platform/friend-requests/4/reject', {
            method: 'POST',
            body: undefined,
        })
        expect(requestJson).toHaveBeenNthCalledWith(4, '/api/im/platform/friend-requests/5/cancel', {
            method: 'POST',
        })
        expect(requestJson).toHaveBeenNthCalledWith(5, '/api/im/platform/friends/21', {
            method: 'PATCH',
            body: {remark: 'Luigi'},
        })
        expect(requestJson).toHaveBeenNthCalledWith(6, '/api/im/platform/friends/21', {method: 'DELETE'})
        expect(requestJson).toHaveBeenNthCalledWith(7, '/api/im/platform/groups', {
            method: 'POST',
            body: {groupKey: 'mushroom', name: 'Mushroom', joinPolicy: 'APPROVAL'},
        })
        expect(requestJson).toHaveBeenNthCalledWith(8, '/api/im/surfaces/GROUP/8/members/21', {
            method: 'DELETE',
        })
    })
})
