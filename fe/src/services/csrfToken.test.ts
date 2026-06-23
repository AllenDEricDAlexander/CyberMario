import {afterEach, describe, expect, test, vi} from 'vitest'
import {
    clearCsrfToken,
    CSRF_HEADER_NAME,
    csrfHeaderFor,
    isUnsafeMethod,
    readCsrfToken,
    saveCsrfToken,
} from './csrfToken'

describe('csrfToken', () => {
    afterEach(() => {
        clearCsrfToken()
        vi.unstubAllGlobals()
    })

    test('stores csrf token from endpoint response in memory', () => {
        saveCsrfToken('masked-token')

        expect(readCsrfToken()).toBe('masked-token')
    })

    test('does not use raw XSRF cookie value as request token', () => {
        vi.stubGlobal('document', {
            cookie: 'theme=dark; XSRF-TOKEN=raw-cookie-token; session=active',
        })

        expect(readCsrfToken()).toBeNull()
        expect(csrfHeaderFor('POST')).toEqual({})
    })

    test('detects unsafe request methods', () => {
        expect(isUnsafeMethod('POST')).toBe(true)
        expect(isUnsafeMethod('PUT')).toBe(true)
        expect(isUnsafeMethod('PATCH')).toBe(true)
        expect(isUnsafeMethod('DELETE')).toBe(true)
        expect(isUnsafeMethod(' post ')).toBe(true)

        expect(isUnsafeMethod('GET')).toBe(false)
        expect(isUnsafeMethod('HEAD')).toBe(false)
        expect(isUnsafeMethod('OPTIONS')).toBe(false)
        expect(isUnsafeMethod('TRACE')).toBe(false)
    })

    test('returns csrf header only for unsafe requests with token', () => {
        saveCsrfToken('masked-token-123')

        expect(csrfHeaderFor('POST')).toEqual({
            [CSRF_HEADER_NAME]: 'masked-token-123',
        })
        expect(csrfHeaderFor('GET')).toEqual({})
    })

    test('does not throw when document is missing', () => {
        expect(readCsrfToken()).toBeNull()
        expect(csrfHeaderFor('POST')).toEqual({})
    })

    test('returns empty headers for blank or missing tokens', () => {
        saveCsrfToken('')
        expect(readCsrfToken()).toBeNull()
        expect(csrfHeaderFor('POST')).toEqual({})

        saveCsrfToken('   ')
        expect(readCsrfToken()).toBeNull()
        expect(csrfHeaderFor('POST')).toEqual({})

        clearCsrfToken()
        expect(readCsrfToken()).toBeNull()
        expect(csrfHeaderFor('POST')).toEqual({})
    })
})
