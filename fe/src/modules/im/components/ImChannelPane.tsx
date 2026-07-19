import {GlobalOutlined, KeyOutlined, PlusOutlined, ReloadOutlined, TeamOutlined} from '@ant-design/icons'
import {Alert, App, Avatar, Button, Empty, Input, List, Modal, Select, Space, Tag, Typography} from 'antd'
import {useEffect, useState} from 'react'
import type {ImJoinPolicy, ImSurfaceType, JoinRequestCreateRequest, JoinResultView} from '../imTypes'
import type {
    PlatformChannelGroupCreateRequest,
    PlatformChannelView,
    PlatformGroupView,
    PlatformJoinRequestView,
    PlatformSurfaceCreateRequest,
    PlatformSurfaceInvitationRequest,
    PlatformSurfaceMemberView,
    PlatformUserView,
} from '../platformImTypes'
import {ImJoinApplyControls} from './ImJoinApplyControls'
import {ImSurfaceManagement} from './ImSurfaceManagement'

export type ImChannelPaneProps = {
    currentUser?: PlatformUserView
    channels: PlatformChannelView[]
    members: PlatformSurfaceMemberView[]
    joinRequests: PlatformJoinRequestView[]
    userResults: PlatformUserView[]
    onRefresh: () => Promise<PlatformChannelView[]>
    onCreate: (request: PlatformSurfaceCreateRequest) => Promise<PlatformChannelView>
    onListGroups: (channelId: number) => Promise<PlatformGroupView[]>
    onCreateGroup: (channelId: number, request: PlatformChannelGroupCreateRequest) => Promise<PlatformGroupView>
    onOpenConversation: (conversationId: number) => Promise<void>
    onSearchUsers: (keyword: string) => Promise<void>
    onInvite: (
        surfaceType: ImSurfaceType,
        surfaceId: number,
        request: PlatformSurfaceInvitationRequest,
    ) => Promise<void>
    onApply: (request: JoinRequestCreateRequest) => Promise<JoinResultView>
    onCancel: (id: number) => Promise<JoinResultView>
    onLeave: (surfaceType: ImSurfaceType, surfaceId: number) => Promise<void>
    onLoadManagement: (surfaceType: ImSurfaceType, surfaceId: number) => Promise<void>
    onApprove: (surfaceType: ImSurfaceType, surfaceId: number, id: number) => Promise<void>
    onReject: (surfaceType: ImSurfaceType, surfaceId: number, id: number) => Promise<void>
    onRemoveMember: (surfaceType: ImSurfaceType, surfaceId: number, userId: number) => Promise<void>
    onTransferOwnership: (surfaceType: ImSurfaceType, surfaceId: number, userId: number) => Promise<void>
}

