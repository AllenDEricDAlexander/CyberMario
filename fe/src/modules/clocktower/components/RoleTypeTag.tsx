import {Tag} from 'antd'
import type {ClocktowerRoleType} from '../clocktowerTypes'

const roleTypeLabels: Record<ClocktowerRoleType, string> = {
    TOWNSFOLK: '镇民',
    OUTSIDER: '外来者',
    MINION: '爪牙',
    DEMON: '恶魔',
    TRAVELER: '旅行者',
    FABLED: '传奇',
}

const roleTypeColors: Record<ClocktowerRoleType, string> = {
    TOWNSFOLK: 'blue',
    OUTSIDER: 'cyan',
    MINION: 'volcano',
    DEMON: 'red',
    TRAVELER: 'purple',
    FABLED: 'gold',
}

type RoleTypeTagProps = {
    value?: ClocktowerRoleType | null
}

export function RoleTypeTag({value}: RoleTypeTagProps) {
    if (!value) {
        return <Tag>未定</Tag>
    }
    return <Tag color={roleTypeColors[value]}>{roleTypeLabels[value]}</Tag>
}
