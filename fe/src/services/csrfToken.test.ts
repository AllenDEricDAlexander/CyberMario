import {afterEach, describe, expect, test, vi} from 'vitest'
import {
    CSRF_HEADER_NAME,
    csrfHeaderFor,
    isUnsafeMethod,
    readCsrfToken,
} from './csrfToken'

describe('csrfToken', () => {
    afterEach(() => {
        vi.unstubAllGlobals()
    })

    test('reads XSRF token from document cookie', () => {
        vi.stubGlobal('document', {
            cookie: 'theme=dark; XSRF-TOKEN=abc%20123; session=active',
        })

        expect(readCsrfToken()).toBe('abc 123')
    })

    test('detects unsafe request methods', () => {
        expect(isUnsafeMethod('POST')).toBe(true)
        expect(isUnsafeMethod('PUT')).toBe(true)
        expect(isUnsafeMethod('PATCH')).toBe(true)
        expect(isUnsafeMethod('DELETE')).toBe(true)

        expect(isUnsafeMethod('GET')).toBe(false)
        expect(isUnsafeMethod('HEAD')).toBe(false)
        expect(isUnsafeMethod('OPTIONS')).toBe(false)
        expect(isUnsafeMethod('TRACE')).toBe(false)
    })

    test('returns csrf header only for unsafe requests with token', () => {
        expect(csrfHeaderFor('POST', 'XSRF-TOKEN=token-123')).toEqual({
            [CSRF_HEADER_NAME]: 'token-123',
        })
        expect(csrfHeaderFor('GET', 'XSRF-TOKEN=token-123')).toEqual({})
    })

    test('does not throw when document is missing', () => {
        expect(readCsrfToken()).toBeNull()
        expect(csrfHeaderFor('POST')).toEqual({})
    })

    test('returns empty headers for blank or missing tokens', () => {
        expect(readCsrfToken('XSRF-TOKEN=')).toBeNull()
        expect(readCsrfToken('theme=dark')).toBeNull()
        expect(csrfHeaderFor('POST', 'XSRF-TOKEN=')).toEqual({})
        expect(csrfHeaderFor('POST', 'theme=dark')).toEqual({})
    })
})
