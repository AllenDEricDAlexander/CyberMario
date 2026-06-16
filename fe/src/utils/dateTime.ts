export type DateTimeDisplayFormat = 'dateTime' | 'date' | 'time' | 'YYYY-MM-DD HH:mm:ss' | 'YYYY-MM-DD' | 'HH:mm:ss'

export type DateTimeValue = string | number | Date | null | undefined

export function formatLocalDateTime(
    value?: DateTimeValue,
    format: DateTimeDisplayFormat = 'dateTime',
    emptyText = '-',
) {
    if (value === undefined || value === null || value === '') {
        return emptyText
    }
    const date = value instanceof Date ? value : new Date(value)
    if (Number.isNaN(date.getTime())) {
        return String(value)
    }

    const dateText = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    const timeText = `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
    if (format === 'date' || format === 'YYYY-MM-DD') {
        return dateText
    }
    if (format === 'time' || format === 'HH:mm:ss') {
        return timeText
    }
    return `${dateText} ${timeText}`
}

function pad(value: number) {
    return String(value).padStart(2, '0')
}
