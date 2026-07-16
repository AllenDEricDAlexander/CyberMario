import type {InvestmentDecimal} from '../types/investmentCommonTypes'

type InvestmentDecimalTextProps = {
    value?: InvestmentDecimal | null
    suffix?: string
    emptyText?: string
}

/**
 * Renders server decimal strings verbatim so JavaScript number coercion cannot lose precision.
 */
export function InvestmentDecimalText({value, suffix, emptyText = '-'}: InvestmentDecimalTextProps) {
    if (value === null || value === undefined || value === '') {
        return <span>{emptyText}</span>
    }
    return <span>{value}{suffix ? ` ${suffix}` : ''}</span>
}
