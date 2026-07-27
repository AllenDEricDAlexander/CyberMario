import {Typography} from 'antd'
import type {CSSProperties, ReactNode} from 'react'

type PageSectionProps = {
    title?: ReactNode
    description?: ReactNode
    actions?: ReactNode
    children: ReactNode
}

/** A titled block inside a page. Use instead of stacking bare cards with margins. */
export function PageSection({title, description, actions, children}: PageSectionProps) {
    const hasHeader = Boolean(title || description || actions)

    return (
        <section className="page-section">
            {hasHeader && (
                <header className="page-section-header">
                    <div>
                        {title && (
                            <Typography.Title className="page-section-title" level={5}>{title}</Typography.Title>
                        )}
                        {description && (
                            <Typography.Paragraph className="page-section-description" type="secondary">
                                {description}
                            </Typography.Paragraph>
                        )}
                    </div>
                    {actions && <div className="page-toolbar-actions">{actions}</div>}
                </header>
            )}
            {children}
        </section>
    )
}

type PageGridProps = {
    children: ReactNode
    /** Minimum column width before the grid wraps. Defaults to 320px. */
    minWidth?: number
}

/** Responsive auto-fit grid — the default layout for card collections. */
export function PageGrid({children, minWidth}: PageGridProps) {
    return (
        <div
            className="page-grid"
            style={minWidth ? ({'--page-grid-min': `${minWidth}px`} as CSSProperties) : undefined}
        >
            {children}
        </div>
    )
}

/** Vertical stack with the standard page gap. */
export function PageStack({children}: { children: ReactNode }) {
    return <div className="page-stack">{children}</div>
}
