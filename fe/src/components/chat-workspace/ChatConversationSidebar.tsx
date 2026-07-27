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
        <aside className="chat-workspace-x-sidebar" aria-label={`${brandTitle} 会话列表`}>
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
                    新建会话
                </Button>
                <Space.Compact>
                    {onReload && (
                        <Tooltip title="刷新会话列表">
                            <Button
                                aria-label="刷新会话列表"
                                icon={<ReloadOutlined/>}
                                loading={loading}
                                onClick={onReload}
                            />
                        </Tooltip>
                    )}
                    {onArchive && (
                        <Tooltip title="归档当前会话">
                            <Button
                                aria-label="归档当前会话"
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
                        description={loading ? '正在加载会话…' : '还没有会话，点击「新建会话」开始'}
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                    />
                )}
            </div>
        </aside>
    )
}