export function ImChannelPane(props: ImChannelPaneProps) {
    const {message} = App.useApp()
    const [selectedChannelId, setSelectedChannelId] = useState<number>()
    const [selectedGroupId, setSelectedGroupId] = useState<number>()
    const [groups, setGroups] = useState<PlatformGroupView[]>([])
    const [createChannelOpen, setCreateChannelOpen] = useState(false)
    const [createGroupOpen, setCreateGroupOpen] = useState(false)
    const [joinOpen, setJoinOpen] = useState(false)
    const [surfaceJoinKey, setSurfaceJoinKey] = useState('')
    const [name, setName] = useState('')
    const [joinPolicy, setJoinPolicy] = useState<ImJoinPolicy>('OPEN')
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState<string>()
    const selectedChannel = props.channels.find((channel) => channel.id === selectedChannelId) ?? props.channels[0]
    const selectedGroup = groups.find((group) => group.id === selectedGroupId)
    const managedSurface = selectedGroup ?? selectedChannel
    const channelManager = selectedChannel?.memberRole === 'OWNER' || selectedChannel?.memberRole === 'ADMIN'
    const activeChannelId = selectedChannel?.id
    const {onListGroups} = props

    useEffect(() => {
        if (!activeChannelId) {
            setGroups([])
            return
        }
        setSelectedGroupId(undefined)
        void onListGroups(activeChannelId)
            .then(setGroups)
            .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : '频道群组加载失败'))
    }, [activeChannelId, onListGroups])

    async function loadGroups(channelId: number) {
        try {
            setGroups(await props.onListGroups(channelId))
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : '频道群组加载失败')
        }
    }

    async function run<T>(action: () => Promise<T>, success: string) {
        setBusy(true)
        setError(undefined)
        try {
            const value = await action()
            void message.success(success)
            return value
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : '操作失败，请稍后重试')
            return undefined
        } finally {
            setBusy(false)
        }
    }

    async function createChannel() {
        const normalized = name.trim()
        if (!normalized) return
        const channel = await run(() => props.onCreate({name: normalized}), '频道已创建')
        if (channel) {
            setSelectedChannelId(channel.id)
            setName('')
            setCreateChannelOpen(false)
        }
    }

    async function createGroup() {
        const normalized = name.trim()
        if (!normalized || !selectedChannel) return
        const group = await run(
            () => props.onCreateGroup(selectedChannel.id, {name: normalized, joinPolicy}),
            '子群组已创建',
        )
        if (group) {
            await loadGroups(selectedChannel.id)
            setSelectedGroupId(group.id)
            setName('')
            setJoinPolicy('OPEN')
            setCreateGroupOpen(false)
        }
    }

    async function joinChannel() {
        const joinKey = surfaceJoinKey.trim()
        if (!joinKey) return
        const result = await run(() => props.onApply({joinKey}), '加入请求已提交')
        if (result) {
            setSurfaceJoinKey('')
            setJoinOpen(false)
        }
    }

    return (
        <>
            <aside aria-label="频道列表" className="platform-im-list-pane">
                <header className="platform-im-pane-header">
                    <div>
                        <Typography.Title level={4}>频道</Typography.Title>
                        <Typography.Text type="secondary">仅显示你已加入的频道</Typography.Text>
                    </div>
                    <Space.Compact>
                        <Button aria-label="使用 Key 加入频道" icon={<KeyOutlined/>} onClick={() => setJoinOpen(true)}/>
                        <Button aria-label="创建频道" icon={<PlusOutlined/>} onClick={() => setCreateChannelOpen(true)}/>
                        <Button aria-label="刷新频道" icon={<ReloadOutlined/>} onClick={() => void props.onRefresh()}/>
                    </Space.Compact>
                </header>
                <div className="platform-im-list-scroll">
                    <List
                        dataSource={props.channels}
                        locale={{emptyText: <Empty description="暂无频道，可自行创建" image={Empty.PRESENTED_IMAGE_SIMPLE}/>}}
                        renderItem={(channel) => (
                            <List.Item
                                className={channel.id === selectedChannel?.id ? 'is-active' : ''}
                                onClick={() => setSelectedChannelId(channel.id)}
                            >
                                <List.Item.Meta
                                    avatar={<Avatar icon={<GlobalOutlined/>}/>}
                                    description={`${channel.memberCount ?? 0} 位成员`}
                                    title={channel.name}
                                />
                                {channel.memberRole && <Tag>{channel.memberRole}</Tag>}
                            </List.Item>
                        )}
                        rowKey="id"
                    />
                </div>
            </aside>
            <section aria-label="频道详情" className="platform-im-detail-pane platform-im-group-detail">
                {selectedChannel ? (
                    <>
                        <header className="platform-im-group-header">
                            <Avatar icon={<GlobalOutlined/>} size={56}/>
                            <div>
                                <Typography.Title level={3}>{selectedChannel.name}</Typography.Title>
                                <Space size={6} wrap>
                                    <Tag>{selectedChannel.memberCount ?? 0} 位频道成员</Tag>
                                    {selectedChannel.memberRole && <Tag color="blue">{selectedChannel.memberRole}</Tag>}
                                </Space>
                                <Typography.Text code copyable={{text: selectedChannel.joinKey}}>
                                    {selectedChannel.joinKey}
                                </Typography.Text>
                            </div>
                            <Space>
                                {selectedChannel.mainConversationId && (
                                    <Button onClick={() => void props.onOpenConversation(selectedChannel.mainConversationId!)} type="primary">
                                        进入频道
                                    </Button>
                                )}
                                <Button onClick={() => setSelectedGroupId(undefined)}>管理频道</Button>
                                {channelManager && (
                                    <Button icon={<PlusOutlined/>} onClick={() => setCreateGroupOpen(true)}>创建子群组</Button>
                                )}
                            </Space>
                        </header>
                        {error && <Alert closable message={error} onClose={() => setError(undefined)} showIcon type="error"/>}
                        <div className="platform-im-surface-grid">
                            <Typography.Title level={5}>频道下的群组</Typography.Title>
                            <List
                                dataSource={groups}
                                grid={{gutter: 12, column: 2}}
                                locale={{emptyText: '该频道下暂无群组'}}
                                renderItem={(group) => (
                                    <List.Item>
                                        <button
                                            className={`platform-im-surface-card${group.id === selectedGroupId ? ' is-active' : ''}`}
                                            onClick={() => setSelectedGroupId(group.id)}
                                            type="button"
                                        >
                                            <TeamOutlined/>
                                            <span>{group.name}</span>
                                            <Tag>{policyLabel(group.joinPolicy)}</Tag>
                                            <Tag>{membershipLabel(group.membershipStatus)}</Tag>
                                        </button>
                                    </List.Item>
                                )}
                                rowKey="id"
                            />
                        </div>
                        {selectedGroup && (
                            <div className="platform-im-group-membership">
                                <header className="platform-im-section-header">
                                    <div>
                                        <Typography.Title level={5}>{selectedGroup.name}</Typography.Title>
                                        <Typography.Text type="secondary">仅频道成员可加入此群组</Typography.Text>
                                        <Typography.Text code copyable={{text: selectedGroup.joinKey}}>
                                            {selectedGroup.joinKey}
                                        </Typography.Text>
                                    </div>
                                    {selectedGroup.conversationId && selectedGroup.membershipStatus === 'ACTIVE' && (
                                        <Button onClick={() => void props.onOpenConversation(selectedGroup.conversationId!)} type="primary">
                                            进入群聊
                                        </Button>
                                    )}
                                </header>
                                <ImJoinApplyControls
                                    currentMemberRole={selectedGroup.memberRole}
                                    currentMembershipStatus={selectedGroup.membershipStatus}
                                    joinPolicy={selectedGroup.joinPolicy}
                                    pendingReviewRequests={props.joinRequests.map((request) => ({
                                        requestId: request.joinRequestId,
                                        userLabel: `${request.displayName} (${request.accountNo})`,
                                    }))}
                                    surface={{
                                        surfaceType: 'GROUP',
                                        surfaceId: selectedGroup.id,
                                        joinKey: selectedGroup.joinKey,
                                    }}
                                    onApply={async (surface) => {
                                        const result = await props.onApply({joinKey: surface.joinKey})
                                        await loadGroups(selectedChannel.id)
                                        return result
                                    }}
                                    onApprove={(id) => props.onApprove('GROUP', selectedGroup.id, id)}
                                    onCancel={async (id) => {
                                        const result = await props.onCancel(id)
                                        await loadGroups(selectedChannel.id)
                                        return result
                                    }}
                                    onLeave={async (surface) => {
                                        await props.onLeave(surface.surfaceType, surface.surfaceId)
                                        await loadGroups(selectedChannel.id)
                                    }}
                                    onReject={(id) => props.onReject('GROUP', selectedGroup.id, id)}
                                />
                            </div>
                        )}
                        {managedSurface && (
                            <ImSurfaceManagement
                                childGroup={Boolean(selectedGroup)}
                                currentUser={props.currentUser}
                                joinRequests={props.joinRequests}
                                memberRole={managedSurface.memberRole}
                                members={props.members}
                                surfaceId={managedSurface.id}
                                surfaceType={selectedGroup ? 'GROUP' : 'CHANNEL'}
                                userResults={props.userResults}
                                onApprove={props.onApprove}
                                onInvite={props.onInvite}
                                onLoad={props.onLoadManagement}
                                onReject={props.onReject}
                                onRemoveMember={props.onRemoveMember}
                                onSearchUsers={props.onSearchUsers}
                                onTransferOwnership={props.onTransferOwnership}
                            />
                        )}
                    </>
                ) : (
                    <Empty description="创建或接受邀请后，频道会显示在这里" image={Empty.PRESENTED_IMAGE_SIMPLE}/>
                )}
            </section>
            <SurfaceCreateModal
                busy={busy}
                name={name}
                open={createChannelOpen}
                title="创建频道"
                onCancel={() => setCreateChannelOpen(false)}
                onChange={setName}
                onCreate={() => void createChannel()}
            />
            <SurfaceCreateModal
                busy={busy}
                joinPolicy={joinPolicy}
                name={name}
                open={createGroupOpen}
                title="在频道下创建群组"
                onCancel={() => setCreateGroupOpen(false)}
                onChange={setName}
                onCreate={() => void createGroup()}
                onJoinPolicyChange={setJoinPolicy}
            />
            <Modal
                confirmLoading={busy}
                okButtonProps={{disabled: !surfaceJoinKey.trim()}}
                okText="加入"
                onCancel={() => setJoinOpen(false)}
                onOk={() => void joinChannel()}
                open={joinOpen}
                title="使用唯一 Key 加入频道"
            >
                <Input
                    aria-label="频道唯一 Key"
                    maxLength={32}
                    placeholder="chn_..."
                    value={surfaceJoinKey}
                    onChange={(event) => setSurfaceJoinKey(event.target.value)}
                />
            </Modal>
        </>
    )
}

