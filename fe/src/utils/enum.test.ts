import {describe, expect, test} from 'vitest'
import {enumCode, enumDesc, enumEquals} from './enum'

describe('enum helpers', () => {
    test('reads coded enum values from backend enum payloads', () => {
        const value = {code: 1, desc: '启用'}

        expect(enumCode(value)).toBe(1)
        expect(enumDesc(value)).toBe('启用')
        expect(enumEquals(value, '1')).toBe(true)
    })

    test('uses fallback description for blank values', () => {
        expect(enumDesc(undefined)).toBe('-')
        expect(enumDesc(null, '未知')).toBe('未知')
        expect(enumDesc('')).toBe('-')
    })
})
