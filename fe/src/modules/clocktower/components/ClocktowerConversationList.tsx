import {Badge, Button, Empty, List, Space, Tag, Typography} from 'antd'
import {DateTimeText} from '../../../components/DateTimeText'
import type {ClocktowerConversationResponse} from '../clocktowerTypes'
import type {ClocktowerChatPolicy} from './ClocktowerChatPanel'

type ClocktowerConversationListProps = {
    conversations: ClocktowerConversationResponse[]
    activeConversationId?: number | null
    getPolicy: (conversation: ClocktowerConversationResponse) => ClocktowerChatPolicy
    onSelect: (conversation: ClocktowerConversationResponse) => void
}

export function ClocktowerConversationList({
    activeConversationId,
    conversations,
    getPolicy,
    onSelect,
}: ClocktowerConversationListProps) {
    return (
        <List
            dataSource={conversations}
            locale={{emptyText: <Empty description="暂无会话"/>}}
            renderItem={(conversation) => {
                const policy = getPolicy(conversation)
                return (
                    <List.Item
                        actions={[
                            <Button
                                autoInsertSpace={false}
                                key="select"
                                onClick={() => onSelect(conversation)}
                                size="small"
                                type={activeConversationId === conversation.conversationId ? 'primary' : 'default'}
                            >
                                查看
                            </Button>,
                        ]}
                    >
                        <Space orientation="vertical" size={2}>
                            <Space wrap>
                                <Badge count={conversation.unreadCount ?? 0} overflowCount={99} size="small">
                                    <Typography.Text strong>
                                        {conversationLabel(conversation)}
                                    </Typography.Text>
                                </Badge>
                                <Tag color={policy.readOnly ? 'default' : 'success'}>
                                    {policy.readOnly ? '只读' : '可发言'}
                                </Tag>
                                <Badge
                                    status={policy.readOnly ? 'default' : 'success'}
                                    text={policy.reason}
                                />
                            </Space>
                            <Space wrap size={4}>
                                <Tag>{conversation.groupKey}</Tag>
                                <Tag>#{conversation.messageSeq}</Tag>
                                {conversation.lastActiveAt ?? conversation.lastMessageAt ? (
                                    <Typography.Text type="secondary">
                                        <DateTimeText value={conversation.lastActiveAt ?? conversation.lastMessageAt}/>
                                    </Typography.Text>
                                ) : null}
                            </Space>
                            {conversation.lastMessage?.content && (
                                <Typography.Text ellipsis type="secondary">
                                    {conversation.lastMessage.content}
                                </Typography.Text>
                            )}
                        </Space>
                    </List.Item>
                )
            }}
            rowKey="conversationId"
            size="small"
        />
    )
}

export function conversationLabel(conversation: ClocktowerConversationResponse) {
    const groupKey = conversation.groupKey.toUpperCase()
    if (groupKey === 'PUBLIC') {
        return '玩家公聊'
    }
    if (groupKey === 'PRIVATE') {
        return conversation.participantKey ? `${conversation.participantKey} 私聊` : '私聊'
    }
    if (groupKey === 'SPECTATOR') {
        return '旁观席'
    }
    if (groupKey === 'SYSTEM') {
        return '公告'
    }
    return conversation.channelKey || conversation.conversationType
}
