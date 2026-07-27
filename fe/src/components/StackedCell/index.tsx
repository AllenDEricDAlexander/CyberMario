import type {ReactNode} from 'react'

type StackedCellProps = {
    primary: ReactNode
    secondary?: ReactNode
    /** Renders the primary line in normal weight — for non-identifier columns. */
    plain?: boolean
}

/**
 * Two-line table cell: a strong primary value with a muted qualifier beneath.
 *
 * Collapsing "账号 + 用户名" or "邮箱 + 手机" into one column this way removes
 * several columns per table, which is what forced the wide horizontal scroll.
 */
export function StackedCell({primary, secondary, plain}: StackedCellProps) {
    return (
        <div className="stacked-cell">
            <span className={plain ? 'stacked-cell-primary is-plain' : 'stacked-cell-primary'}>{primary}</span>
            {secondary !== undefined && secondary !== null && secondary !== '' && (
                <span className="stacked-cell-secondary">{secondary}</span>
            )}
        </div>
    )
}
