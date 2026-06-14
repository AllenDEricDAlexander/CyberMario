import {afterEach, beforeEach, describe, expect, test, vi} from 'vitest'
import {
    clearTokens,
    getAccessToken,
    getRefreshToken,
    hasStoredToken,
    saveTokens,
    shouldRefreshAccessToken
} from './tokenStorage'

function installLocalStorage() {
    const store = new Map<string, string>()
    vi.stubGlobal('localStorage', {
        getItem: (key: string) => store.get(key) ?? null,
        setItem: (key: string, value: string) => store.set(key, value),
        removeItem: (key: string) => store.delete(key),
        clear: () => store.clear(),
    })
}

describe('tokenStorage', () => {
    beforeEach(() => {
        installLocalStorage()
        vi.useFakeTimers()
        vi.setSystemTime(new Date('2026-01-01T00:00:00.000Z'))
    })

    afterEach(() => {
        vi.useRealTimers()
        vi.unstubAllGlobals()
    })

    test('stores and clears access and refresh tokens', () => {
        saveTokens({
            accessToken: 'access-token',
            refreshToken: 'refresh-token',
            accessTokenExpiresInSeconds: 120,
            refreshTokenExpiresInSeconds: 600,
        })

        expect(getAccessToken()).toBe('access-token')
        expect(getRefreshToken()).toBe('refresh-token')
        expect(hasStoredToken()).toBe(true)

        clearTokens()

        expect(getAccessToken()).toBeNull()
        expect(getRefreshToken()).toBeNull()
        expect(hasStoredToken()).toBe(false)
    })

    test('refreshes access token when it is within the configured skew', () => {
        saveTokens({
            accessToken: 'access-token',
            refreshToken: 'refresh-token',
            accessTokenExpiresInSeconds: 30,
        })

        expect(shouldRefreshAccessToken(60_000)).toBe(true)
        expect(shouldRefreshAccessToken(10_000)).toBe(false)
    })
})
