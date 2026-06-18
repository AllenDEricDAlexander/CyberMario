import {Tag} from 'antd'
import type {ClocktowerRoleSummaryResponse} from '../clocktowerTypes'

type RoleSummaryTagsProps = {
    roleCodes: string[]
    roles?: ClocktowerRoleSummaryResponse[]
}

export function RoleSummaryTags({roleCodes, roles}: RoleSummaryTagsProps) {
    const roleByCode = new Map((roles ?? []).map((role) => [role.roleCode, role]))
    const summaries = roleCodes.length > 0
        ? roleCodes.map((roleCode) => roleByCode.get(roleCode) ?? {roleCode, roleName: roleCode})
        : roles ?? []

    return (
        <span>
            {summaries.map((role) => (
                <Tag
                    key={role.roleCode}>{role.roleName === role.roleCode ? role.roleCode : `${role.roleName} (${role.roleCode})`}</Tag>
            ))}
        </span>
    )
}
