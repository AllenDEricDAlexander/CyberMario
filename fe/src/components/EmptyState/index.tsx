import {Empty, Typography} from 'antd'
import type {ReactNode} from 'react'

type EmptyStateProps = {
    title?: string
    description?: ReactNode
    /** Primary call to action, e.g. a "新建" button. */
    action?: ReactNode
    /** Custom illustration; defaults to Ant Design's simple outline image. */
    image?: ReactNode
    /** Tighter padding for use inside cards and drawers. */
    inline?: boolean
}

/**
 * Empty placeholder used by tables, panels and search results so a page with no
 * data still explains what should be there and how to create it.
 */
export function EmptyState({title = '暂无数据', description, action, image, inline}: EmptyStateProps) {
    return (
        <div className={inline ? 'state-block state-block-inline' : 'state-block'}>
            <Empty
                description={
                    <>
                        <Typography.Text strong>{title}</Typography.Text>
                        {description && (
                            <Typography.Paragraph className="state-block-description">
                                {description}
                            </Typography.Paragraph>
                        )}
                    </>
                }
                image={image ?? Empty.PRESENTED_IMAGE_SIMPLE}
            />
            {action && <div className="state-block-actions">{action}</div>}
        </div>
    )
}
