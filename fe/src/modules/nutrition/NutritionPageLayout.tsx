import {Card} from 'antd'
import type {ReactNode} from 'react'
import {PageGrid, PageStack} from '../../components/PageSection'

/**
 * Nutrition-flavoured aliases for the shared layout primitives.
 *
 * These used to hand-roll their own grid and flex column with literal `16px`
 * gaps, which drifted from the rest of the console. They now forward to
 * `PageGrid` / `PageStack`; the names stay because every nutrition page — and
 * their tests — import them.
 */
export function NutritionPageGrid({children}: { children: ReactNode }) {
    return <PageGrid>{children}</PageGrid>
}

export function NutritionStack({children}: { children: ReactNode }) {
    return <PageStack>{children}</PageStack>
}

/** A titled card, the unit nutrition pages arrange inside a `NutritionPageGrid`. */
export function NutritionSection({title, extra, children}: { title: string; extra?: ReactNode; children: ReactNode }) {
    return (
        <Card extra={extra} title={title}>
            {children}
        </Card>
    )
}
