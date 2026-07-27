import {MoreOutlined} from '@ant-design/icons'
import {App, Button, Dropdown, Popconfirm, Tooltip} from 'antd'
import type {MenuProps} from 'antd'
import type {ReactNode} from 'react'

export type RowAction = {
    key: string
    label: ReactNode
    icon?: ReactNode
    onClick?: () => void
    /** Renders the action in danger styling. */
    danger?: boolean
    disabled?: boolean
    /** When set, the action asks for confirmation before `onClick` runs. */
    confirm?: string
    hidden?: boolean
    tooltip?: string
}

type RowActionsProps = {
    actions: RowAction[]
    /** How many actions stay inline before the rest collapse into a menu. */
    maxInline?: number
    /** Rendered when no action is visible — keeps table columns from collapsing. */
    emptyText?: ReactNode
}

/**
 * Table row actions with automatic overflow.
 *
 * Rows used to render six or seven small buttons side by side, which forced
 * 360px+ action columns and horizontal scrolling. Only the first `maxInline`
 * actions stay visible; the rest move into a "更多" dropdown.
 */
export function RowActions({actions, maxInline = 2, emptyText = '-'}: RowActionsProps) {
    const {modal} = App.useApp()
    const visible = actions.filter((action) => !action.hidden)

    if (visible.length === 0) {
        return <>{emptyText}</>
    }

    // One extra action is cheaper to show inline than to hide behind a menu.
    const inline = visible.length <= maxInline + 1 ? visible : visible.slice(0, maxInline)
    const overflow = visible.slice(inline.length)

    const menuItems: MenuProps['items'] = overflow.map((action) => ({
        key: action.key,
        label: action.label,
        icon: action.icon,
        danger: action.danger,
        disabled: action.disabled,
    }))

    function runOverflowAction(key: string) {
        const action = overflow.find((item) => item.key === key)
        if (!action?.onClick) {
            return
        }
        if (!action.confirm) {
            action.onClick()
            return
        }
        // A Popconfirm cannot survive the menu closing, so overflow actions
        // confirm through a modal instead.
        modal.confirm({
            title: action.confirm,
            okText: '确认',
            cancelText: '取消',
            okButtonProps: {danger: action.danger},
            onOk: action.onClick,
        })
    }

    return (
        <div className="row-actions">
            {inline.map((action) => (
                <InlineAction action={action} key={action.key}/>
            ))}
            {overflow.length > 0 && (
                <Dropdown
                    menu={{
                        className: 'row-actions-menu',
                        items: menuItems,
                        onClick: ({key}) => runOverflowAction(key),
                    }}
                    placement="bottomRight"
                    trigger={['click']}
                >
                    <Button
                        aria-label="更多操作"
                        className="row-actions-more"
                        icon={<MoreOutlined/>}
                        size="small"
                        type="text"
                    />
                </Dropdown>
            )}
        </div>
    )
}

function InlineAction({action}: { action: RowAction }) {
    const button = (
        <Button
            danger={action.danger}
            disabled={action.disabled}
            icon={action.icon}
            onClick={action.confirm ? undefined : action.onClick}
            size="small"
            type="link"
        >
            {action.label}
        </Button>
    )

    const withTooltip = action.tooltip ? <Tooltip title={action.tooltip}>{button}</Tooltip> : button

    if (!action.confirm) {
        return withTooltip
    }

    return (
        <Popconfirm
            cancelText="取消"
            okButtonProps={{danger: action.danger}}
            okText="确认"
            onConfirm={action.onClick}
            title={action.confirm}
        >
            {withTooltip}
        </Popconfirm>
    )
}
