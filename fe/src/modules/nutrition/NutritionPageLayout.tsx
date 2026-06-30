import {Card} from 'antd'
import type {ReactNode} from 'react'

export function NutritionPageGrid({children}: { children: ReactNode }) {
    return (
        <div style={{display: 'grid', gap: 16, gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))'}}>
            {children}
        </div>
    )
}

export function NutritionStack({children}: { children: ReactNode }) {
    return <div style={{display: 'flex', flexDirection: 'column', gap: 16}}>{children}</div>
}

export function NutritionSection({title, extra, children}: { title: string; extra?: ReactNode; children: ReactNode }) {
    return (
        <Card extra={extra} title={title}>
            {children}
        </Card>
    )
}
