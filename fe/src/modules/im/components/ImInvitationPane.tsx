import {GlobalOutlined, ReloadOutlined, TeamOutlined} from '@ant-design/icons'
import {Alert, App, Avatar, Button, Empty, List, Space, Tag, Typography} from 'antd'
import {useState} from 'react'
import type {PlatformInvitationView} from '../platformImTypes'

export type ImInvitationPaneProps = {
    invitations: PlatformInvitationView[]
    onRefresh: () => Promise<PlatformInvitationView[]>
    onAccept: (invitationId: number) => Promise<void>
    onReject: (invitationId: number) => Promise<void>
}

export function ImInvitationPane(props: ImInvitationPaneProps) {
    const {message} = App.useApp()
    const [busyId, setBusyId] = useState<number>()
    const [error, setError] = useState<string>()

    async function decide(invitation: PlatformInvitationView, accept: boolean) {
        setBusyId(invitation.invitationId)
        setError(undefined)
        try {
            if (accept) {
                await props.onAccept(invitation.invitationId)
                void message.success(`已加入${invitation.surfaceName}`)
            } else {
                await props.onReject(invitation.invitationId)
                void message.success('已拒绝邀请')
            }
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : '邀请处理失败')
        } finally {
            setBusyId(undefined)
        }
    }

    return (
        <>
            <aside aria-label="邀请列表" className="platform-im-list-pane">
                <header className="platform-im-pane-header">
                    <div>
                        <Typography.Title level={4}>邀请</Typography.Title>
                        <Typography.Text type="secondary">频道与群组邀请</Typography.Text>
                    </div>
                    <Button aria-label="刷新邀请" icon={<ReloadOutlined/>} onClick={() => void props.onRefresh()}/>
                </header>
                <div className="platform-im-list-scroll">
                    <List
                        dataSource={props.invitations}
                        locale={{emptyText: <Empty description="暂无待处理邀请" image={Empty.PRESENTED_IMAGE_SIMPLE}/>}}
                        renderItem={(invitation) => (
                            <List.Item>
                                <List.Item.Meta
                                    avatar={<Avatar icon={invitation.surfaceType === 'CHANNEL'
                                        ? <GlobalOutlined/>
                                        : <TeamOutlined/>}/>}
                                    description={`来自 ${invitation.inviterDisplayName}`}
                                    title={invitation.surfaceName}
                                />
                                <Tag>{invitation.surfaceType === 'CHANNEL' ? '频道' : '群组'}</Tag>
                            </List.Item>
                        )}
                        rowKey="invitationId"
                    />
                </div>
            </aside>
            <section aria-label="邀请详情" className="platform-im-detail-pane platform-im-group-detail">
                {error && <Alert closable message={error} onClose={() => setError(undefined)} showIcon type="error"/>}
                {props.invitations.length === 0 ? (
                    <Empty description="所有邀请都已处理" image={Empty.PRESENTED_IMAGE_SIMPLE}/>
                ) : (
                    <List
                        dataSource={props.invitations}
                        renderItem={(invitation) => (
                            <List.Item actions={[
                                <Button
                                    disabled={busyId === invitation.invitationId}
                                    key="reject"
                                    onClick={() => void decide(invitation, false)}
                                >
                                    拒绝
                                </Button>,
                                <Button
                                    loading={busyId === invitation.invitationId}
                                    key="accept"
                                    onClick={() => void decide(invitation, true)}
                                    type="primary"
                                >
                                    接受邀请
                                </Button>,
                            ]}>
                                <List.Item.Meta
                                    avatar={<Avatar icon={invitation.surfaceType === 'CHANNEL'
                                        ? <GlobalOutlined/>
                                        : <TeamOutlined/>} size={48}/>}
                                    description={(
                                        <Space orientation="vertical" size={4}>
                                            <Typography.Text>邀请人：{invitation.inviterDisplayName}</Typography.Text>
                                            {invitation.channelId && <Typography.Text type="secondary">该群组属于频道 #{invitation.channelId}</Typography.Text>}
                                            {invitation.message && <Typography.Text type="secondary">{invitation.message}</Typography.Text>}
                                        </Space>
                                    )}
                                    title={invitation.surfaceName}
                                />
                            </List.Item>
                        )}
                        rowKey="invitationId"
                    />
                )}
            </section>
        </>
    )
}
