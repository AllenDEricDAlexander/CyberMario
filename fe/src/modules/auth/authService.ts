import {requestJson} from '../../services/request'
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
    return requestJson<void>('/api/auth/logout', {
        method: 'POST',
    })
}
