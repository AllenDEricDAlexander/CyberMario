import {DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SendOutlined} from '@ant-design/icons'
import {Alert, App, Avatar, Button, Empty, Input, List, Modal, Popconfirm, Space, Tabs, Tag, Typography} from 'antd'
import {useEffect, useState} from 'react'
import type {
    PlatformFriendDecisionRequest,
    PlatformFriendRequestCreateRequest,
    PlatformFriendRequestView,
    PlatformFriendView,
    PlatformUserView,
} from '../platformImTypes'

type FriendTab = 'FRIENDS' | 'INCOMING' | 'OUTGOING'

export type ImFriendPaneProps = {
    friends: PlatformFriendView[]
    incomingRequests: PlatformFriendRequestView[]
    outgoingRequests: PlatformFriendRequestView[]
    userResults: PlatformUserView[]
    onRefresh: () => Promise<void>
    onSearchUsers: (keyword: string) => Promise<void>
    onRequestFriend: (request: PlatformFriendRequestCreateRequest) => Promise<void>
    onAcceptRequest: (id: number, request?: PlatformFriendDecisionRequest) => Promise<void>
    onRejectRequest: (id: number, request?: PlatformFriendDecisionRequest) => Promise<void>
    onCancelRequest: (id: number) => Promise<void>
    onUpdateRemark: (friendUserId: number, remark: string) => Promise<void>
    onRemoveFriend: (friendUserId: number) => Promise<void>
    onOpenDm: (friendUserId: number) => Promise<void>
}

export function ImFriendPane(props: ImFriendPaneProps) {
    const {message} = App.useApp()
    const [tab, setTab] = useState<FriendTab>('FRIENDS')
    const [selectedFriendId, setSelectedFriendId] = useState<number>()
    const [searchOpen, setSearchOpen] = useState(false)
    const [keyword, setKeyword] = useState('')
    const [requestMessage, setRequestMessage] = useState('')
    const [remark, setRemark] = useState('')
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState<string>()
    const selectedFriend = props.friends.find((friend) => friend.friendUserId === selectedFriendId)
        ?? props.friends[0]

    useEffect(() => {
        setRemark(selectedFriend?.remark ?? '')
    }, [selectedFriend?.friendUserId, selectedFriend?.remark])

    async function run(action: () => Promise<void>, success: string) {
        setBusy(true)
        setError(undefined)
        try {
            await action()
            void message.success(success)
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : '操作失败，请稍后重试')
        } finally {
            setBusy(false)
        }
    }

    return (
        <>
            <aside aria-label="联系人列表" className="platform-im-list-pane">
                <header className="platform-im-pane-header">
                    <div>
                        <Typography.Title level={4}>联系人</Typography.Title>
                        <Typography.Text type="secondary">好友与申请</Typography.Text>
                    </div>
                    <Space.Compact>
                        <Button aria-label="添加好友" icon={<PlusOutlined/>} onClick={() => setSearchOpen(true)}/>
                        <Button aria-label="刷新联系人" icon={<ReloadOutlined/>} onClick={() => void props.onRefresh()}/>
                    </Space.Compact>
                </header>
                {error && <Alert closable message={error} onClose={() => setError(undefined)} showIcon type="error"/>}
                <Tabs
                    activeKey={tab}
                    className="platform-im-tabs"
                    items={[
                        {key: 'FRIENDS', label: `好友 ${props.friends.length}`},
                        {key: 'INCOMING', label: `收到 ${pendingCount(props.incomingRequests)}`},
                        {key: 'OUTGOING', label: `发出 ${pendingCount(props.outgoingRequests)}`},
                    ]}
                    onChange={(key) => setTab(key as FriendTab)}
                    size="small"
                />
                <div className="platform-im-list-scroll">
                    {tab === 'FRIENDS' && (
                        <List
                            dataSource={props.friends}
                            locale={{emptyText: <Empty description="还没有好友" image={Empty.PRESENTED_IMAGE_SIMPLE}/>}}
                            renderItem={(friend) => (
                                <List.Item className={friend.friendUserId === selectedFriend?.friendUserId ? 'is-active' : ''}
                                           onClick={() => setSelectedFriendId(friend.friendUserId)}>
                                    <List.Item.Meta
                                        avatar={<Avatar src={friend.avatarUrl}>{avatarText(friend.displayName)}</Avatar>}
                                        description={friend.accountNo}
                                        title={friend.remark || friend.displayName}
                                    />
                                    {!friend.available && <Tag>不可用</Tag>}
                                </List.Item>
                            )}
                            rowKey="friendUserId"
                        />
                    )}
                    {tab === 'INCOMING' && (
                        <RequestList
                            busy={busy}
                            incoming
                            requests={props.incomingRequests}
                            onAccept={(id) => run(() => props.onAcceptRequest(id), '已接受好友申请')}
                            onReject={(id) => run(() => props.onRejectRequest(id), '已拒绝好友申请')}
                        />
                    )}
                    {tab === 'OUTGOING' && (
                        <RequestList
                            busy={busy}
                            requests={props.outgoingRequests}
                            onCancel={(id) => run(() => props.onCancelRequest(id), '已取消好友申请')}
                        />
                    )}
                </div>
            </aside>
            <section aria-label="联系人详情" className="platform-im-detail-pane platform-im-profile-pane">
                {selectedFriend ? (
                    <>
                        <div className="platform-im-profile-hero">
                            <Avatar size={72} src={selectedFriend.avatarUrl}>{avatarText(selectedFriend.displayName)}</Avatar>
                            <div>
                                <Typography.Title level={3}>{selectedFriend.remark || selectedFriend.displayName}</Typography.Title>
                                <Typography.Text type="secondary">
                                    {selectedFriend.displayName} · {selectedFriend.accountNo}
                                </Typography.Text>
                            </div>
                        </div>
                        <div className="platform-im-profile-form">
                            <Typography.Text strong>好友备注</Typography.Text>
                            <Input
                                maxLength={80}
                                placeholder="仅自己可见"
                                prefix={<EditOutlined/>}
                                value={remark}
                                onChange={(event) => setRemark(event.target.value)}
                            />
                            <Space wrap>
                                <Button
                                    icon={<SendOutlined/>}
                                    loading={busy}
                                    onClick={() => void run(() => props.onOpenDm(selectedFriend.friendUserId), '已打开私聊')}
                                    type="primary"
                                >
                                    发消息
                                </Button>
                                <Button
                                    disabled={remark === (selectedFriend.remark ?? '')}
                                    loading={busy}
                                    onClick={() => void run(
                                        () => props.onUpdateRemark(selectedFriend.friendUserId, remark),
                                        '备注已保存',
                                    )}
                                >
                                    保存备注
                                </Button>
                                <Popconfirm
                                    description="历史私聊仍会保留，但双方不能继续发送。"
                                    onConfirm={() => void run(
                                        () => props.onRemoveFriend(selectedFriend.friendUserId),
                                        '好友已删除',
                                    )}
                                    title="确认删除好友？"
                                >
                                    <Button danger icon={<DeleteOutlined/>} loading={busy}>删除好友</Button>
                                </Popconfirm>
                            </Space>
                        </div>
                    </>
                ) : (
                    <Empty description="选择一位好友查看详情" image={Empty.PRESENTED_IMAGE_SIMPLE}/>
                )}
            </section>
            <Modal
                footer={null}
                onCancel={() => setSearchOpen(false)}
                open={searchOpen}
                title="添加好友"
            >
                <Space className="platform-im-friend-search" orientation="vertical" size={12}>
                    <Input.Search
                        enterButton="查找"
                        maxLength={80}
                        placeholder="输入账号或昵称"
                        value={keyword}
                        onChange={(event) => setKeyword(event.target.value)}
                        onSearch={(value) => void props.onSearchUsers(value)}
                    />
                    <Input.TextArea
                        maxLength={200}
                        placeholder="申请说明（可选）"
                        value={requestMessage}
                        onChange={(event) => setRequestMessage(event.target.value)}
                    />
                    <List
                        dataSource={props.userResults}
                        locale={{emptyText: '输入关键词查找用户'}}
                        renderItem={(user) => (
                            <List.Item actions={[
                                <Button
                                    key="request"
                                    loading={busy}
                                    onClick={() => void run(async () => {
                                        await props.onRequestFriend({
                                            targetUserId: user.userId,
                                            message: requestMessage.trim() || undefined,
                                        })
                                    }, '好友申请已发送')}
                                    size="small"
                                    type="primary"
                                >
                                    添加
                                </Button>,
                            ]}>
                                <List.Item.Meta
                                    avatar={<Avatar src={user.avatarUrl}>{avatarText(user.displayName)}</Avatar>}
                                    description={user.accountNo}
                                    title={user.displayName}
                                />
                            </List.Item>
                        )}
                        rowKey="userId"
                    />
                </Space>
            </Modal>
        </>
    )
}

