import {fireEvent, render, screen} from '@testing-library/react'
import {App} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import type {PlatformConversationView} from './platformImTypes'
import {PlatformImWorkspaceView, type PlatformImWorkspace} from './PlatformImPage'

vi.mock('./components/ImThreadPane', () => ({
    ImThreadPane: ({conversation}: {conversation?: PlatformConversationView}) => (
        <section aria-label="消息详情">thread:{conversation?.title}</section>
    ),
}))
vi.mock('./components/ImFriendPane', () => ({
    ImFriendPane: () => <><aside aria-label="联系人列表"/><section aria-label="联系人详情"/></>,
}))
vi.mock('./components/ImGroupPane', () => ({
    ImGroupPane: () => <><aside aria-label="群组列表"/><section aria-label="群组详情"/></>,
}))
vi.mock('./components/ImChannelPane', () => ({
    ImChannelPane: () => <><aside aria-label="频道列表"/><section aria-label="频道详情"/></>,
}))
vi.mock('./components/ImInvitationPane', () => ({
    ImInvitationPane: () => <><aside aria-label="邀请列表"/><section aria-label="邀请详情"/></>,
}))

describe('PlatformImWorkspaceView', () => {
    test('renders the three-column workspace without a pinned default channel', () => {
        renderWorkspace()

        expect(screen.getByRole('navigation', {name: '即时通信功能'})).toBeTruthy()
        expect(screen.getByRole('complementary', {name: '会话列表'})).toBeTruthy()
        expect(screen.getByRole('region', {name: '消息详情'}).textContent).toContain('产品频道')
        expect(screen.queryByText('置顶')).toBeNull()
        expect(screen.getAllByText('3').length).toBeGreaterThan(0)
    })

    test('loads member-scoped channel and invitation data from the rail', () => {
        const workspace = createWorkspace()
        renderWorkspace(workspace)

        fireEvent.click(screen.getByRole('button', {name: '联系人'}))
        expect(workspace.selectActivity).toHaveBeenCalledWith('FRIENDS')
        expect(workspace.refreshFriends).toHaveBeenCalledTimes(1)

        fireEvent.click(screen.getByRole('button', {name: '频道'}))
        expect(workspace.selectActivity).toHaveBeenLastCalledWith('CHANNELS')
        expect(workspace.refreshChannels).toHaveBeenCalledTimes(1)

        fireEvent.click(screen.getByRole('button', {name: '邀请'}))
        expect(workspace.selectActivity).toHaveBeenLastCalledWith('INVITATIONS')
        expect(workspace.refreshInvitations).toHaveBeenCalledTimes(1)
    })
})

function renderWorkspace(workspace = createWorkspace()) {
    return render(<App><PlatformImWorkspaceView workspace={workspace}/></App>)
}

function createWorkspace(): PlatformImWorkspace {
    const channel = conversation()
    return {
        activity: 'MESSAGES',
        selectedConversationId: 10,
        currentUser: {userId: 1, accountNo: 'mario', displayName: 'Mario'},
        conversations: [channel],
        messagesByConversation: {10: []},
        friends: [],
        incomingRequests: [],
        outgoingRequests: [],
        userResults: [],
        channels: [],
        groups: [],
        invitations: [],
        surfaceMembers: [],
        joinRequests: [],
        unreadTotal: 3,
        pendingFriendRequestCount: 1,
        pendingInvitationCount: 2,
        status: 'ready',
        selectedConversation: channel,
        messages: [],
        selectActivity: vi.fn(),
        selectConversation: vi.fn().mockResolvedValue(undefined),
        reload: vi.fn().mockResolvedValue(undefined),
        refreshConversations: vi.fn().mockResolvedValue([channel]),
        refreshFriends: vi.fn().mockResolvedValue(undefined),
        refreshChannels: vi.fn().mockResolvedValue([]),
        refreshGroups: vi.fn().mockResolvedValue(undefined),
        refreshInvitations: vi.fn().mockResolvedValue([]),
        refreshSurfaceAdmin: vi.fn().mockResolvedValue(undefined),
        refreshPlatformData: vi.fn().mockResolvedValue(undefined),
        searchUsers: vi.fn().mockResolvedValue(undefined),
        sendText: vi.fn().mockResolvedValue(true),
        retryMessage: vi.fn().mockResolvedValue(true),
        handleResync: vi.fn().mockResolvedValue(undefined),
        requestFriend: vi.fn().mockResolvedValue(undefined),
        acceptFriendRequest: vi.fn().mockResolvedValue(undefined),
        rejectFriendRequest: vi.fn().mockResolvedValue(undefined),
        cancelFriendRequest: vi.fn().mockResolvedValue(undefined),
        updateFriendRemark: vi.fn().mockResolvedValue(undefined),
        removeFriend: vi.fn().mockResolvedValue(undefined),
        openDm: vi.fn().mockResolvedValue(undefined),
        createChannel: vi.fn(),
        createGroup: vi.fn().mockResolvedValue(undefined),
        createChannelGroup: vi.fn(),
        listChannelGroups: vi.fn().mockResolvedValue([]),
        inviteSurface: vi.fn(),
        acceptInvitation: vi.fn().mockResolvedValue(undefined),
        rejectInvitation: vi.fn().mockResolvedValue(undefined),
        transferOwnership: vi.fn().mockResolvedValue(undefined),
        applyJoin: vi.fn(),
        approveJoin: vi.fn().mockResolvedValue(undefined),
        rejectJoin: vi.fn().mockResolvedValue(undefined),
        cancelJoin: vi.fn(),
        leaveSurface: vi.fn().mockResolvedValue(undefined),
        removeSurfaceMember: vi.fn().mockResolvedValue(undefined),
    }
}

function conversation(): PlatformConversationView {
    return {
        conversationId: 10,
        conversationType: 'CHANNEL_MAIN',
        displayType: 'CHANNEL',
        title: '产品频道',
        ownerSurfaceType: 'CHANNEL',
        surfaceId: 2,
        surfaceKey: 'product',
        membershipStatus: 'ACTIVE',
        memberRole: 'MEMBER',
        canRead: true,
        canPost: true,
        messageSeq: 3,
        status: 'ACTIVE',
        unreadCount: 3,
    }
}
