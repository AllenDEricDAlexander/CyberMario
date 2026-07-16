import {Select} from 'antd'
import type {InvestmentWorkspaceResponse} from '../types/investmentWorkspaceTypes'

type InvestmentWorkspaceSelectProps = {
    workspaces: InvestmentWorkspaceResponse[]
    value?: number | null
    loading?: boolean
    disabled?: boolean
    onChange: (workspaceId: number | null) => void
}

export function InvestmentWorkspaceSelect({
    workspaces,
    value,
    loading,
    disabled,
    onChange,
}: InvestmentWorkspaceSelectProps) {
    return (
        <Select
            allowClear
            aria-label="当前投资工作区"
            disabled={disabled}
            loading={loading}
            onChange={(workspaceId) => onChange(workspaceId ?? null)}
            options={workspaces.map((workspace) => ({
                label: `${workspace.name} · ${workspace.baseCurrency}`,
                value: workspace.id,
            }))}
            placeholder="选择投资工作区"
            style={{minWidth: 240}}
            value={value ?? undefined}
        />
    )
}
