import {Empty, List, Space, Tag, Typography} from 'antd'
import {DateTimeText} from '../../../components/DateTimeText'
import type {ClocktowerMessageResponse} from '../clocktowerTypes'

type ClocktowerMessageListProps = {
    messages: ClocktowerMessageResponse[]
    loading?: boolean
}

export function ClocktowerMessageList({messages, loading}: ClocktowerMessageListProps) {
    return (
        <List
            dataSource={messages}
            loading={loading}
            locale={{emptyText: <Empty description="暂无消息"/>}}
            renderItem={(message) => (
                <List.Item>
                    <Space orientation="vertical" size={2} style={{width: '100%'}}>
                        <Space wrap>
                            <Tag>#{message.messageSeq}</Tag>
                            <Tag color={message.messageType === 'TEXT' ? 'processing' : 'default'}>
                                {message.messageType}
                            </Tag>
                            {message.status === 'PENDING' && <Tag color="warning">发送中</Tag>}
                            {message.status === 'FAILED' && <Tag color="error">发送失败</Tag>}
                            <Typography.Text type="secondary">
                                <DateTimeText value={message.sentAt}/>
                            </Typography.Text>
                        </Space>
                        <Typography.Paragraph style={{marginBottom: 0, whiteSpace: 'pre-wrap'}}>
                            {message.content}
                        </Typography.Paragraph>
                    </Space>
                </List.Item>
            )}
            rowKey={(message) => message.messageId > 0 ? message.messageId : message.clientMsgId ?? message.messageSeq}
            size="small"
        />
    )
}
