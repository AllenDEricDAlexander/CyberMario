import {ReloadOutlined, SettingOutlined} from '@ant-design/icons'
import {Button, Space, Tag, Tooltip, Typography} from 'antd'
import type {ReactNode} from 'react'

export type ChatWorkspaceHeaderProps = {
    title: ReactNode
    subtitle?: ReactNode
    messageCount: number
    status?: ReactNode
    actions?: ReactNode
    onReload?: () => void
    onOpenSettings?: () => void
}

export function ChatWorkspaceHeader(props: ChatWorkspaceHeaderProps) {
    const {
        title,
        subtitle,
        messageCount,
        status,
        actions,
        onReload,
        onOpenSettings,
    } = props

    return (
        <header className="chat-workspace-x-header">
            <div className="chat-workspace-x-header-main">
                <Typography.Title className="chat-workspace-x-header-title" level={3}>
                    {title}
                </Typography.Title>
                {subtitle && (
                    <Typography.Text className="chat-workspace-x-header-subtitle" type="secondary">
                        {subtitle}
                    </Typography.Text>
                )}
            </div>

            <Space className="chat-workspace-x-header-actions" wrap>
                {status ?? <Tag color="success">Ready</Tag>}
                <Tag>{messageCount} messages</Tag>
                {actions}
                {onReload && (
                    <Tooltip title="Reload">
                        <Button aria-label="Reload" icon={<ReloadOutlined/>} onClick={onReload}/>
                    </Tooltip>
                )}
                {onOpenSettings && (
                    <Tooltip title="Settings">
                        <Button aria-label="Settings" icon={<SettingOutlined/>} onClick={onOpenSettings}/>
                    </Tooltip>
                )}
            </Space>
        </header>
    )
}
