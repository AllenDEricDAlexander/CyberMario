import {requestJson} from '../../services/request'
import {getRefreshToken} from '../../services/tokenStorage'
import type {LoginRequest, LoginResponse, RegisterRequest} from './authTypes'

export function login(request: LoginRequest) {
    return requestJson<LoginResponse>('/api/auth/login', {
        method: 'POST',
        body: request,
        auth: false,
    })
}

export function register(request: RegisterRequest) {
    return requestJson<LoginResponse>('/api/auth/register', {
        method: 'POST',
        body: request,
        auth: false,
    })
}

export function fetchCurrentUser() {
    return requestJson<LoginResponse>('/api/auth/me')
}

export function logout() {
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
        return Promise.resolve()
    }
    return requestJson<void>('/api/auth/logout', {
        method: 'POST',
        body: {refreshToken},
    })
}
