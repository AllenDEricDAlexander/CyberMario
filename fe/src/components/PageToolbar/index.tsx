import {ArrowLeftOutlined} from '@ant-design/icons'
import {Breadcrumb, Button, Typography} from 'antd'
import type {ReactNode} from 'react'
import {Link, useNavigate} from 'react-router'

export type PageBreadcrumbItem = {
    title: ReactNode
    to?: string
}

type PageToolbarProps = {
    title: ReactNode
    description?: ReactNode
    actions?: ReactNode
    /** Small glyph rendered in a tinted square to the left of the title. */
    icon?: ReactNode
    /** Rendered above the title — status tags, environment badges, counters. */
    eyebrow?: ReactNode
    breadcrumb?: PageBreadcrumbItem[]
    /** Shows a back button. `true` goes back one history entry, a string navigates to it. */
    back?: boolean | string
}

/**
 * The standard header for every admin page: breadcrumb, title block and the
 * page-level action buttons. Layout and spacing live in `.page-toolbar` styles
 * so pages never need to patch margins themselves.
 */
export function PageToolbar({title, description, actions, icon, eyebrow, breadcrumb, back}: PageToolbarProps) {
    const hasBreadcrumb = Boolean(breadcrumb?.length)

    return (
        <div className="page-toolbar">
            <div className="page-toolbar-main">
                {(hasBreadcrumb || eyebrow) && (
                    <div className="page-toolbar-eyebrow">
                        {hasBreadcrumb && (
                            <Breadcrumb
                                items={breadcrumb?.map((item) => ({
                                    title: item.to ? <Link to={item.to}>{item.title}</Link> : item.title,
                                }))}
                            />
                        )}
                        {eyebrow}
                    </div>
                )}
                <div className="page-toolbar-heading">
                    {back && <BackButton to={typeof back === 'string' ? back : undefined}/>}
                    {icon && <span className="page-toolbar-icon">{icon}</span>}
                    <Typography.Title className="page-toolbar-title" level={3}>{title}</Typography.Title>
                </div>
                {description && (
                    <Typography.Paragraph className="page-toolbar-description" type="secondary">
                        {description}
                    </Typography.Paragraph>
                )}
            </div>
            {actions && <div className="page-toolbar-actions">{actions}</div>}
        </div>
    )
}

/**
 * Kept as a separate component so `useNavigate` — and therefore the router
 * context requirement — only applies to pages that actually opt into a back
 * button. Plain toolbars stay renderable in isolation.
 */
function BackButton({to}: { to?: string }) {
    const navigate = useNavigate()

    return (
        <Button
            aria-label="返回"
            className="page-toolbar-back"
            icon={<ArrowLeftOutlined/>}
            onClick={() => void (to ? navigate(to) : navigate(-1))}
            shape="circle"
            type="text"
        />
    )
}
