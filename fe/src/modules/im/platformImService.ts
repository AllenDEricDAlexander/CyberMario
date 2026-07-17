import {requestJson} from '../../services/request'
import {buildSearchParams} from '../../services/urlSearch'
import type {ImSurfaceType} from './imTypes'
import type {
    PlatformBootstrapView,
    PlatformChannelGroupCreateRequest,
    PlatformChannelView,
    PlatformConversationView,
    PlatformFriendDecisionRequest,
    PlatformFriendPage,
    PlatformFriendRemarkRequest,
    PlatformFriendRequestCreateRequest,
    PlatformFriendRequestPage,
    PlatformFriendRequestParams,
    PlatformFriendRequestView,
    PlatformFriendView,
    PlatformGroupView,
    PlatformInvitationPage,
    PlatformInvitationView,
    PlatformJoinRequestPage,
    PlatformOwnershipTransferRequest,
    PlatformPageParams,
    PlatformSurfaceCreateRequest,
    PlatformSurfaceInvitationRequest,
    PlatformSurfaceMemberPage,
    PlatformUserPage,
    PlatformUserSearchParams,
} from './platformImTypes'

export function getPlatformImBootstrap() {
    return requestJson<PlatformBootstrapView>('/api/im/platform/bootstrap')
}

export function listPlatformImConversations() {
    return requestJson<PlatformConversationView[]>('/api/im/platform/conversations')
}

export function searchPlatformUsers(params: PlatformUserSearchParams) {
    const search = buildSearchParams({
        keyword: params.keyword,
        page: normalizePage(params.page),
        size: normalizeSize(params.size, 20),
    })
    return requestJson<PlatformUserPage>(`/api/im/platform/users?${search}`)
}

export function listPlatformFriends(params: PlatformPageParams = {}) {
    return requestJson<PlatformFriendPage>(`/api/im/platform/friends?${pageSearch(params, 20)}`)
}

export function listPlatformFriendRequests(params: PlatformFriendRequestParams) {
    const search = buildSearchParams({
        box: params.box,
        page: normalizePage(params.page),
        size: normalizeSize(params.size, 20),
    })
    return requestJson<PlatformFriendRequestPage>(`/api/im/platform/friend-requests?${search}`)
}

export function createPlatformFriendRequest(request: PlatformFriendRequestCreateRequest) {
    return requestJson<PlatformFriendRequestView>('/api/im/platform/friend-requests', {
        method: 'POST',
        body: request,
    })
}

export function acceptPlatformFriendRequest(id: number, request?: PlatformFriendDecisionRequest) {
    return decideFriendRequest(id, 'accept', request)
}

export function rejectPlatformFriendRequest(id: number, request?: PlatformFriendDecisionRequest) {
    return decideFriendRequest(id, 'reject', request)
}

export function cancelPlatformFriendRequest(id: number) {
    return requestJson<PlatformFriendRequestView>(`/api/im/platform/friend-requests/${id}/cancel`, {
        method: 'POST',
    })
}

export function updatePlatformFriendRemark(friendUserId: number, request: PlatformFriendRemarkRequest) {
    return requestJson<PlatformFriendView>(`/api/im/platform/friends/${friendUserId}`, {
        method: 'PATCH',
        body: request,
    })
}

export function removePlatformFriend(friendUserId: number) {
    return requestJson<void>(`/api/im/platform/friends/${friendUserId}`, {method: 'DELETE'})
}

export function createPlatformChannel(request: PlatformSurfaceCreateRequest) {
    return requestJson<PlatformChannelView>('/api/im/platform/channels', {
        method: 'POST',
        body: request,
    })
}

export function listPlatformChannels() {
    return requestJson<PlatformChannelView[]>('/api/im/platform/channels')
}

export function createPlatformGroup(request: PlatformSurfaceCreateRequest) {
    return requestJson<PlatformGroupView>('/api/im/platform/groups', {
        method: 'POST',
        body: request,
    })
}

export function listPlatformGroups() {
    return requestJson<PlatformGroupView[]>('/api/im/platform/groups')
}

export function createPlatformChannelGroup(channelId: number, request: PlatformChannelGroupCreateRequest) {
    return requestJson<PlatformGroupView>(`/api/im/platform/channels/${channelId}/groups`, {
        method: 'POST',
        body: request,
    })
}

export function listPlatformChannelGroups(channelId: number) {
    return requestJson<PlatformGroupView[]>(`/api/im/platform/channels/${channelId}/groups`)
}

export function invitePlatformSurface(
    surfaceType: ImSurfaceType,
    surfaceId: number,
    request: PlatformSurfaceInvitationRequest,
) {
    return requestJson<PlatformInvitationView>(
        `/api/im/platform/surfaces/${surfaceType}/${surfaceId}/invitations`,
        {method: 'POST', body: request},
    )
}

export function listPlatformInvitations(params: PlatformPageParams = {}) {
    return requestJson<PlatformInvitationPage>(`/api/im/platform/invitations?${pageSearch(params, 50)}`)
}

export function acceptPlatformInvitation(invitationId: number) {
    return decidePlatformInvitation(invitationId, 'accept')
}

export function rejectPlatformInvitation(invitationId: number) {
    return decidePlatformInvitation(invitationId, 'reject')
}

export function transferPlatformSurfaceOwnership(
    surfaceType: ImSurfaceType,
    surfaceId: number,
    request: PlatformOwnershipTransferRequest,
) {
    return requestJson<void>(`/api/im/platform/surfaces/${surfaceType}/${surfaceId}/owner`, {
        method: 'POST',
        body: request,
    })
}

export function listPlatformSurfaceMembers(
    surfaceType: ImSurfaceType,
    surfaceId: number,
    params: PlatformPageParams = {},
) {
    return requestJson<PlatformSurfaceMemberPage>(
        `/api/im/surfaces/${surfaceType}/${surfaceId}/members?${pageSearch(params, 50)}`,
    )
}

export function listPlatformJoinRequests(
    surfaceType: ImSurfaceType,
    surfaceId: number,
    params: PlatformPageParams = {},
) {
    return requestJson<PlatformJoinRequestPage>(
        `/api/im/surfaces/${surfaceType}/${surfaceId}/join-requests?${pageSearch(params, 50)}`,
    )
}

export function removePlatformSurfaceMember(surfaceType: ImSurfaceType, surfaceId: number, userId: number) {
    return requestJson<void>(`/api/im/surfaces/${surfaceType}/${surfaceId}/members/${userId}`, {
        method: 'DELETE',
    })
}

function decideFriendRequest(id: number, action: 'accept' | 'reject', request?: PlatformFriendDecisionRequest) {
    return requestJson<PlatformFriendRequestView>(`/api/im/platform/friend-requests/${id}/${action}`, {
        method: 'POST',
        body: request,
    })
}

function decidePlatformInvitation(id: number, action: 'accept' | 'reject') {
    return requestJson<PlatformInvitationView>(`/api/im/platform/invitations/${id}/${action}`, {
        method: 'POST',
    })
}

function pageSearch(params: PlatformPageParams, defaultSize: number) {
    return buildSearchParams({
        page: normalizePage(params.page),
        size: normalizeSize(params.size, defaultSize),
    })
}

function normalizePage(page: number | undefined) {
    return Math.max(1, page ?? 1)
}

function normalizeSize(size: number | undefined, maximum: number) {
    return Math.min(maximum, Math.max(1, size ?? maximum))
}
