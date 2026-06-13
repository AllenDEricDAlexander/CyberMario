import {Tag} from 'antd'
import {enumDesc, enumEquals, type EnumValue} from '../../utils/enum'

type ApiRiskTagProps = {
    value: EnumValue
}

export function ApiRiskTag({value}: ApiRiskTagProps) {
    if (enumEquals(value, 3) || enumEquals(value, 'HIGH')) {
        return <Tag color="red">{enumDesc(value, '高')}</Tag>
    }
    if (enumEquals(value, 2) || enumEquals(value, 'MEDIUM')) {
        return <Tag color="orange">{enumDesc(value, '中')}</Tag>
    }
    return <Tag color="green">{enumDesc(value, '低')}</Tag>
}
