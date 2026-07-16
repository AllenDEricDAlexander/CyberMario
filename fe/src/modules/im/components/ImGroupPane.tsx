import {PlusOutlined, ReloadOutlined, TeamOutlined} from '@ant-design/icons'
import {Alert, App, Avatar, Button, Empty, Input, List, Modal, Popconfirm, Select, Space, Tag, Typography} from 'antd'
import {useEffect, useState} from 'react'
import type {ImJoinPolicy, ImSurfaceType, JoinRequestCreateRequest, JoinResultView} from '../imTypes'
import type {
    PlatformGroupCreateRequest,
    PlatformGroupView,
    PlatformJoinRequestView,
    PlatformSurfaceMemberView,
    PlatformUserView,
} from '../platformImTypes'
import {ImJoinApplyControls} from './ImJoinApplyControls'

export type ImGroupPaneProps = {
    currentUser?: PlatformUserView
    groups: PlatformGroupView[]
    members: PlatformSurfaceMemberView[]
    joinRequests: PlatformJoinRequestView[]
    onRefresh: () => Promise<void>
    onCreate: (request: PlatformGroupCreateRequest) => Promise<void>
    onApply: (request: JoinRequestCreateRequest) => Promise<JoinResultView>
    onCancel: (id: number) => Promise<JoinResultView>
    onLeave: (surfaceType: ImSurfaceType, surfaceId: number) => Promise<void>
    onLoadManagement: (surfaceType: ImSurfaceType, surfaceId: number) => Promise<void>
    onApprove: (surfaceType: ImSurfaceType, surfaceId: number, id: number) => Promise<void>
    onReject: (surfaceType: ImSurfaceType, surfaceId: number, id: number) => Promise<void>
    onRemoveMember: (surfaceType: ImSurfaceType, surfaceId: number, userId: number) => Promise<void>
    onOpenConversation: (conversationId: number) => Promise<void>
}

