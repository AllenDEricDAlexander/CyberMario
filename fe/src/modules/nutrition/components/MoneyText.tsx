import type {NutritionAmount} from '../nutritionTypes'

type MoneyTextProps = {
    value?: NutritionAmount | null
    currency?: string
    emptyText?: string
}

export function MoneyText({value, currency = 'CNY', emptyText = '-'}: MoneyTextProps) {
    if (value === null || value === undefined || value === '') {
        return <span>{emptyText}</span>
    }
    const numericValue = typeof value === 'number' ? value : Number(value)
    if (!Number.isFinite(numericValue)) {
        return <span>{emptyText}</span>
    }
    return <span>{formatMoney(numericValue, currency)}</span>
}

export function formatMoney(value: number, currency = 'CNY') {
    return new Intl.NumberFormat('zh-CN', {
        currency,
        currencyDisplay: 'narrowSymbol',
        maximumFractionDigits: 2,
        minimumFractionDigits: 2,
        style: 'currency',
    }).format(value)
}
