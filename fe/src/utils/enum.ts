export type CodedEnum = {
    code: number
    desc: string
}

export type EnumValue = string | number | CodedEnum | null | undefined

export function enumCode(value: EnumValue) {
    if (typeof value === 'object' && value) {
        return value.code
    }
    return value
}

export function enumDesc(value: EnumValue, fallback = '-') {
    if (typeof value === 'object' && value) {
        return value.desc
    }
    if (value === null || value === undefined || value === '') {
        return fallback
    }
    return String(value)
}

export function enumEquals(value: EnumValue, expectedCode: number | string) {
    return enumCode(value) === expectedCode || String(enumCode(value)) === String(expectedCode)
}