export function ImGroupPane(props: ImGroupPaneProps) {
    const {message} = App.useApp()
    const {onLoadManagement} = props
    const [selectedGroupId, setSelectedGroupId] = useState<number>()
    const [createOpen, setCreateOpen] = useState(false)
    const [name, setName] = useState('')
    const [joinPolicy, setJoinPolicy] = useState<ImJoinPolicy>('OPEN')
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState<string>()
    const selectedGroup = props.groups.find((group) => group.id === selectedGroupId) ?? props.groups[0]
    const selectedConversationId = selectedGroup?.conversationId
    const reviewer = selectedGroup?.memberRole === 'OWNER' || selectedGroup?.memberRole === 'ADMIN'

    useEffect(() => {
        if (selectedGroup && reviewer) {
            void onLoadManagement('GROUP', selectedGroup.id)
        }
    }, [onLoadManagement, reviewer, selectedGroup])

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

    async function create() {
        const normalized = name.trim()
        if (!normalized) return
        await run(async () => {
            await props.onCreate({
                groupKey: createGroupKey(normalized),
                name: normalized,
                joinPolicy,
            })
            setName('')
            setJoinPolicy('OPEN')
            setCreateOpen(false)
        }, '群组已创建')
    }

    return (
        <>
            <aside aria-label="群组列表" className="platform-im-list-pane">
                <header className="platform-im-pane-header">
                    <div>
                        <Typography.Title level={4}>群组</Typography.Title>
                        <Typography.Text type="secondary">发现与管理平台群组</Typography.Text>
                    </div>
                    <Space.Compact>
                        <Button aria-label="创建群组" icon={<PlusOutlined/>} onClick={() => setCreateOpen(true)}/>
                        <Button aria-label="刷新群组" icon={<ReloadOutlined/>} onClick={() => void props.onRefresh()}/>
                    </Space.Compact>
                </header>
                {error && <Alert closable message={error} onClose={() => setError(undefined)} showIcon type="error"/>}
                <div className="platform-im-list-scroll">
                    <List
                        dataSource={props.groups}
                        locale={{emptyText: <Empty description="暂无群组" image={Empty.PRESENTED_IMAGE_SIMPLE}/>}}
                        renderItem={(group) => (
                            <List.Item className={group.id === selectedGroup?.id ? 'is-active' : ''}
                                       onClick={() => setSelectedGroupId(group.id)}>
                                <List.Item.Meta
                                    avatar={(
                                        <Avatar icon={<TeamOutlined/>}/>
                                    )}
                                    description={`${policyLabel(group.joinPolicy)} · ${group.memberCount ?? 0} 人`}
                                    title={group.name}
                                />
                                {group.membershipStatus && <Tag>{membershipLabel(group.membershipStatus)}</Tag>}
                            </List.Item>
                        )}
                        rowKey="id"
                    />
                </div>
            </aside>
            <section aria-label="群组详情" className="platform-im-detail-pane platform-im-group-detail">
                {selectedGroup ? (
                    <>
                        <header className="platform-im-group-header">
                            <Avatar icon={<TeamOutlined/>} size={56}/>
                            <div>
                                <Typography.Title level={3}>{selectedGroup.name}</Typography.Title>
                                <Space size={6} wrap>
                                    <Tag>{policyLabel(selectedGroup.joinPolicy)}</Tag>
                                    <Tag>{selectedGroup.memberCount ?? 0} 位成员</Tag>
                                    {selectedGroup.memberRole && <Tag color="blue">{selectedGroup.memberRole}</Tag>}
                                </Space>
                            </div>
                            {selectedConversationId && selectedGroup.membershipStatus === 'ACTIVE' && (
                                <Button
                                    onClick={() => void props.onOpenConversation(selectedConversationId)}
                                    type="primary"
                                >
                                    进入会话
                                </Button>
                            )}
                        </header>
                        {selectedGroup.announcement && (
                            <Alert message={selectedGroup.announcement} showIcon type="info"/>
                        )}
                        <div className="platform-im-group-membership">
                            <ImJoinApplyControls
                                currentMemberRole={selectedGroup.memberRole}
                                currentMembershipStatus={selectedGroup.membershipStatus}
                                joinPolicy={selectedGroup.joinPolicy}
                                pendingReviewRequests={props.joinRequests.map((request) => ({
                                    requestId: request.joinRequestId,
                                    userLabel: `${request.displayName} (${request.accountNo})`,
                                }))}
                                surface={{surfaceType: 'GROUP', surfaceId: selectedGroup.id}}
                                onApply={(surface) => props.onApply({
                                    surfaceType: surface.surfaceType,
                                    surfaceId: surface.surfaceId,
                                })}
                                onApprove={(id) => props.onApprove('GROUP', selectedGroup.id, id)}
                                onCancel={props.onCancel}
                                onLeave={(surface) => props.onLeave(surface.surfaceType, surface.surfaceId)}
                                onReject={(id) => props.onReject('GROUP', selectedGroup.id, id)}
                            />
                        </div>
                        {reviewer && (
                            <div className="platform-im-group-management">
                                <Typography.Title level={5}>成员管理</Typography.Title>
                                <List
                                    dataSource={props.members}
                                    locale={{emptyText: '暂无成员'}}
                                    renderItem={(member) => (
                                        <List.Item actions={canRemoveMember(member, selectedGroup, props.currentUser)
                                            ? [
                                                <Popconfirm
                                                    key="remove"
                                                    onConfirm={() => void run(
                                                        () => props.onRemoveMember('GROUP', selectedGroup.id, member.userId),
                                                        '成员已移除',
                                                    )}
                                                    title="确认移除该成员？"
                                                >
                                                    <Button danger disabled={busy} size="small">移除</Button>
                                                </Popconfirm>,
                                            ]
                                            : []}>
                                            <List.Item.Meta
                                                avatar={<Avatar src={member.avatarUrl}>{avatarText(member.displayName)}</Avatar>}
                                                description={member.accountNo}
                                                title={member.displayName}
                                            />
                                            <Tag>{member.memberRole}</Tag>
                                        </List.Item>
                                    )}
                                    rowKey="membershipId"
                                    size="small"
                                />
                            </div>
                        )}
                    </>
                ) : (
                    <Empty description="选择一个群组查看详情" image={Empty.PRESENTED_IMAGE_SIMPLE}/>
                )}
            </section>
            <Modal
                confirmLoading={busy}
                okButtonProps={{disabled: !name.trim()}}
                okText="创建"
                onCancel={() => setCreateOpen(false)}
                onOk={() => void create()}
                open={createOpen}
                title="创建群组"
            >
                <Space className="platform-im-create-group" orientation="vertical" size={14}>
                    <Input
                        maxLength={80}
                        placeholder="群组名称"
                        value={name}
                        onChange={(event) => setName(event.target.value)}
                    />
                    <Select<ImJoinPolicy>
                        aria-label="加入方式"
                        options={[
                            {label: '开放加入', value: 'OPEN'},
                            {label: '需要审批', value: 'APPROVAL'},
                        ]}
                        value={joinPolicy}
                        onChange={setJoinPolicy}
                    />
                </Space>
            </Modal>
        </>
    )
}

export function createGroupKey(name: string) {
    const normalized = name.toLowerCase()
        .replace(/[^a-z0-9\u4e00-\u9fff]+/g, '-')
        .replace(/^-|-$/g, '')
        .slice(0, 32) || 'group'
    const suffix = globalThis.crypto?.randomUUID?.().slice(0, 8) ?? Date.now().toString(36)
    return `${normalized}-${suffix}`
}

function canRemoveMember(
    member: PlatformSurfaceMemberView,
    group: PlatformGroupView,
    currentUser?: PlatformUserView,
) {
    return member.userId !== currentUser?.userId && member.memberRole !== 'OWNER' && group.memberRole !== 'MEMBER'
}

function policyLabel(policy: ImJoinPolicy) {
    return policy === 'OPEN' ? '开放加入' : '需要审批'
}

function membershipLabel(status: string) {
    if (status === 'ACTIVE') return '已加入'
    if (status === 'PENDING') return '待审批'
    if (status === 'LEFT') return '未加入'
    if (status === 'BANNED') return '已禁止'
    return status
}

function avatarText(name: string) {
    return name.trim().slice(0, 1).toUpperCase() || 'U'
}
