import {CrownOutlined, PlusOutlined} from '@ant-design/icons'
import {Alert, App, Avatar, Button, Input, List, Modal, Popconfirm, Space, Tag, Typography} from 'antd'
import {useEffect, useState} from 'react'
import type {ImSurfaceType} from '../imTypes'
import type {
    PlatformJoinRequestView,
    PlatformSurfaceInvitationRequest,
    PlatformSurfaceMemberView,
    PlatformUserView,
} from '../platformImTypes'

export type ImSurfaceManagementProps = {
    surfaceType: ImSurfaceType
    surfaceId: number
    memberRole?: string | null
    currentUser?: PlatformUserView
    members: PlatformSurfaceMemberView[]
    joinRequests: PlatformJoinRequestView[]
    userResults: PlatformUserView[]
    childGroup?: boolean
    onSearchUsers: (keyword: string) => Promise<void>
    onLoad: (surfaceType: ImSurfaceType, surfaceId: number) => Promise<void>
    onInvite: (
        surfaceType: ImSurfaceType,
        surfaceId: number,
        request: PlatformSurfaceInvitationRequest,
    ) => Promise<void>
    onTransferOwnership: (surfaceType: ImSurfaceType, surfaceId: number, newOwnerUserId: number) => Promise<void>
    onApprove: (surfaceType: ImSurfaceType, surfaceId: number, requestId: number) => Promise<void>
    onReject: (surfaceType: ImSurfaceType, surfaceId: number, requestId: number) => Promise<void>
    onRemoveMember: (surfaceType: ImSurfaceType, surfaceId: number, userId: number) => Promise<void>
}

export function ImSurfaceManagement(props: ImSurfaceManagementProps) {
    const {message} = App.useApp()
    const [inviteOpen, setInviteOpen] = useState(false)
    const [inviteMessage, setInviteMessage] = useState('')
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState<string>()
    const manager = props.memberRole === 'OWNER' || props.memberRole === 'ADMIN'
    const {onLoad, surfaceId, surfaceType} = props

    useEffect(() => {
        if (manager) {
            void onLoad(surfaceType, surfaceId)
        }
    }, [manager, onLoad, surfaceId, surfaceType])

    if (!manager) return null

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

    async function invite(userId: number) {
        await run(async () => {
            await props.onInvite(props.surfaceType, props.surfaceId, {
                inviteeUserId: userId,
                message: inviteMessage.trim() || undefined,
            })
            setInviteMessage('')
            setInviteOpen(false)
        }, '邀请已发送')
    }

    return (
        <div className="platform-im-group-management">
            <header className="platform-im-section-header">
                <div>
                    <Typography.Title level={5}>成员管理</Typography.Title>
                    <Typography.Text type="secondary">仅所有者和管理员可以直接邀请用户</Typography.Text>
                </div>
                <Button icon={<PlusOutlined/>} onClick={() => setInviteOpen(true)}>邀请成员</Button>
            </header>
            {error && <Alert closable message={error} onClose={() => setError(undefined)} showIcon type="error"/>}
            {props.joinRequests.length > 0 && (
                <List
                    dataSource={props.joinRequests.filter((request) => request.status === 'PENDING')}
                    header={<Typography.Text strong>待审批申请</Typography.Text>}
                    renderItem={(request) => (
                        <List.Item actions={[
                            <Button key="approve" onClick={() => void run(
                                () => props.onApprove(props.surfaceType, props.surfaceId, request.joinRequestId),
                                '已通过申请',
                            )} size="small" type="primary">通过</Button>,
                            <Button danger key="reject" onClick={() => void run(
                                () => props.onReject(props.surfaceType, props.surfaceId, request.joinRequestId),
                                '已拒绝申请',
                            )} size="small">拒绝</Button>,
                        ]}>
                            <List.Item.Meta description={request.accountNo} title={request.displayName}/>
                        </List.Item>
                    )}
                    rowKey="joinRequestId"
                    size="small"
                />
            )}
            <List
                dataSource={props.members}
                locale={{emptyText: '暂无成员'}}
                renderItem={(member) => (
                    <List.Item actions={memberActions(member, props, busy, run)}>
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
            <Modal footer={null} onCancel={() => setInviteOpen(false)} open={inviteOpen} title="邀请成员">
                <Space className="platform-im-create-group" orientation="vertical" size={12}>
                    {props.childGroup && (
                        <Alert message="子群组只能邀请已经加入父频道的用户。" showIcon type="info"/>
                    )}
                    <Input.Search
                        allowClear
                        enterButton="搜索用户"
                        placeholder="输入账号或昵称"
                        onSearch={(keyword) => void props.onSearchUsers(keyword)}
                    />
                    <Input.TextArea
                        maxLength={512}
                        placeholder="邀请说明（可选）"
                        value={inviteMessage}
                        onChange={(event) => setInviteMessage(event.target.value)}
                    />
                    <List
                        dataSource={props.userResults.filter((user) => user.userId !== props.currentUser?.userId)}
                        locale={{emptyText: '搜索用户后发起邀请'}}
                        renderItem={(user) => (
                            <List.Item actions={[
                                <Button disabled={busy} key="invite" onClick={() => void invite(user.userId)} type="primary">
                                    邀请
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
                        size="small"
                    />
                </Space>
            </Modal>
        </div>
    )
}

function memberActions(
    member: PlatformSurfaceMemberView,
    props: ImSurfaceManagementProps,
    busy: boolean,
    run: (action: () => Promise<void>, success: string) => Promise<void>,
) {
    if (member.userId === props.currentUser?.userId || member.memberRole === 'OWNER') return []
    const actions = []
    if (props.memberRole === 'OWNER') {
        actions.push(
            <Popconfirm
                key="owner"
                onConfirm={() => void run(
                    () => props.onTransferOwnership(props.surfaceType, props.surfaceId, member.userId),
                    '所有权已转移',
                )}
                title="确认将所有权转移给该成员？"
            >
                <Button disabled={busy} icon={<CrownOutlined/>} size="small">转让</Button>
            </Popconfirm>,
        )
    }
    actions.push(
        <Popconfirm
            key="remove"
            onConfirm={() => void run(
                () => props.onRemoveMember(props.surfaceType, props.surfaceId, member.userId),
                '成员已移除',
            )}
            title="确认移除该成员？"
        >
            <Button danger disabled={busy} size="small">移除</Button>
        </Popconfirm>,
    )
    return actions
}

function avatarText(name: string) {
    return name.trim().slice(0, 1).toUpperCase() || 'U'
}
