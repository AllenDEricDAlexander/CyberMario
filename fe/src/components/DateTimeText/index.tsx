import type {DateTimeDisplayFormat, DateTimeValue} from '../../utils/dateTime'
import {formatLocalDateTime} from '../../utils/dateTime'

type DateTimeTextProps = {
    value?: DateTimeValue
    format?: DateTimeDisplayFormat
    emptyText?: string
}

export function DateTimeText({value, format = 'dateTime', emptyText = '-'}: DateTimeTextProps) {
    return <>{formatLocalDateTime(value, format, emptyText)}</>
}
