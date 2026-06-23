import {afterEach, beforeEach, describe, expect, test, vi} from 'vitest'
import {bootstrapAuthSession} from './authStore'

const LEGACY_TOKEN_KEYS = [
    'cyber-mario-access-token',
    'cyber-mario-refresh-token',
    'cyber-mario-access-token-expires-at',
    'cyber-mario-refresh-token-expires-at',
]

function installLocalStorage() {
    const store = new Map<string, string>()
    vi.stubGlobal('localStorage', {
        getItem: (key: string) => store.get(key) ?? null,
        setItem: (key: string, value: string) => store.set(key, value),
        removeItem: (key: string) => store.delete(key),
        clear: () => store.clear(),
    })
}

describe('bootstrapAuthSession', () => {
    beforeEach(() => {
        installLocalStorage()
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    test('checks the current user on startup without local storage tokens', async () => {
        const reload = vi.fn().mockResolvedValue(undefined)
        const clearSession = vi.fn()
        const finish = vi.fn()

        await bootstrapAuthSession({reload, clearSession, finish})

        expect(reload).toHaveBeenCalledTimes(1)
        expect(clearSession).not.toHaveBeenCalled()
        expect(finish).toHaveBeenCalledTimes(1)
    })

    test('clears legacy local storage token keys on startup', async () => {
        LEGACY_TOKEN_KEYS.forEach((key) => localStorage.setItem(key, `${key}-value`))

        await bootstrapAuthSession({
            reload: vi.fn().mockResolvedValue(undefined),
            clearSession: vi.fn(),
            finish: vi.fn(),
        })

        LEGACY_TOKEN_KEYS.forEach((key) => expect(localStorage.getItem(key)).toBeNull())
    })

    test('clears in-memory session and finishes bootstrapping when current user reload fails', async () => {
        const reload = vi.fn().mockRejectedValue(new Error('unauthenticated'))
        const clearSession = vi.fn()
        const finish = vi.fn()

        await bootstrapAuthSession({reload, clearSession, finish})

        expect(clearSession).toHaveBeenCalledTimes(1)
        expect(finish).toHaveBeenCalledTimes(1)
    })
})
