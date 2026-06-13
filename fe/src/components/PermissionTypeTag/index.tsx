import {Tag} from 'antd'
import {enumDesc, enumEquals, type EnumValue} from '../../utils/enum'

type PermissionTypeTagProps = {
    value: EnumValue
}

export function PermissionTypeTag({value}: PermissionTypeTagProps) {
    if (enumEquals(value, 1) || enumEquals(value, 'MENU')) {
        return <Tag color="blue">{enumDesc(value, '菜单')}</Tag>
    }
    if (enumEquals(value, 2) || enumEquals(value, 'BUTTON')) {
        return <Tag color="purple">{enumDesc(value, '按钮')}</Tag>
    }
    return <Tag color="volcano">{enumDesc(value, '接口')}</Tag>
}
