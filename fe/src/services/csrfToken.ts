export const CSRF_COOKIE_NAME = 'XSRF-TOKEN'
export const CSRF_HEADER_NAME = 'X-XSRF-TOKEN'

const UNSAFE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

function readCookie(name: string, cookieSource?: string) {
    const source = cookieSource ?? (typeof document === 'undefined' ? '' : document.cookie)
    if (!source) {
        return null
    }

    const cookie = source
        .split(';')
        .map((item) => item.trim())
        .find((item) => item.startsWith(`${name}=`))
    if (!cookie) {
        return null
    }

    const value = cookie.slice(name.length + 1)
    if (!value) {
        return null
    }

    return decodeURIComponent(value)
}

export function readCsrfToken(cookieSource?: string) {
    return readCookie(CSRF_COOKIE_NAME, cookieSource)
}

export function isUnsafeMethod(method?: string) {
    return UNSAFE_METHODS.has((method ?? 'GET').toUpperCase())
}

export function csrfHeaderFor(method?: string, cookieSource?: string): Record<string, string> {
    if (!isUnsafeMethod(method)) {
        return {}
    }

    const token = readCsrfToken(cookieSource)
    if (!token) {
        return {}
    }

    return {
        [CSRF_HEADER_NAME]: token,
    }
}
