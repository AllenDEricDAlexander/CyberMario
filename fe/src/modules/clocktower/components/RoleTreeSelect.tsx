import {TreeSelect} from 'antd'
import type {CSSProperties} from 'react'
import {enumCode} from '../../../utils/enum'
import type {ClocktowerRoleResponse, ClocktowerRoleType, ClocktowerRoleTypeCode} from '../clocktowerTypes'

type RoleTreeNode = {
    title: string
    value: string
    selectable?: boolean
    checkable?: boolean
    disableCheckbox?: boolean
    searchText?: string
    children?: RoleTreeNode[]
}

type RoleTreeSelectProps = {
    disabled?: boolean
    loading?: boolean
    placeholder?: string
    roles: ClocktowerRoleResponse[]
    style?: CSSProperties
    value?: string[]
    onChange?: (value: string[]) => void
}

const roleTypeOrder: Array<{ code: ClocktowerRoleTypeCode; label: string; numericCode: number }> = [
    {code: 'TOWNSFOLK', label: '镇民', numericCode: 1},
    {code: 'OUTSIDER', label: '外来者', numericCode: 2},
    {code: 'MINION', label: '爪牙', numericCode: 3},
    {code: 'DEMON', label: '恶魔', numericCode: 4},
    {code: 'TRAVELER', label: '旅行者', numericCode: 5},
    {code: 'FABLED', label: '传奇', numericCode: 6},
]

const roleTypeByNumericCode = new Map(roleTypeOrder.map((type) => [type.numericCode, type.code]))
const roleTypeLabels = new Map(roleTypeOrder.map((type) => [type.code, type.label]))

export function buildRoleTreeData(roles: ClocktowerRoleResponse[]) {
    const rolesByType = new Map<ClocktowerRoleTypeCode, ClocktowerRoleResponse[]>()
    roles.forEach((role) => {
        const roleTypeCode = normalizeRoleTypeCode(role.roleType)
        if (!roleTypeCode) {
            return
        }
        rolesByType.set(roleTypeCode, [...rolesByType.get(roleTypeCode) ?? [], role])
    })

    return roleTypeOrder
        .filter((type) => rolesByType.has(type.code))
        .map<RoleTreeNode>((type) => ({
            title: type.label,
            value: `type-${type.code}`,
            selectable: false,
            disableCheckbox: true,
            children: [...rolesByType.get(type.code) ?? []]
                .sort((current, next) => current.roleCode.localeCompare(next.roleCode))
                .map((role) => ({
                    title: `${role.roleName} (${role.roleCode})`,
                    value: role.roleCode,
                    selectable: true,
                    checkable: true,
                    searchText: [role.roleName, role.roleCode, role.name].filter(Boolean).join(' '),
                })),
        }))
}

export function selectedRoleCountText(roleCodes: string[] | undefined, playerCount: number | undefined) {
    return `已选择 ${roleCodes?.length ?? 0} / ${playerCount ?? 0} 个角色`
}

export function RoleTreeSelect({
    disabled,
    loading,
    placeholder = '请选择角色',
    roles,
    style,
    value,
    onChange,
}: RoleTreeSelectProps) {
    return (
        <TreeSelect
            allowClear
            disabled={disabled}
            loading={loading}
            maxTagCount="responsive"
            onChange={(nextValue) => onChange?.(Array.isArray(nextValue) ? nextValue : [])}
            placeholder={placeholder}
            showCheckedStrategy={TreeSelect.SHOW_CHILD}
            showSearch={{filterTreeNode: filterRoleTreeNode}}
            style={style}
            treeCheckable
            treeData={buildRoleTreeData(roles)}
            treeDefaultExpandAll
            value={value}
        />
    )
}

function normalizeRoleTypeCode(roleType: ClocktowerRoleType): ClocktowerRoleTypeCode | undefined {
    const code = enumCode(roleType)
    if (typeof code === 'number') {
        return roleTypeByNumericCode.get(code)
    }
    if (typeof code === 'string' && roleTypeLabels.has(code as ClocktowerRoleTypeCode)) {
        return code as ClocktowerRoleTypeCode
    }
    return undefined
}

function filterRoleTreeNode(inputValue: string, treeNode: unknown) {
    const searchText = String((treeNode as { searchText?: string }).searchText ?? '')
    return searchText.toLowerCase().includes(inputValue.toLowerCase())
}
