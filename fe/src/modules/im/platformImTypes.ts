import type {PageResult} from '../../types/api'
import type {GroupView, ImJoinPolicy, ImMembershipStatus, ImSurfaceType, MessageView} from './imTypes'

export type PlatformImActivity = 'MESSAGES' | 'FRIENDS' | 'GROUPS'
export type PlatformConversationDisplayType = 'PUBLIC_CHANNEL' | 'GROUP' | 'DM'
export type FriendRequestBox = 'INCOMING' | 'OUTGOING'

export type PlatformUserView = {
    userId: number
    accountNo: string
    displayName: string
    avatarUrl?: string | null
}

export type PlatformConversationView = {
    conversationId: number
    conversationType: string
    displayType: PlatformConversationDisplayType
    title: string
    avatarUrl?: string | null
    peerUserId?: number | null
    ownerSurfaceType?: ImSurfaceType | null
    surfaceId?: number | null
    surfaceKey?: string | null
    membershipStatus?: ImMembershipStatus | null
    memberRole?: string | null
    canRead: boolean
    canPost: boolean
    messageSeq: number
    lastMessageId?: number | null
    lastMessageAt?: string | null
    lastMessage?: MessageView | null
    lastMessageSender?: PlatformUserView | null
    lastActiveAt?: string | null
    status: string
    unreadCount: number
}

export type PlatformBootstrapView = {
    currentUser: PlatformUserView
    publicChannel?: PlatformConversationView | null
    conversations: PlatformConversationView[]
    unreadTotal: number
    pendingFriendRequestCount: number
}

export type PlatformFriendView = {
    friendshipId: number
    friendUserId: number
    accountNo: string
    displayName: string
    avatarUrl?: string | null
    remark?: string | null
    available: boolean
    activatedAt: string
}

export type PlatformFriendRequestView = {
    id: number
    requesterUserId: number
    recipientUserId: number
    peerUserId: number
    peerAccountNo: string
    peerDisplayName: string
    peerAvatarUrl?: string | null
    peerAvailable: boolean
    status: string
    requestMessage?: string | null
    requestedAt: string
    decidedAt?: string | null
    decisionReason?: string | null
}

export type PlatformSurfaceMemberView = {
    membershipId: number
    userId: number
    accountNo: string
    displayName: string
    avatarUrl?: string | null
    available: boolean
    memberRole: string
    status: ImMembershipStatus
    mutedUntil?: string | null
    joinedAt: string
}

export type PlatformJoinRequestView = {
    joinRequestId: number
    userId: number
    accountNo: string
    displayName: string
    avatarUrl?: string | null
    available: boolean
    status: string
    requestedAt: string
}

export type PlatformGroupCreateRequest = {
    groupKey: string
    name: string
    joinPolicy: ImJoinPolicy
    metadataJson?: string | null
}

export type PlatformPageParams = {
    page?: number
    size?: number
}

export type PlatformUserSearchParams = PlatformPageParams & {
    keyword: string
}

export type PlatformFriendRequestParams = PlatformPageParams & {
    box: FriendRequestBox
}

export type PlatformFriendRequestCreateRequest = {
    targetUserId: number
    message?: string | null
}

export type PlatformFriendDecisionRequest = {
    reason?: string | null
}

export type PlatformFriendRemarkRequest = {
    remark?: string | null
}

export type PlatformUserPage = PageResult<PlatformUserView>
export type PlatformFriendPage = PageResult<PlatformFriendView>
export type PlatformFriendRequestPage = PageResult<PlatformFriendRequestView>
export type PlatformSurfaceMemberPage = PageResult<PlatformSurfaceMemberView>
export type PlatformJoinRequestPage = PageResult<PlatformJoinRequestView>
export type PlatformGroupView = GroupView

export type OptimisticMessageView = MessageView & {
    optimistic?: boolean
    error?: string
}
