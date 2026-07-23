import {beforeEach, describe, expect, test, vi} from 'vitest'
import {ApiRequestError} from '../../types/api'
import {completeAccountActivation, login, register} from './authService'

vi.mock('../../services/request', () => ({
    requestJson: vi.fn(),
}))

vi.mock('./passwordEncryption', () => ({
    clearPasswordKeyCache: vi.fn(),
    encryptPasswordForTransport: vi.fn((password: string) => Promise.resolve({
        encryptedPassword: `encrypted:${password}`,
        passwordKeyId: 'key-1',
    })),
}))

describe('authService', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    test('encrypts login password before sending the request body', async () => {
        const {requestJson} = await import('../../services/request')

        await login({account: 'mario', password: 'secret'})

        expect(requestJson).toHaveBeenCalledWith('/api/auth/login', {
            method: 'POST',
            auth: false,
            body: {
                account: 'mario',
                encryptedPassword: 'encrypted:secret',
                passwordKeyId: 'key-1',
            },
        })
        expect(vi.mocked(requestJson).mock.calls[0]?.[1]?.body).not.toHaveProperty('password')
    })

    test('encrypts register password before sending the request body', async () => {
        const {requestJson} = await import('../../services/request')

        await register({
            accountNo: 'mario',
            username: 'mario',
            password: 'password123',
            nickname: 'Mario',
            email: 'mario@example.com',
        })

        expect(requestJson).toHaveBeenCalledWith('/api/auth/register', {
            method: 'POST',
            auth: false,
            body: {
                accountNo: 'mario',
                username: 'mario',
                encryptedPassword: 'encrypted:password123',
                passwordKeyId: 'key-1',
                nickname: 'Mario',
                email: 'mario@example.com',
            },
        })
        expect(vi.mocked(requestJson).mock.calls[0]?.[1]?.body).not.toHaveProperty('password')
    })

    test('refreshes the password key once when login rejects a stale key', async () => {
        const {requestJson} = await import('../../services/request')
        const {clearPasswordKeyCache, encryptPasswordForTransport} = await import('./passwordEncryption')
        vi.mocked(encryptPasswordForTransport)
            .mockResolvedValueOnce({encryptedPassword: 'old-encrypted', passwordKeyId: 'old-key'})
            .mockResolvedValueOnce({encryptedPassword: 'new-encrypted', passwordKeyId: 'new-key'})
        vi.mocked(requestJson)
            .mockRejectedValueOnce(new ApiRequestError('password encryption key is invalid', {
                code: 'AUTH_PASSWORD_KEY_INVALID',
            }))
            .mockResolvedValueOnce({})

        await login({account: 'mario', password: 'secret'})

        expect(clearPasswordKeyCache).toHaveBeenCalledTimes(1)
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/auth/login', {
            method: 'POST',
            auth: false,
            body: {
                account: 'mario',
                encryptedPassword: 'new-encrypted',
                passwordKeyId: 'new-key',
            },
        })
    })

    test('encrypts the activation password and never sends plaintext', async () => {
        const {requestJson} = await import('../../services/request')

        await completeAccountActivation('raw-token', 'new-password-123')

        expect(requestJson).toHaveBeenCalledWith('/api/auth/activation/complete', {
            method: 'POST',
            auth: false,
            body: {
                token: 'raw-token',
                encryptedPassword: 'encrypted:new-password-123',
                passwordKeyId: 'key-1',
            },
        })
        expect(vi.mocked(requestJson).mock.calls[0]?.[1]?.body).not.toHaveProperty('password')
    })
})
