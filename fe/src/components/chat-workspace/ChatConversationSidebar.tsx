import {InboxOutlined, PlusOutlined, ReloadOutlined} from '@ant-design/icons'
import {Button, Empty, Space, Tooltip, Typography} from 'antd'
import {Conversations} from '@ant-design/x'
import {useMemo} from 'react'
import type {ConversationItemType, ConversationsProps} from '@ant-design/x'
import type {ChatWorkspaceConversation} from './chatWorkspaceTypes'

export type ChatConversationSidebarProps = {
    brandTitle: string
    brandDescription?: string
    conversations: ChatWorkspaceConversation[]
    activeKey?: string
    loading?: boolean
    onActiveChange: NonNullable<ConversationsProps['onActiveChange']>
    onNewConversation: () => void
    onReload?: () => void
    onArchive?: (conversationKey?: string) => void
}

export function ChatConversationSidebar(props: ChatConversationSidebarProps) {
    const {
        brandTitle,
        brandDescription,
        conversations,
        activeKey,
        loading,
        onActiveChange,
        onNewConversation,
        onReload,
        onArchive,
    } = props

    const items = useMemo<ConversationItemType[]>(
        () => conversations.map(conversation => ({
            key: conversation.key,
            label: conversation.label,
            group: conversation.group,
            title: typeof conversation.label === 'string' ? conversation.label : undefined,
            description: conversation.description,
            updatedAt: conversation.updatedAt,
        })),
        [conversations]
    )

    return (
        <aside className="chat-workspace-x-sidebar" aria-label={`${brandTitle} conversations`}>
            <div className="chat-workspace-x-sidebar-brand">
                <Typography.Title className="chat-workspace-x-sidebar-title" level={4}>
                    {brandTitle}
                </Typography.Title>
                {brandDescription && (
                    <Typography.Text className="chat-workspace-x-sidebar-description" type="secondary">
                        {brandDescription}
                    </Typography.Text>
                )}
            </div>

            <div className="chat-workspace-x-sidebar-actions">
                <Button
                    block
                    icon={<PlusOutlined/>}
                    type="primary"
                    onClick={onNewConversation}
                >
                    New Chat
                </Button>
                <Space.Compact>
                    {onReload && (
                        <Tooltip title="Refresh conversations">
                            <Button
                                aria-label="Refresh conversations"
                                icon={<ReloadOutlined/>}
                                loading={loading}
                                onClick={onReload}
                            />
                        </Tooltip>
                    )}
                    {onArchive && (
                        <Tooltip title="Archive current conversation">
                            <Button
                                aria-label="Archive current conversation"
                                disabled={!activeKey}
                                icon={<InboxOutlined/>}
                                onClick={() => onArchive(activeKey)}
                            />
                        </Tooltip>
                    )}
                </Space.Compact>
            </div>

            <div className="chat-workspace-x-sidebar-list">
                {items.length > 0 ? (
                    <Conversations
                        activeKey={activeKey}
                        groupable
                        items={items}
                        onActiveChange={onActiveChange}
                    />
                ) : (
                    <Empty
                        className="chat-workspace-x-sidebar-empty"
                        description={loading ? 'Loading conversations...' : 'No conversations yet'}
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                    />
                )}
            </div>
        </aside>
    )
}