function SurfaceCreateModal(props: {
    open: boolean
    busy: boolean
    title: string
    name: string
    joinPolicy?: ImJoinPolicy
    onChange: (name: string) => void
    onJoinPolicyChange?: (policy: ImJoinPolicy) => void
    onCancel: () => void
    onCreate: () => void
}) {
    return (
        <Modal
            confirmLoading={props.busy}
            okButtonProps={{disabled: !props.name.trim()}}
            okText="创建"
            onCancel={props.onCancel}
            onOk={props.onCreate}
            open={props.open}
            title={props.title}
        >
            <Space className="platform-im-create-group" orientation="vertical" size={14}>
                <Input maxLength={80} placeholder="名称" value={props.name}
                       onChange={(event) => props.onChange(event.target.value)}/>
                {props.joinPolicy && props.onJoinPolicyChange && (
                    <Select<ImJoinPolicy>
                        aria-label="加入方式"
                        options={[
                            {label: '频道成员可直接加入', value: 'OPEN'},
                            {label: '频道成员申请后加入', value: 'APPROVAL'},
                        ]}
                        value={props.joinPolicy}
                        onChange={props.onJoinPolicyChange}
                    />
                )}
            </Space>
        </Modal>
    )
}

function policyLabel(policy: ImJoinPolicy) {
    return policy === 'OPEN' ? '开放' : '审批'
}

function membershipLabel(status?: string | null) {
    if (status === 'ACTIVE') return '已加入'
    if (status === 'PENDING') return '待审批'
    return '未加入'
}
