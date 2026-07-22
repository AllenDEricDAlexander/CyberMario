import {requestJson} from '../../services/request'
import {ApiRequestError} from '../../types/api'
import type {LoginRequest, LoginResponse, RegisterRequest} from './authTypes'
import {clearPasswordKeyCache, encryptPasswordForTransport} from './passwordEncryption'

const PASSWORD_KEY_INVALID_CODE = 'AUTH_PASSWORD_KEY_INVALID'

export async function login(request: LoginRequest) {
    return requestWithPasswordKeyRetry(async () => {
        const {password, ...account} = request
        const encryptedPassword = await encryptPasswordForTransport(password)
        return requestJson<LoginResponse>('/api/auth/login', {
            method: 'POST',
            body: {
                ...account,
                ...encryptedPassword,
            },
            auth: false,
        })
    })
}

export async function register(request: RegisterRequest) {
    return requestWithPasswordKeyRetry(async () => {
        const {password, ...profile} = request
        const encryptedPassword = await encryptPasswordForTransport(password)
        return requestJson<LoginResponse>('/api/auth/register', {
            method: 'POST',
            body: {
                ...profile,
                ...encryptedPassword,
            },
            auth: false,
        })
    })
}

export async function completeAccountActivation(token: string, password: string) {
    return requestWithPasswordKeyRetry(async () => {
        const encryptedPassword = await encryptPasswordForTransport(password)
        return requestJson<void>('/api/auth/activation/complete', {
            method: 'POST',
            auth: false,
            body: {token, ...encryptedPassword},
        })
    })
}

export function fetchCurrentUser() {
    return requestJson<LoginResponse>('/api/auth/me')
}

export function logout() {
    return requestJson<void>('/api/auth/logout', {
        method: 'POST',
    })
}

async function requestWithPasswordKeyRetry<T>(action: () => Promise<T>): Promise<T> {
    try {
        return await action()
    } catch (error) {
        if (!(error instanceof ApiRequestError) || error.code !== PASSWORD_KEY_INVALID_CODE) {
            throw error
        }
        clearPasswordKeyCache()
        return action()
    }
}
