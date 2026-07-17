import {GlobalOutlined, ReloadOutlined, TeamOutlined, UserOutlined} from '@ant-design/icons'
import {Avatar, Badge, Button, Empty, Skeleton, Tree, Typography} from 'antd'
import type {PlatformConversationView} from '../platformImTypes'

type ConversationTreeNode = {
    key: number
    title: string
    conversation: PlatformConversationView
    children?: ConversationTreeNode[]
}

export type ImConversationPaneProps = {
    conversations: PlatformConversationView[]
    selectedConversationId?: number
    loading?: boolean
    onRefresh: () => void
    onSelect: (conversationId: number) => void
}

export function ImConversationPane(props: ImConversationPaneProps) {
    const conversationTree = buildPlatformConversationTree(props.conversations)

    return (
        <aside aria-label="会话列表" className="platform-im-list-pane">
            <header className="platform-im-pane-header">
                <div>
                    <Typography.Title level={4}>消息</Typography.Title>
                    <Typography.Text type="secondary">频道群组按层级展示</Typography.Text>
                </div>
                <Button aria-label="刷新会话" icon={<ReloadOutlined/>} onClick={props.onRefresh}/>
            </header>
            <div className="platform-im-list-scroll">
                {props.loading ? (
                    <Skeleton active paragraph={{rows: 5}} title={false}/>
                ) : conversationTree.length === 0 ? (
                    <Empty description="暂无会话" image={Empty.PRESENTED_IMAGE_SIMPLE}/>
                ) : (
                    <Tree<ConversationTreeNode>
                        blockNode
                        className="platform-im-conversation-tree"
                        defaultExpandAll
                        selectedKeys={props.selectedConversationId ? [props.selectedConversationId] : []}
                        titleRender={(node) => (
                            <ConversationTreeRow
                                conversation={node.conversation}
                                selected={node.conversation.conversationId === props.selectedConversationId}
                            />
                        )}
                        treeData={conversationTree}
                        onSelect={(keys) => {
                            const conversationId = Number(keys[0])
                            if (Number.isFinite(conversationId)) {
                                props.onSelect(conversationId)
                            }
                        }}
                    />
                )}
            </div>
        </aside>
    )
}

function ConversationTreeRow(props: {conversation: PlatformConversationView; selected: boolean}) {
    const {conversation} = props
    return (
        <span
            aria-current={props.selected ? 'true' : undefined}
            className="platform-im-conversation-row"
        >
            <Badge count={conversation.unreadCount} offset={[-2, 2]} overflowCount={99} size="small">
                <Avatar icon={conversationIcon(conversation)} src={conversation.avatarUrl}>
                    {conversation.title.slice(0, 1)}
                </Avatar>
            </Badge>
            <span className="platform-im-conversation-copy">
                <span className="platform-im-conversation-title">
                    <Typography.Text ellipsis>{conversation.title}</Typography.Text>
                </span>
                <Typography.Text ellipsis type="secondary">
                    {conversationPreview(conversation)}
                </Typography.Text>
            </span>
            <time dateTime={conversation.lastActiveAt ?? conversation.lastMessageAt ?? undefined}>
                {shortTime(conversation.lastActiveAt ?? conversation.lastMessageAt)}
            </time>
        </span>
    )
}

export function buildPlatformConversationTree(conversations: PlatformConversationView[]): ConversationTreeNode[] {
    const sorted = sortPlatformConversations(conversations)
    const channelsBySurfaceId = new Map(
        sorted
            .filter((conversation) => conversation.displayType === 'CHANNEL' && conversation.surfaceId)
            .map((conversation) => [conversation.surfaceId!, conversation]),
    )
    const childGroups = new Map<number, PlatformConversationView[]>()
    const nestedConversationIds = new Set<number>()

    sorted.forEach((conversation) => {
        if (conversation.displayType !== 'GROUP'
            || !conversation.channelId
            || !channelsBySurfaceId.has(conversation.channelId)) {
            return
        }
        const groups = childGroups.get(conversation.channelId) ?? []
        groups.push(conversation)
        childGroups.set(conversation.channelId, groups)
        nestedConversationIds.add(conversation.conversationId)
    })

    return sorted
        .filter((conversation) => !nestedConversationIds.has(conversation.conversationId))
        .map((conversation) => ({
            key: conversation.conversationId,
            title: conversation.title,
            conversation,
            children: conversation.displayType === 'CHANNEL'
                ? (childGroups.get(conversation.surfaceId ?? -1) ?? []).map(treeNode)
                : undefined,
        }))
}

function treeNode(conversation: PlatformConversationView): ConversationTreeNode {
    return {
        key: conversation.conversationId,
        title: conversation.title,
        conversation,
    }
}

export function sortPlatformConversations(conversations: PlatformConversationView[]) {
    return [...conversations].sort((left, right) => {
        const active = timestamp(right.lastActiveAt ?? right.lastMessageAt) - timestamp(left.lastActiveAt ?? left.lastMessageAt)
        return active || right.conversationId - left.conversationId
    })
}

function conversationIcon(conversation: PlatformConversationView) {
    if (conversation.displayType === 'CHANNEL') return <GlobalOutlined/>
    if (conversation.displayType === 'GROUP') return <TeamOutlined/>
    return <UserOutlined/>
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
