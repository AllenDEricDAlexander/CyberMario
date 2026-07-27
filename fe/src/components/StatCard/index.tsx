import {ArrowDownOutlined, ArrowUpOutlined} from '@ant-design/icons'
import {Skeleton, Tooltip} from 'antd'
import type {CSSProperties, ReactNode} from 'react'

type StatTone = 'accent' | 'sky' | 'amber' | 'coral' | 'violet'

const toneColor: Record<StatTone, string> = {
    accent: 'var(--accent)',
    sky: 'var(--sky)',
    amber: 'var(--amber)',
    coral: 'var(--coral)',
    violet: 'var(--violet)',
}

type StatCardProps = {
    label: ReactNode
    value: ReactNode
    /** Unit or qualifier rendered smaller next to the value, e.g. `ms`, `%`. */
    suffix?: ReactNode
    icon?: ReactNode
    hint?: ReactNode
    tone?: StatTone
    loading?: boolean
    /** Period-over-period change in percent; sign selects the arrow and colour. */
    trend?: number
    /** Explains what the number measures on hover. */
    tooltip?: string
}

/**
 * A single metric tile. Replaces the ad-hoc `Card` + `Statistic` pairs that each
 * dashboard built differently, so all metric rows share one visual rhythm.
 */
export function StatCard({label, value, suffix, icon, hint, tone = 'accent', loading, trend, tooltip}: StatCardProps) {
    const card = (
        <div className="stat-card" style={{'--stat-accent': toneColor[tone]} as CSSProperties}>
            {icon && <span className="stat-card-icon">{icon}</span>}
            <div className="stat-card-main">
                <span className="stat-card-label">{label}</span>
                {loading ? (
                    <Skeleton active paragraph={false} title={{width: '60%'}}/>
                ) : (
                    <span className="stat-card-value">
                        {value}
                        {suffix && <span className="stat-card-suffix">{suffix}</span>}
                    </span>
                )}
                {(hint || typeof trend === 'number') && (
                    <span className="stat-card-hint">
                        {typeof trend === 'number' && (
                            <span className={`stat-card-trend ${trend >= 0 ? 'is-up' : 'is-down'}`}>
                                {trend >= 0 ? <ArrowUpOutlined/> : <ArrowDownOutlined/>}
                                {Math.abs(trend).toFixed(1)}%
                            </span>
                        )}
                        {hint}
                    </span>
                )}
            </div>
        </div>
    )

    return tooltip ? <Tooltip title={tooltip}>{card}</Tooltip> : card
}

type StatGridProps = {
    children: ReactNode
    /** Minimum tile width before the grid wraps. Defaults to 190px. */
    minWidth?: number
    /**
     * Fixed column count at desktop width, stepping down on smaller screens.
     * Use when the tile count has a natural split (6 tiles read better as 3+3
     * than as the ragged 4+2 an auto-fit grid produces).
     */
    columns?: 2 | 3 | 4 | 6
}

export function StatGrid({children, minWidth, columns}: StatGridProps) {
    return (
        <div
            className={columns ? `stat-grid is-cols-${columns}` : 'stat-grid'}
            style={minWidth ? ({'--stat-min': `${minWidth}px`} as CSSProperties) : undefined}
        >
            {children}
        </div>
    )
}
