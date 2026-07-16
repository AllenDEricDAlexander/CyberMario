import {GlobalOutlined, ReloadOutlined} from '@ant-design/icons'
import {Avatar, Badge, Button, Empty, Skeleton, Tag, Typography} from 'antd'
import type {PlatformConversationView} from '../platformImTypes'

export type ImConversationPaneProps = {
    conversations: PlatformConversationView[]
    selectedConversationId?: number
    loading?: boolean
    onRefresh: () => void
    onSelect: (conversationId: number) => void
}

export function ImConversationPane(props: ImConversationPaneProps) {
    const conversations = sortPlatformConversations(props.conversations)

    return (
        <aside aria-label="会话列表" className="platform-im-list-pane">
            <header className="platform-im-pane-header">
                <div>
                    <Typography.Title level={4}>消息</Typography.Title>
                    <Typography.Text type="secondary">最近会话与公共频道</Typography.Text>
                </div>
                <Button aria-label="刷新会话" icon={<ReloadOutlined/>} onClick={props.onRefresh}/>
            </header>
            <div className="platform-im-list-scroll">
                {props.loading ? (
                    <Skeleton active paragraph={{rows: 5}} title={false}/>
                ) : conversations.length === 0 ? (
                    <Empty description="暂无会话" image={Empty.PRESENTED_IMAGE_SIMPLE}/>
                ) : conversations.map((conversation) => (
                    <button
                        aria-current={conversation.conversationId === props.selectedConversationId ? 'true' : undefined}
                        className="platform-im-conversation-row"
                        key={conversation.conversationId}
                        onClick={() => props.onSelect(conversation.conversationId)}
                        type="button"
                    >
                        <Badge count={conversation.unreadCount} offset={[-2, 2]} overflowCount={99} size="small">
                            <Avatar icon={conversation.displayType === 'PUBLIC_CHANNEL' ? <GlobalOutlined/> : undefined}
                                    src={conversation.avatarUrl}>
                                {conversation.title.slice(0, 1)}
                            </Avatar>
                        </Badge>
                        <span className="platform-im-conversation-copy">
                            <span className="platform-im-conversation-title">
                                <Typography.Text ellipsis>{conversation.title}</Typography.Text>
                                {conversation.displayType === 'PUBLIC_CHANNEL' && <Tag color="cyan">置顶</Tag>}
                            </span>
                            <Typography.Text ellipsis type="secondary">
                                {conversationPreview(conversation)}
                            </Typography.Text>
                        </span>
                        <time dateTime={conversation.lastActiveAt ?? conversation.lastMessageAt ?? undefined}>
                            {shortTime(conversation.lastActiveAt ?? conversation.lastMessageAt)}
                        </time>
                    </button>
                ))}
            </div>
        </aside>
    )
}

export function sortPlatformConversations(conversations: PlatformConversationView[]) {
    return [...conversations].sort((left, right) => {
        const pinned = Number(right.displayType === 'PUBLIC_CHANNEL') - Number(left.displayType === 'PUBLIC_CHANNEL')
        if (pinned !== 0) return pinned
        const active = timestamp(right.lastActiveAt ?? right.lastMessageAt) - timestamp(left.lastActiveAt ?? left.lastMessageAt)
        return active || right.conversationId - left.conversationId
    })
}

function conversationPreview(conversation: PlatformConversationView) {
    if (!conversation.lastMessage) {
        return conversation.canPost ? '开始聊天' : '只读会话'
    }
    const sender = conversation.lastMessageSender?.displayName
    const content = conversation.lastMessage.messageType === 'SYSTEM'
        ? `[系统] ${conversation.lastMessage.content ?? ''}`
        : conversation.lastMessage.content ?? ''
    return sender ? `${sender}: ${content}` : content
}

function timestamp(value?: string | null) {
    if (!value) return 0
    const parsed = Date.parse(value)
    return Number.isNaN(parsed) ? 0 : parsed
}

function shortTime(value?: string | null) {
    if (!value) return ''
    const parsed = new Date(value)
    if (Number.isNaN(parsed.getTime())) return ''
    return new Intl.DateTimeFormat('zh-CN', {month: 'numeric', day: 'numeric'}).format(parsed)
}
