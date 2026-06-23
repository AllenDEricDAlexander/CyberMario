export const CSRF_COOKIE_NAME = 'XSRF-TOKEN'
export const CSRF_HEADER_NAME = 'X-XSRF-TOKEN'

const UNSAFE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])
let csrfToken: string | null = null

export function saveCsrfToken(token?: string | null) {
    const normalizedToken = token?.trim()
    csrfToken = normalizedToken || null
}

export function clearCsrfToken() {
    csrfToken = null
}

export function readCsrfToken() {
    return csrfToken
}

export function isUnsafeMethod(method?: string) {
    return UNSAFE_METHODS.has((method ?? 'GET').trim().toUpperCase())
}

export function csrfHeaderFor(method?: string): Record<string, string> {
    if (!isUnsafeMethod(method)) {
        return {}
    }

    const token = readCsrfToken()
    if (!token) {
        return {}
    }

    return {
        [CSRF_HEADER_NAME]: token,
    }
}
