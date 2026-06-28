import {ReloadOutlined} from '@ant-design/icons'
import {Badge, Button, Empty, List, Space, Spin, Tag} from 'antd'
import type {ChannelView, GroupView, ImJoinPolicy, ImMembershipStatus, ImSurfaceType} from '../imTypes'

type SurfaceIdentity = {
    surfaceType: ImSurfaceType
    surfaceId: number
}

type SurfaceItem = {
    id: number
    surfaceType: ImSurfaceType
    name: string
    joinPolicy: ImJoinPolicy
    memberCount?: number | null
    unreadCount?: number | null
    membershipStatus?: ImMembershipStatus | null
    announcement?: string | null
    channel?: ChannelView
    group?: GroupView
}

export type ImSurfaceBrowserProps = {
    channels: ChannelView[]
    groups: GroupView[]
    loading: boolean
    activeSurface?: SurfaceIdentity
    onSelectSurface: (surface: SurfaceIdentity) => void
    onRefresh: () => void
}

export function ImSurfaceBrowser(props: ImSurfaceBrowserProps) {
    const surfaces = [
        ...props.channels.map(channelSurfaceItem),
        ...props.groups.map(groupSurfaceItem),
    ]

    if (props.loading) {
        return (
            <div aria-busy="true" aria-label="Loading channels and groups" style={{padding: 16, textAlign: 'center'}}>
                <Spin description="Loading channels and groups"/>
            </div>
        )
    }

    return (
        <List
            dataSource={surfaces}
            header={(
                <div style={{alignItems: 'center', display: 'flex', justifyContent: 'space-between', gap: 12}}>
                    <strong>Channels and groups</strong>
                    <Button icon={<ReloadOutlined/>} onClick={props.onRefresh} size="small">
                        Refresh
                    </Button>
                </div>
            )}
            locale={{
                emptyText: <Empty description="No channels or groups yet" image={Empty.PRESENTED_IMAGE_SIMPLE}/>,
            }}
            renderItem={(item) => (
                <List.Item
                    actions={[
                        <Button
                            key="select"
                            onClick={() => props.onSelectSurface({surfaceType: item.surfaceType, surfaceId: item.id})}
                            size="small"
                            type={isActiveSurface(item, props.activeSurface) ? 'primary' : 'default'}
                        >
                            Select
                        </Button>,
                    ]}
                >
                    <List.Item.Meta
                        description={(
                            <Space orientation="vertical" size={4}>
                                <Space size={4} wrap>
                                    <Tag color={item.surfaceType === 'CHANNEL' ? 'blue' : 'geekblue'}>
                                        {surfaceTypeLabel(item.surfaceType)}
                                    </Tag>
                                    <Tag>{joinPolicyLabel(item.joinPolicy)}</Tag>
                                    {item.membershipStatus && <Tag>{membershipStatusLabel(item.membershipStatus)}</Tag>}
                                    <span>{memberCountText(item.memberCount)}</span>
                                </Space>
                                {item.announcement && <span>{item.announcement}</span>}
                            </Space>
                        )}
                        title={(
                            <Space size={8}>
                                <span>{item.name}</span>
                                <Badge count={item.unreadCount ?? 0} overflowCount={99}/>
                            </Space>
                        )}
                    />
                </List.Item>
            )}
            rowKey={(item) => `${item.surfaceType}-${item.id}`}
        />
    )
}

function channelSurfaceItem(channel: ChannelView): SurfaceItem {
    return {
        id: channel.id,
        surfaceType: 'CHANNEL',
        name: channel.name,
        joinPolicy: channel.joinPolicy,
        memberCount: channel.memberCount,
        unreadCount: channel.unreadCount,
        membershipStatus: channel.membershipStatus,
        announcement: channel.announcement,
        channel,
    }
}

function groupSurfaceItem(group: GroupView): SurfaceItem {
    return {
        id: group.id,
        surfaceType: 'GROUP',
        name: group.name,
        joinPolicy: group.joinPolicy,
        memberCount: group.memberCount,
        unreadCount: group.unreadCount,
        membershipStatus: group.membershipStatus,
        announcement: group.announcement,
        group,
    }
}

function isActiveSurface(item: SurfaceItem, activeSurface?: SurfaceIdentity) {
    return activeSurface?.surfaceType === item.surfaceType && activeSurface.surfaceId === item.id
}

function surfaceTypeLabel(surfaceType: ImSurfaceType) {
    return surfaceType === 'CHANNEL' ? 'Channel' : 'Group'
}

function joinPolicyLabel(joinPolicy: ImJoinPolicy) {
    switch (joinPolicy) {
        case 'OPEN':
            return 'Open'
        case 'APPROVAL':
            return 'Approval'
        case 'INVITE_ONLY':
            return 'Invite only'
        default:
            return joinPolicy
    }
}

function membershipStatusLabel(status: ImMembershipStatus) {
    switch (status) {
        case 'PENDING':
            return 'Pending'
        case 'ACTIVE':
            return 'Active'
        case 'LEFT':
            return 'Left'
        case 'BANNED':
            return 'Banned'
        default:
            return status
    }
}

function memberCountText(memberCount?: number | null) {
    const count = memberCount ?? 0
    return `${count} ${count === 1 ? 'member' : 'members'}`
}
