import {Alert, Button, Spin} from 'antd'
import {ImActivityRail} from './components/ImActivityRail'
import {ImConversationPane} from './components/ImConversationPane'
import {ImFriendPane} from './components/ImFriendPane'
import {ImGroupPane} from './components/ImGroupPane'
import {ImThreadPane} from './components/ImThreadPane'
import type {PlatformImActivity} from './platformImTypes'
import {usePlatformImWorkspace} from './usePlatformImWorkspace'
import './PlatformImPage.css'

export type PlatformImWorkspace = ReturnType<typeof usePlatformImWorkspace>

function PlatformImPage() {
    const workspace = usePlatformImWorkspace()
    return <PlatformImWorkspaceView workspace={workspace}/>
}

export function PlatformImWorkspaceView({workspace}: {workspace: PlatformImWorkspace}) {
    if (workspace.status === 'loading' && !workspace.currentUser) {
        return (
            <div aria-label="正在加载即时通信" className="platform-im-loading">
                <Spin size="large"/>
            </div>
        )
    }

    if (workspace.status === 'error' && !workspace.currentUser) {
        return (
            <Alert
                action={<Button onClick={() => void workspace.reload()}>重试</Button>}
                description={workspace.error}
                message="即时通信加载失败"
                showIcon
                type="error"
            />
        )
    }

    function selectActivity(activity: PlatformImActivity) {
        workspace.selectActivity(activity)
        if (activity === 'FRIENDS') {
            void workspace.refreshFriends()
        } else if (activity === 'GROUPS') {
            void workspace.refreshGroups()
        }
    }

    function openPublicChannel() {
        const publicChannelId = workspace.publicChannel?.conversationId
        workspace.selectActivity('MESSAGES')
        if (publicChannelId) {
            void workspace.selectConversation(publicChannelId)
        }
    }

    function openConversation(conversationId: number) {
        workspace.selectActivity('MESSAGES')
        return workspace.selectConversation(conversationId)
    }

    const selectedGroup = workspace.selectedConversation?.displayType === 'GROUP'
        ? workspace.groups.find((group) => group.id === workspace.selectedConversation?.surfaceId)
        : undefined

    return (
        <div className="platform-im-shell">
            {workspace.error && (
                <Alert banner closable message={workspace.error} showIcon type="error"/>
            )}
            <div className="platform-im-workspace">
                <ImActivityRail
                    activity={workspace.activity}
                    currentUser={workspace.currentUser}
                    pendingFriendRequestCount={workspace.pendingFriendRequestCount}
                    unreadTotal={workspace.unreadTotal}
                    onActivityChange={selectActivity}
                    onOpenPublicChannel={openPublicChannel}
                />
                {workspace.activity === 'MESSAGES' && (
                    <>
                        <ImConversationPane
                            conversations={workspace.conversations}
                            loading={workspace.status === 'loading'}
                            selectedConversationId={workspace.selectedConversationId}
                            onRefresh={() => void workspace.refreshConversations()}
                            onSelect={(conversationId) => void workspace.selectConversation(conversationId)}
                        />
                        <ImThreadPane
                            conversation={workspace.selectedConversation}
                            currentUser={workspace.currentUser}
                            group={selectedGroup}
                            messages={workspace.messages}
                            onAddFriend={(userId) => workspace.requestFriend({targetUserId: userId})}
                            onApplyJoin={(surfaceType, surfaceId) => workspace.applyJoin({surfaceType, surfaceId})}
                            onCancelJoin={workspace.cancelJoin}
                            onLeave={workspace.leaveSurface}
                            onRetry={workspace.retryMessage}
                            onSend={workspace.sendText}
                        />
                    </>
                )}
                {workspace.activity === 'FRIENDS' && (
                    <ImFriendPane
                        friends={workspace.friends}
                        incomingRequests={workspace.incomingRequests}
                        outgoingRequests={workspace.outgoingRequests}
                        userResults={workspace.userResults}
                        onAcceptRequest={workspace.acceptFriendRequest}
                        onCancelRequest={workspace.cancelFriendRequest}
                        onOpenDm={workspace.openDm}
                        onRefresh={workspace.refreshFriends}
                        onRejectRequest={workspace.rejectFriendRequest}
                        onRemoveFriend={workspace.removeFriend}
                        onRequestFriend={workspace.requestFriend}
                        onSearchUsers={workspace.searchUsers}
                        onUpdateRemark={(friendUserId, remark) => workspace.updateFriendRemark(friendUserId, {remark})}
                    />
                )}
                {workspace.activity === 'GROUPS' && (
                    <ImGroupPane
                        currentUser={workspace.currentUser}
                        groups={workspace.groups}
                        joinRequests={workspace.joinRequests}
                        members={workspace.surfaceMembers}
                        onApply={workspace.applyJoin}
                        onApprove={workspace.approveJoin}
                        onCancel={workspace.cancelJoin}
                        onCreate={workspace.createGroup}
                        onLeave={workspace.leaveSurface}
                        onLoadManagement={workspace.refreshSurfaceAdmin}
                        onOpenConversation={openConversation}
                        onRefresh={workspace.refreshGroups}
                        onReject={workspace.rejectJoin}
                        onRemoveMember={workspace.removeSurfaceMember}
                    />
                )}
            </div>
        </div>
    )
}

export const Component = PlatformImPage
