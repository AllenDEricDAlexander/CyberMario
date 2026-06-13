import {Tag} from 'antd'
import {enumDesc, enumEquals, type EnumValue} from '../../utils/enum'

type StatusTagProps = {
    value: EnumValue
}

export function StatusTag({value}: StatusTagProps) {
    if (enumEquals(value, 1) || enumEquals(value, 'ENABLED')) {
        return <Tag color="success">{enumDesc(value, '启用')}</Tag>
    }
    if (enumEquals(value, 2) || enumEquals(value, 'DRAFT')) {
        return <Tag color="default">{enumDesc(value, '草稿')}</Tag>
    }
    return <Tag color="error">{enumDesc(value, '禁用')}</Tag>
}
