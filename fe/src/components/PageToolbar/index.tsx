import {Space, Typography} from 'antd'
import type {ReactNode} from 'react'

type PageToolbarProps = {
    title: string
    description?: string
    actions?: ReactNode
}

export function PageToolbar({title, description, actions}: PageToolbarProps) {
    return (
        <div className="page-toolbar">
            <div>
                <Typography.Title level={3}>{title}</Typography.Title>
                {description && <Typography.Paragraph type="secondary">{description}</Typography.Paragraph>}
            </div>
            {actions && <Space wrap>{actions}</Space>}
        </div>
    )
}
