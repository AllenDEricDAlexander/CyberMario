import {
    ContactsOutlined,
    GlobalOutlined,
    MessageOutlined,
    TeamOutlined,
} from '@ant-design/icons'
import {Avatar, Badge, Button, Tooltip, Typography} from 'antd'
import type {PlatformImActivity, PlatformUserView} from '../platformImTypes'

export type ImActivityRailProps = {
    activity: PlatformImActivity
    currentUser?: PlatformUserView
    unreadTotal: number
    pendingFriendRequestCount: number
    onActivityChange: (activity: PlatformImActivity) => void
    onOpenPublicChannel: () => void
}

export function ImActivityRail(props: ImActivityRailProps) {
    return (
        <nav aria-label="即时通信功能" className="platform-im-activity-rail">
            <div className="platform-im-activity-brand" aria-hidden="true">IM</div>
            <RailButton
                active={props.activity === 'MESSAGES'}
                badge={props.unreadTotal}
                icon={<MessageOutlined/>}
                label="消息"
                onClick={() => props.onActivityChange('MESSAGES')}
            />
            <RailButton
                active={props.activity === 'FRIENDS'}
                badge={props.pendingFriendRequestCount}
                icon={<ContactsOutlined/>}
                label="联系人"
                onClick={() => props.onActivityChange('FRIENDS')}
            />
            <RailButton
                active={props.activity === 'GROUPS'}
                icon={<TeamOutlined/>}
                label="群组"
                onClick={() => props.onActivityChange('GROUPS')}
            />
            <RailButton
                active={false}
                icon={<GlobalOutlined/>}
                label="公共频道"
                onClick={props.onOpenPublicChannel}
            />
            <Tooltip placement="right" title={props.currentUser?.displayName ?? '当前用户'}>
                <div className="platform-im-activity-user">
                    <Avatar src={props.currentUser?.avatarUrl}>
                        {avatarText(props.currentUser?.displayName)}
                    </Avatar>
                    <Typography.Text>{props.currentUser?.displayName}</Typography.Text>
                </div>
            </Tooltip>
        </nav>
    )
}

type RailButtonProps = {
    active: boolean
    badge?: number
    icon: React.ReactNode
    label: string
    onClick: () => void
}

function RailButton(props: RailButtonProps) {
    return (
        <Tooltip placement="right" title={props.label}>
            <Badge count={props.badge ?? 0} offset={[-4, 4]} overflowCount={99} size="small">
                <Button
                    aria-label={props.label}
                    aria-pressed={props.active}
                    className="platform-im-activity-button"
                    icon={props.icon}
                    onClick={props.onClick}
                    type={props.active ? 'primary' : 'text'}
                />
            </Badge>
        </Tooltip>
    )
}

function avatarText(name?: string) {
    return name?.trim().slice(0, 1).toUpperCase() || 'U'
}
