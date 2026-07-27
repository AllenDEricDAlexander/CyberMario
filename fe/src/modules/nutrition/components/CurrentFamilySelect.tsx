import {Select} from 'antd'
import type {NutritionFamilyResponse} from '../nutritionTypes'
import '../nutrition.css'

type CurrentFamilySelectProps = {
    families: NutritionFamilyResponse[]
    value?: number | null
    loading?: boolean
    disabled?: boolean
    onChange: (familyId: number | null) => void
}

export function CurrentFamilySelect({families, value, loading, disabled, onChange}: CurrentFamilySelectProps) {
    return (
        <Select
            allowClear
            aria-label="当前营养家庭"
            className="nutrition-select"
            disabled={disabled}
            loading={loading}
            onChange={(familyId) => onChange(familyId ?? null)}
            options={families.map((family) => ({label: family.name, value: family.id}))}
            placeholder="选择家庭"
            value={value ?? undefined}
        />
    )
}
