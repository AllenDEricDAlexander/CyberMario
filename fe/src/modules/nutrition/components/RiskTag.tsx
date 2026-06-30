import {Tag} from 'antd'
import type {NutritionRiskLevel} from '../nutritionTypes'

type RiskTagProps = {
    value?: NutritionRiskLevel | null
    blocking?: boolean
}

const riskLabels: Record<NutritionRiskLevel, string> = {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险',
    BLOCKING: '阻断风险',
}

const riskColors: Record<NutritionRiskLevel, string> = {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'error',
    BLOCKING: 'error',
}

export function RiskTag({value, blocking}: RiskTagProps) {
    if (!value) {
        return <Tag>未评估</Tag>
    }
    const highRiskBlocking = blocking && value === 'HIGH'
    return <Tag color={highRiskBlocking ? 'error' : riskColors[value]}>{riskLabels[value]}</Tag>
}
