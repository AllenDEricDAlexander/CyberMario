import {Tag} from 'antd'
import {enumCode, enumDesc} from '../../../utils/enum'
import type {ClocktowerRoleType, ClocktowerRoleTypeCode} from '../clocktowerTypes'

const roleTypeLabels: Record<ClocktowerRoleTypeCode, string> = {
    TOWNSFOLK: '镇民',
    OUTSIDER: '外来者',
    MINION: '爪牙',
    DEMON: '恶魔',
    TRAVELER: '旅行者',
    FABLED: '传奇',
}

const roleTypeColors: Record<ClocktowerRoleTypeCode | number, string> = {
    TOWNSFOLK: 'blue',
    OUTSIDER: 'cyan',
    MINION: 'volcano',
    DEMON: 'red',
    TRAVELER: 'purple',
    FABLED: 'gold',
    1: 'blue',
    2: 'cyan',
    3: 'volcano',
    4: 'red',
    5: 'purple',
    6: 'gold',
}

type RoleTypeTagProps = {
    value?: ClocktowerRoleType | null
}

export function RoleTypeTag({value}: RoleTypeTagProps) {
    if (!value) {
        return <Tag>未定</Tag>
    }
    const code = enumCode(value)
    const label = typeof value === 'string' ? roleTypeLabels[value] ?? enumDesc(value) : enumDesc(value)
    return <Tag color={roleTypeColors[code as ClocktowerRoleTypeCode | number]}>{label}</Tag>
}
