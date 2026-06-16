import {describe, expect, test} from 'vitest'
import {formatLocalDateTime} from './dateTime'

describe('formatLocalDateTime', () => {
    test('formats values in browser local time with supported display formats', () => {
        const value = new Date(2026, 5, 16, 7, 8, 9)

        expect(formatLocalDateTime(value)).toBe('2026-06-16 07:08:09')
        expect(formatLocalDateTime(value, 'date')).toBe('2026-06-16')
        expect(formatLocalDateTime(value, 'time')).toBe('07:08:09')
        expect(formatLocalDateTime(value, 'YYYY-MM-DD HH:mm:ss')).toBe('2026-06-16 07:08:09')
        expect(formatLocalDateTime(value, 'YYYY-MM-DD')).toBe('2026-06-16')
        expect(formatLocalDateTime(value, 'HH:mm:ss')).toBe('07:08:09')
    })

    test('handles empty and invalid values without throwing', () => {
        expect(formatLocalDateTime()).toBe('-')
        expect(formatLocalDateTime(null)).toBe('-')
        expect(formatLocalDateTime('')).toBe('-')
        expect(formatLocalDateTime('not-a-date')).toBe('not-a-date')
    })

    test('formats backend ISO instants with microseconds instead of showing raw ISO text', () => {
        const result = formatLocalDateTime('2026-06-15T23:31:22.372073Z')

        expect(result).not.toBe('2026-06-15T23:31:22.372073Z')
        expect(result).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:22$/)
    })
})
