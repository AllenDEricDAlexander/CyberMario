const ACCESS_TOKEN_KEY = 'cyber-mario-access-token'
const REFRESH_TOKEN_KEY = 'cyber-mario-refresh-token'
const ACCESS_TOKEN_EXPIRES_AT_KEY = 'cyber-mario-access-token-expires-at'
const REFRESH_TOKEN_EXPIRES_AT_KEY = 'cyber-mario-refresh-token-expires-at'

export type TokenSnapshot = {
    accessToken?: string | null
    refreshToken?: string | null
    accessTokenExpiresInSeconds?: number | null
    refreshTokenExpiresInSeconds?: number | null
}

export function getAccessToken() {
    return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken() {
    return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function shouldRefreshAccessToken(skewMilliseconds: number) {
    const accessToken = getAccessToken()
    const refreshToken = getRefreshToken()
    const expiresAt = getTokenExpiresAt(ACCESS_TOKEN_EXPIRES_AT_KEY)
    return Boolean(accessToken && refreshToken && expiresAt !== null && expiresAt - Date.now() <= skewMilliseconds)
}

export function saveTokens(tokens: TokenSnapshot) {
    if (tokens.accessToken) {
        localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken)
        saveTokenExpiresAt(ACCESS_TOKEN_EXPIRES_AT_KEY, tokens.accessTokenExpiresInSeconds)
    }
    if (tokens.refreshToken) {
        localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
        saveTokenExpiresAt(REFRESH_TOKEN_EXPIRES_AT_KEY, tokens.refreshTokenExpiresInSeconds)
    }
}

export function clearTokens() {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(ACCESS_TOKEN_EXPIRES_AT_KEY)
    localStorage.removeItem(REFRESH_TOKEN_EXPIRES_AT_KEY)
}

export function hasStoredToken() {
    return Boolean(getAccessToken() || getRefreshToken())
}

function getTokenExpiresAt(key: string) {
    const value = localStorage.getItem(key)
    if (!value) {
        return null
    }
    const expiresAt = Number(value)
    return Number.isFinite(expiresAt) ? expiresAt : null
}

function saveTokenExpiresAt(key: string, expiresInSeconds?: number | null) {
    if (expiresInSeconds && Number.isFinite(expiresInSeconds)) {
        localStorage.setItem(key, String(Date.now() + expiresInSeconds * 1000))
        return
    }
    localStorage.removeItem(key)
}
