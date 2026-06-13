const ACCESS_TOKEN_KEY = 'cyber-mario-access-token'
const REFRESH_TOKEN_KEY = 'cyber-mario-refresh-token'

export type TokenSnapshot = {
    accessToken?: string | null
    refreshToken?: string | null
}

export function getAccessToken() {
    return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken() {
    return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function saveTokens(tokens: TokenSnapshot) {
    if (tokens.accessToken) {
        localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken)
    }
    if (tokens.refreshToken) {
        localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
    }
}

export function clearTokens() {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export function hasStoredToken() {
    return Boolean(getAccessToken() || getRefreshToken())
}
