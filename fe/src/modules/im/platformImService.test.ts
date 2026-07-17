import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    acceptPlatformInvitation,
    acceptPlatformFriendRequest,
    cancelPlatformFriendRequest,
    createPlatformChannel,
    createPlatformChannelGroup,
    createPlatformFriendRequest,
    createPlatformGroup,
    getPlatformImBootstrap,
    invitePlatformSurface,
    listPlatformChannelGroups,
    listPlatformChannels,
    listPlatformFriendRequests,
    listPlatformFriends,
    listPlatformGroups,
    listPlatformInvitations,
    listPlatformImConversations,
    listPlatformJoinRequests,
    listPlatformSurfaceMembers,
    rejectPlatformFriendRequest,
    rejectPlatformInvitation,
    removePlatformFriend,
    removePlatformSurfaceMember,
    searchPlatformUsers,
    transferPlatformSurfaceOwnership,
    updatePlatformFriendRemark,
} from './platformImService'

vi.mock('../../services/request', () => ({
    requestJson: vi.fn(),
}))

describe('platformImService', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    test('builds member-scoped channel, group, invitation, and page endpoints', async () => {
        const {requestJson} = await import('../../services/request')

        void getPlatformImBootstrap()
        void listPlatformImConversations()
        void searchPlatformUsers({keyword: 'Mario & Luigi', page: 0, size: 99})
        void listPlatformFriends({page: 2, size: 10})
        void listPlatformFriendRequests({box: 'OUTGOING', page: 3, size: 15})
        void listPlatformChannels()
        void listPlatformGroups()
        void listPlatformChannelGroups(7)
        void listPlatformInvitations()
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
        expect(requestJson).toHaveBeenNthCalledWith(6, '/api/im/platform/channels')
        expect(requestJson).toHaveBeenNthCalledWith(7, '/api/im/platform/groups')
        expect(requestJson).toHaveBeenNthCalledWith(8, '/api/im/platform/channels/7/groups')
        expect(requestJson).toHaveBeenNthCalledWith(9, '/api/im/platform/invitations?page=1&size=50')
        expect(requestJson).toHaveBeenNthCalledWith(10, '/api/im/surfaces/GROUP/8/members?page=2&size=50')
        expect(requestJson).toHaveBeenNthCalledWith(11, '/api/im/surfaces/CHANNEL/9/join-requests?page=1&size=50')
    })

    test('sends friendship and invitation-only surface mutations to exact routes', async () => {
        const {requestJson} = await import('../../services/request')

        void createPlatformFriendRequest({targetUserId: 21, message: 'Hi'})
        void acceptPlatformFriendRequest(3, {reason: 'Welcome'})
        void rejectPlatformFriendRequest(4)
        void cancelPlatformFriendRequest(5)
        void updatePlatformFriendRemark(21, {remark: 'Luigi'})
        void removePlatformFriend(21)
        void createPlatformChannel({name: 'Mushroom'})
        void createPlatformGroup({name: 'Mushroom'})
        void createPlatformChannelGroup(6, {name: 'Delivery', joinPolicy: 'APPROVAL'})
        void invitePlatformSurface('CHANNEL', 6, {inviteeUserId: 21, message: 'Join us'})
        void acceptPlatformInvitation(9)
        void rejectPlatformInvitation(10)
        void transferPlatformSurfaceOwnership('GROUP', 8, {newOwnerUserId: 21})
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
        expect(requestJson).toHaveBeenNthCalledWith(7, '/api/im/platform/channels', {
            method: 'POST',
            body: {name: 'Mushroom'},
        })
        expect(requestJson).toHaveBeenNthCalledWith(8, '/api/im/platform/groups', {
            method: 'POST',
            body: {name: 'Mushroom'},
        })
        expect(requestJson).toHaveBeenNthCalledWith(9, '/api/im/platform/channels/6/groups', {
            method: 'POST',
            body: {name: 'Delivery', joinPolicy: 'APPROVAL'},
        })
        expect(requestJson).toHaveBeenNthCalledWith(10, '/api/im/platform/surfaces/CHANNEL/6/invitations', {
            method: 'POST',
            body: {inviteeUserId: 21, message: 'Join us'},
        })
        expect(requestJson).toHaveBeenNthCalledWith(11, '/api/im/platform/invitations/9/accept', {method: 'POST'})
        expect(requestJson).toHaveBeenNthCalledWith(12, '/api/im/platform/invitations/10/reject', {method: 'POST'})
        expect(requestJson).toHaveBeenNthCalledWith(13, '/api/im/platform/surfaces/GROUP/8/owner', {
            method: 'POST',
            body: {newOwnerUserId: 21},
        })
        expect(requestJson).toHaveBeenNthCalledWith(14, '/api/im/surfaces/GROUP/8/members/21', {
            method: 'DELETE',
        })
    })
})
