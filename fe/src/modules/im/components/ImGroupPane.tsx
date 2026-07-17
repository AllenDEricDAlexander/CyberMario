import {PlusOutlined, ReloadOutlined, TeamOutlined} from '@ant-design/icons'
import {Alert, App, Avatar, Button, Empty, Input, List, Modal, Space, Tag, Typography} from 'antd'
import {useState} from 'react'
import type {ImSurfaceType} from '../imTypes'
import type {
    PlatformGroupView,
    PlatformJoinRequestView,
    PlatformSurfaceCreateRequest,
    PlatformSurfaceInvitationRequest,
    PlatformSurfaceMemberView,
    PlatformUserView,
} from '../platformImTypes'
import {ImSurfaceManagement} from './ImSurfaceManagement'

export type ImGroupPaneProps = {
    currentUser?: PlatformUserView
    groups: PlatformGroupView[]
    members: PlatformSurfaceMemberView[]
    joinRequests: PlatformJoinRequestView[]
    userResults: PlatformUserView[]
    onRefresh: () => Promise<void>
    onCreate: (request: PlatformSurfaceCreateRequest) => Promise<PlatformGroupView>
    onLeave: (surfaceType: ImSurfaceType, surfaceId: number) => Promise<void>
    onOpenConversation: (conversationId: number) => Promise<void>
    onSearchUsers: (keyword: string) => Promise<void>
    onInvite: (
        surfaceType: ImSurfaceType,
        surfaceId: number,
        request: PlatformSurfaceInvitationRequest,
    ) => Promise<void>
    onLoadManagement: (surfaceType: ImSurfaceType, surfaceId: number) => Promise<void>
    onApprove: (surfaceType: ImSurfaceType, surfaceId: number, id: number) => Promise<void>
    onReject: (surfaceType: ImSurfaceType, surfaceId: number, id: number) => Promise<void>
    onRemoveMember: (surfaceType: ImSurfaceType, surfaceId: number, userId: number) => Promise<void>
    onTransferOwnership: (surfaceType: ImSurfaceType, surfaceId: number, userId: number) => Promise<void>
}

export function ImGroupPane(props: ImGroupPaneProps) {
    const {message} = App.useApp()
    const [selectedGroupId, setSelectedGroupId] = useState<number>()
    const [createOpen, setCreateOpen] = useState(false)
    const [name, setName] = useState('')
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState<string>()
    const selectedGroup = props.groups.find((group) => group.id === selectedGroupId) ?? props.groups[0]

    async function create() {
        const normalized = name.trim()
        if (!normalized) return
        setBusy(true)
        setError(undefined)
        try {
            const group = await props.onCreate({name: normalized})
            setSelectedGroupId(group.id)
            setName('')
            setCreateOpen(false)
            void message.success('独立群组已创建')
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : '群组创建失败')
        } finally {
            setBusy(false)
        }
    }

    return (
        <>
            <aside aria-label="群组列表" className="platform-im-list-pane">
                <header className="platform-im-pane-header">
                    <div>
                        <Typography.Title level={4}>独立群组</Typography.Title>
                        <Typography.Text type="secondary">仅显示你已加入的独立群组</Typography.Text>
                    </div>
                    <Space.Compact>
                        <Button aria-label="创建独立群组" icon={<PlusOutlined/>} onClick={() => setCreateOpen(true)}/>
                        <Button aria-label="刷新群组" icon={<ReloadOutlined/>} onClick={() => void props.onRefresh()}/>
                    </Space.Compact>
                </header>
                {error && <Alert closable message={error} onClose={() => setError(undefined)} showIcon type="error"/>}
                <div className="platform-im-list-scroll">
                    <List
                        dataSource={props.groups}
                        locale={{emptyText: <Empty description="暂无独立群组，可自行创建" image={Empty.PRESENTED_IMAGE_SIMPLE}/>}}
                        renderItem={(group) => (
                            <List.Item
                                className={group.id === selectedGroup?.id ? 'is-active' : ''}
                                onClick={() => setSelectedGroupId(group.id)}
                            >
                                <List.Item.Meta
                                    avatar={<Avatar icon={<TeamOutlined/>}/>}
                                    description={`${group.memberCount ?? 0} 位成员`}
                                    title={group.name}
                                />
                                {group.memberRole && <Tag>{group.memberRole}</Tag>}
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
                                    <Tag>邀请制独立群组</Tag>
                                    <Tag>{selectedGroup.memberCount ?? 0} 位成员</Tag>
                                    {selectedGroup.memberRole && <Tag color="blue">{selectedGroup.memberRole}</Tag>}
                                </Space>
                            </div>
                            <Space>
                                {selectedGroup.conversationId && (
                                    <Button onClick={() => void props.onOpenConversation(selectedGroup.conversationId!)} type="primary">
                                        进入群聊
                                    </Button>
                                )}
                                {selectedGroup.memberRole !== 'OWNER' && (
                                    <Button danger onClick={() => void props.onLeave('GROUP', selectedGroup.id)}>退出群组</Button>
                                )}
                            </Space>
                        </header>
                        {selectedGroup.announcement && <Alert message={selectedGroup.announcement} showIcon type="info"/>}
                        <ImSurfaceManagement
                            currentUser={props.currentUser}
                            joinRequests={props.joinRequests}
                            memberRole={selectedGroup.memberRole}
                            members={props.members}
                            surfaceId={selectedGroup.id}
                            surfaceType="GROUP"
                            userResults={props.userResults}
                            onApprove={props.onApprove}
                            onInvite={props.onInvite}
                            onLoad={props.onLoadManagement}
                            onReject={props.onReject}
                            onRemoveMember={props.onRemoveMember}
                            onSearchUsers={props.onSearchUsers}
                            onTransferOwnership={props.onTransferOwnership}
                        />
                    </>
                ) : (
                    <Empty description="创建或接受邀请后，独立群组会显示在这里" image={Empty.PRESENTED_IMAGE_SIMPLE}/>
                )}
            </section>
            <Modal
                confirmLoading={busy}
                okButtonProps={{disabled: !name.trim()}}
                okText="创建"
                onCancel={() => setCreateOpen(false)}
                onOk={() => void create()}
                open={createOpen}
                title="创建独立群组"
            >
                <Space className="platform-im-create-group" orientation="vertical" size={14}>
                    <Alert message="独立群组不会出现在搜索或发现列表中，只能由所有者或管理员邀请加入。" showIcon type="info"/>
                    <Input maxLength={80} placeholder="群组名称" value={name}
                           onChange={(event) => setName(event.target.value)}/>
                </Space>
            </Modal>
        </>
    )
}
