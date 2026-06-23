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

    test('does not persist sensitive tokens in browser storage', () => {
        saveTokens({
            accessToken: 'access-token',
            refreshToken: 'refresh-token',
            accessTokenExpiresInSeconds: 120,
            refreshTokenExpiresInSeconds: 600,
        })

        expect(localStorage.getItem('cyber-mario-access-token')).toBeNull()
        expect(localStorage.getItem('cyber-mario-refresh-token')).toBeNull()
        expect(getAccessToken()).toBeNull()
        expect(getRefreshToken()).toBeNull()
    })

    test('removes legacy token keys when clearing tokens', () => {
        localStorage.setItem('cyber-mario-access-token', 'access-token')
        localStorage.setItem('cyber-mario-refresh-token', 'refresh-token')
        localStorage.setItem('cyber-mario-access-token-expires-at', '1767225600000')
        localStorage.setItem('cyber-mario-refresh-token-expires-at', '1767225600000')

        clearTokens()

        expect(localStorage.getItem('cyber-mario-access-token')).toBeNull()
        expect(localStorage.getItem('cyber-mario-refresh-token')).toBeNull()
        expect(localStorage.getItem('cyber-mario-access-token-expires-at')).toBeNull()
        expect(localStorage.getItem('cyber-mario-refresh-token-expires-at')).toBeNull()
    })

    test('does not treat legacy tokens as an active browser session', () => {
        localStorage.setItem('cyber-mario-access-token', 'access-token')
        localStorage.setItem('cyber-mario-refresh-token', 'refresh-token')

        expect(hasStoredToken()).toBe(false)
    })

    test('does not refresh access tokens in browser cookie mode', () => {
        localStorage.setItem('cyber-mario-access-token', 'access-token')
        localStorage.setItem('cyber-mario-refresh-token', 'refresh-token')
        localStorage.setItem('cyber-mario-access-token-expires-at', String(Date.now() + 30_000))

        expect(shouldRefreshAccessToken(60_000)).toBe(false)
        expect(shouldRefreshAccessToken(10_000)).toBe(false)
    })
})