function RequestList(props: {
    busy: boolean
    incoming?: boolean
    requests: PlatformFriendRequestView[]
    onAccept?: (id: number) => Promise<void>
    onReject?: (id: number) => Promise<void>
    onCancel?: (id: number) => Promise<void>
}) {
    return (
        <List
            dataSource={props.requests}
            locale={{emptyText: <Empty description="暂无申请" image={Empty.PRESENTED_IMAGE_SIMPLE}/>}}
            renderItem={(request) => (
                <List.Item actions={requestActions(request, props)}>
                    <List.Item.Meta
                        avatar={<Avatar src={request.peerAvatarUrl}>{avatarText(request.peerDisplayName)}</Avatar>}
                        description={request.requestMessage || request.peerAccountNo}
                        title={request.peerDisplayName}
                    />
                    <Tag>{requestStatus(request.status)}</Tag>
                </List.Item>
            )}
            rowKey="id"
        />
    )
}

function requestActions(request: PlatformFriendRequestView, props: Parameters<typeof RequestList>[0]) {
    if (request.status !== 'PENDING') return []
    if (props.incoming) {
        return [
            <Button disabled={props.busy} key="accept" onClick={() => void props.onAccept?.(request.id)} size="small" type="primary">接受</Button>,
            <Button danger disabled={props.busy} key="reject" onClick={() => void props.onReject?.(request.id)} size="small">拒绝</Button>,
        ]
    }
    return [
        <Button disabled={props.busy} key="cancel" onClick={() => void props.onCancel?.(request.id)} size="small">取消</Button>,
    ]
}

function pendingCount(requests: PlatformFriendRequestView[]) {
    return requests.filter((request) => request.status === 'PENDING').length
}

function requestStatus(status: string) {
    if (status === 'PENDING') return '待处理'
    if (status === 'ACTIVE') return '已通过'
    if (status === 'REJECTED') return '已拒绝'
    if (status === 'CANCELLED') return '已取消'
    return status
}

function avatarText(name: string) {
    return name.trim().slice(0, 1).toUpperCase() || 'U'
}
