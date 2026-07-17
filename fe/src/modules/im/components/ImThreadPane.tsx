import {GlobalOutlined, LockOutlined, TeamOutlined, UserOutlined} from '@ant-design/icons'
import {Alert, Avatar, Button, Empty, Space, Tag, Typography} from 'antd'
import {useMemo, useState} from 'react'
import {ChatMessageList, ChatSender} from '../../../components/chat-workspace'
import {mapImMessagesToWorkspaceMessages} from '../imMappers'
import type {ImSurfaceType, JoinResultView} from '../imTypes'
import type {
    OptimisticMessageView,
    PlatformConversationView,
    PlatformGroupView,
    PlatformUserView,
} from '../platformImTypes'
import {ImJoinApplyControls} from './ImJoinApplyControls'

const emojiChoices = ['😀', '👍', '🎉', '❤️']

export type ImThreadPaneProps = {
    conversation?: PlatformConversationView
    currentUser?: PlatformUserView
    group?: PlatformGroupView
    messages: OptimisticMessageView[]
    onSend: (content: string) => Promise<boolean>
    onRetry: (conversationId: number, clientMsgId: string) => Promise<boolean>
    onAddFriend: (userId: number) => Promise<void>
    onApplyJoin: (surfaceType: ImSurfaceType, surfaceId: number) => Promise<JoinResultView>
    onCancelJoin: (requestId: number) => Promise<JoinResultView>
    onLeave: (surfaceType: ImSurfaceType, surfaceId: number) => Promise<void>
}

export function ImThreadPane(props: ImThreadPaneProps) {
    const [input, setInput] = useState('')
    const [actionError, setActionError] = useState<string>()
    const workspaceMessages = useMemo(
        () => mapImMessagesToWorkspaceMessages(props.messages, {currentUserId: props.currentUser?.userId}),
        [props.currentUser?.userId, props.messages],
    )

    if (!props.conversation) {
        return (
            <section aria-label="消息详情" className="platform-im-detail-pane platform-im-detail-empty">
                <Empty description="选择一个会话开始" image={Empty.PRESENTED_IMAGE_SIMPLE}/>
            </section>
        )
    }

    const conversation = props.conversation
    const surfaceType = conversation.ownerSurfaceType
    const surfaceId = conversation.surfaceId
    const isSurface = Boolean(surfaceType && surfaceId)
    const canManageMembership = isSurface && conversation.membershipStatus !== 'BANNED'
    const sending = props.messages.some((message) => message.status === 'PENDING')

    async function send(content: string) {
        if (await props.onSend(content)) {
            setInput('')
        }
    }

    async function addFriend(userId: number) {
        setActionError(undefined)
        try {
            await props.onAddFriend(userId)
        } catch (reason) {
            setActionError(reason instanceof Error ? reason.message : '好友申请发送失败')
        }
    }

    return (
        <section aria-label="消息详情" className="platform-im-detail-pane platform-im-thread-pane">
            <header className="platform-im-thread-header">
                <Avatar icon={conversationIcon(conversation)} src={conversation.avatarUrl}>
                    {conversation.title.slice(0, 1)}
                </Avatar>
                <div className="platform-im-thread-heading">
                    <Typography.Title level={4}>{conversation.title}</Typography.Title>
                    <Space size={6} wrap>
                        <Tag>{displayTypeLabel(conversation.displayType)}</Tag>
                        {conversation.membershipStatus && <Tag>{membershipLabel(conversation.membershipStatus)}</Tag>}
                        {!conversation.canPost && <Tag icon={<LockOutlined/>}>只读</Tag>}
                    </Space>
                </div>
                {canManageMembership && surfaceType && surfaceId && (
                    <div className="platform-im-thread-membership">
                        <ImJoinApplyControls
                            currentMemberRole={conversation.memberRole}
                            currentMembershipStatus={conversation.membershipStatus}
                            joinPolicy={props.group?.joinPolicy ?? 'OPEN'}
                            surface={{surfaceType, surfaceId}}
                            onApply={(surface) => props.onApplyJoin(surface.surfaceType, surface.surfaceId)}
                            onApprove={() => undefined}
                            onCancel={props.onCancelJoin}
                            onLeave={(surface) => props.onLeave(surface.surfaceType, surface.surfaceId)}
                            onReject={() => undefined}
                        />
                    </div>
                )}
            </header>
            <div className="platform-im-thread-body">
                <ChatMessageList
                    canReloadMessage={(message) => message.status === 'error' && typeof message.clientMsgId === 'string'}
                    messages={workspaceMessages}
                    sending={sending}
                    onReload={(message) => {
                        if (typeof message.clientMsgId === 'string') {
                            void props.onRetry(conversation.conversationId, message.clientMsgId)
                        }
                    }}
                />
            </div>
            <footer className="platform-im-composer-area">
                {actionError && <Alert closable message={actionError} onClose={() => setActionError(undefined)} type="error"/>}
                {!conversation.canPost && (
                    <ReadOnlyExplanation conversation={conversation} onAddFriend={addFriend}/>
                )}
                <div className="platform-im-emoji-row" aria-label="快捷表情">
                    {emojiChoices.map((emoji) => (
                        <Button
                            aria-label={`插入 ${emoji}`}
                            disabled={!conversation.canPost}
                            key={emoji}
                            onClick={() => setInput((current) => `${current}${emoji}`)}
                            size="small"
                            type="text"
                        >
                            {emoji}
                        </Button>
                    ))}
                </div>
                <ChatSender
                    disabled={!conversation.canPost}
                    input={input}
                    placeholder={conversation.canPost ? '输入消息，Enter 发送' : '当前会话不可发送消息'}
                    sending={sending}
                    onInputChange={setInput}
                    onSend={(content) => void send(content)}
                />
            </footer>
        </section>
    )
}

function ReadOnlyExplanation(props: {
    conversation: PlatformConversationView
    onAddFriend: (userId: number) => Promise<void>
}) {
    const peerUserId = props.conversation.peerUserId
    if (props.conversation.displayType === 'DM' && peerUserId) {
        return (
            <Alert
                action={(
                    <Button onClick={() => void props.onAddFriend(peerUserId)} size="small">
                        重新添加好友
                    </Button>
                )}
                message="好友关系已解除，历史消息仍可查看；重新成为好友后才能继续发送。"
                showIcon
                type="info"
            />
        )
    }
    if (props.conversation.membershipStatus === 'BANNED') {
        return <Alert message="你已被移出或禁止加入该会话。" showIcon type="error"/>
    }
    if (props.conversation.membershipStatus === 'ACTIVE') {
        return <Alert message="当前成员状态不允许发送，可能处于禁言期。" showIcon type="warning"/>
    }
    return <Alert message="加入后即可发送消息并接收实时通知。" showIcon type="info"/>
}

function conversationIcon(conversation: PlatformConversationView) {
    if (conversation.displayType === 'CHANNEL') return <GlobalOutlined/>
    if (conversation.displayType === 'GROUP') return <TeamOutlined/>
    return <UserOutlined/>
}

function displayTypeLabel(displayType: PlatformConversationView['displayType']) {
    if (displayType === 'CHANNEL') return '频道'
    if (displayType === 'GROUP') return '群组'
    return '私聊'
}

function membershipLabel(status: string) {
    if (status === 'ACTIVE') return '已加入'
    if (status === 'PENDING') return '待审批'
    if (status === 'LEFT') return '已退出'
    if (status === 'BANNED') return '已禁止'
    return status
}
