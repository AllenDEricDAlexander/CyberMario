const ACCESS_TOKEN_KEY = 'cyber-mario-access-token'
const REFRESH_TOKEN_KEY = 'cyber-mario-refresh-token'
const ACCESS_TOKEN_EXPIRES_AT_KEY = 'cyber-mario-access-token-expires-at'
const REFRESH_TOKEN_EXPIRES_AT_KEY = 'cyber-mario-refresh-token-expires-at'
const LEGACY_TOKEN_KEYS = [
    ACCESS_TOKEN_KEY,
    REFRESH_TOKEN_KEY,
    ACCESS_TOKEN_EXPIRES_AT_KEY,
    REFRESH_TOKEN_EXPIRES_AT_KEY,
]

export type TokenSnapshot = {
    accessToken?: string | null
    refreshToken?: string | null
    accessTokenExpiresInSeconds?: number | null
    refreshTokenExpiresInSeconds?: number | null
}

export function getAccessToken() {
    return null
}

export function getRefreshToken() {
    return null
}

export function shouldRefreshAccessToken(skewMilliseconds: number) {
    void skewMilliseconds
    return false
}

export function saveTokens(tokens: TokenSnapshot) {
    void tokens
    clearTokens()
}

export function clearTokens() {
    LEGACY_TOKEN_KEYS.forEach((key) => localStorage.removeItem(key))
}

export function hasStoredToken() {
    return false
}
