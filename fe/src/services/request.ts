import {ApiRequestError, type ApiResponse} from '../types/api'
import {publishResponsePermissionVersion} from './permissionVersionEvents'
import {clearTokens, getAccessToken, getRefreshToken, saveTokens, shouldRefreshAccessToken} from './tokenStorage'

export const API_BASE_URL = String(import.meta.env.VITE_API_BASE_URL ?? '')
const TRACE_ID_HEADER = 'X-Trace-Id'
const ACCESS_TOKEN_REFRESH_SKEW_MILLISECONDS = 60_000

type RequestOptions = {
    method?: string
    body?: unknown
    auth?: boolean
    headers?: HeadersInit
}

type JsonLineHandler<T> = (chunk: T) => void

type RefreshLoginResponse = {
    accessToken?: string | null
    refreshToken?: string | null
    accessTokenExpiresInSeconds?: number | null
    refreshTokenExpiresInSeconds?: number | null
}

let refreshPromise: Promise<boolean> | null = null

export async function requestJson<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
    return requestJsonInternal<T>(endpoint, options, true)
}

export async function requestFormData<T>(endpoint: string, formData: FormData, options: Omit<RequestOptions, 'body'> = {}): Promise<T> {
    const response = await fetchWithAuthRetry(
        () => fetch(`${API_BASE_URL}${endpoint}`, {
            method: options.method ?? 'POST',
            headers: buildHeaders(options, false),
            body: formData,
        }),
        options,
    )

    return unwrapApiResponse<T>(response)
}

async function requestJsonInternal<T>(
    endpoint: string,
    options: RequestOptions,
    allowRefresh: boolean,
): Promise<T> {
    const response = await fetchWithAuthRetry(
        () => fetch(`${API_BASE_URL}${endpoint}`, {
            method: options.method ?? 'GET',
            headers: buildHeaders(options),
            body: options.body === undefined ? undefined : JSON.stringify(options.body),
        }),
        options,
        allowRefresh,
    )

    return unwrapApiResponse<T>(response)
}

export async function streamJsonLines<T>(
    endpoint: string,
    request: {
        body: unknown
        signal: AbortSignal
    },
    onChunk: JsonLineHandler<T>,
) {
    const response = await fetchWithAuthRetry(
        () => fetch(`${API_BASE_URL}${endpoint}`, {
            method: 'POST',
            headers: buildHeaders({headers: {Accept: 'application/x-ndjson'}}),
            body: JSON.stringify(request.body),
            signal: request.signal,
        }),
        {},
    )

    if (!response.ok) {
        await throwApiResponseError(response)
    }
    if (!response.body) {
        throw new Error('后端没有返回可读取的响应流')
    }

    await readJsonLines(response.body, onChunk)
}

async function readJsonLines<T>(body: ReadableStream<Uint8Array>, onChunk: JsonLineHandler<T>) {
    const reader = body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
        const {done, value} = await reader.read()
        if (done) {
            break
        }

        buffer += decoder.decode(value, {stream: true})
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''

        for (const line of lines) {
            const trimmed = line.trim()
            if (!trimmed) continue
            onChunk(JSON.parse(trimmed) as T)
        }
    }

    if (buffer.trim()) {
        onChunk(JSON.parse(buffer.trim()) as T)
    }
}

export function resolveErrorMessage(error: unknown) {
    if (error instanceof ApiRequestError) {
        return error.message
    }
    if (error instanceof Error) {
        return error.message
    }
    return '请求失败，请稍后重试'
}

function buildHeaders(options: RequestOptions, includeJsonContentType = true) {
    const headers = new Headers(options.headers)
    if (!headers.has('Accept')) {
        headers.set('Accept', 'application/json')
    }
    if (!headers.has(TRACE_ID_HEADER)) {
        headers.set(TRACE_ID_HEADER, createUuidV7())
    }
    if (includeJsonContentType && !headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json')
    }
    const accessToken = options.auth === false ? null : getAccessToken()
    if (accessToken) {
        headers.set('Authorization', `Bearer ${accessToken}`)
    }
    return headers
}

async function unwrapApiResponse<T>(response: Response): Promise<T> {
    const text = await response.text()
    const payload = parseApiResponse<T>(text)

    if (!response.ok) {
        throw toApiRequestError(response, payload)
    }

    if (!payload) {
        return undefined as T
    }

    if (payload.code !== '0') {
        throw new ApiRequestError(payload.message || '请求失败', {
            code: payload.code,
            status: response.status,
            traceId: payload.traceId,
        })
    }

    return payload.data
}

async function throwApiResponseError(response: Response): Promise<never> {
    const text = await response.text()
    throw toApiRequestError(response, parseApiResponse<unknown>(text))
}

function parseApiResponse<T>(text: string) {
    if (!text) {
        return null
    }
    try {
        return JSON.parse(text) as ApiResponse<T>
    } catch {
        return null
    }
}

function toApiRequestError(response: Response, payload: ApiResponse<unknown> | null) {
    return new ApiRequestError(payload?.message ?? defaultHttpErrorMessage(response.status), {
        code: payload?.code ?? `HTTP_${response.status}`,
        status: response.status,
        traceId: payload?.traceId,
    })
}

function defaultHttpErrorMessage(status: number) {
    if (status === 401) {
        return '登录已失效，请重新登录'
    }
    if (status === 403) {
        return '没有权限执行该操作'
    }
    if (status === 404) {
        return '请求的资源不存在'
    }
    if (status >= 500) {
        return '服务暂时不可用，请稍后重试'
    }
    return `请求失败：HTTP ${status}`
}

async function fetchWithAuthRetry(
    fetcher: () => Promise<Response>,
    options: Pick<RequestOptions, 'auth'>,
    allowRefresh = true,
) {
    if (options.auth !== false && shouldRefreshAccessToken(ACCESS_TOKEN_REFRESH_SKEW_MILLISECONDS)) {
        await refreshAccessToken()
    }

    const response = await fetcher()
    if (response.status !== 401 || options.auth === false || !allowRefresh) {
        publishResponsePermissionVersion(response)
        return response
    }

    const refreshed = await refreshAccessToken()
    const retryResponse = refreshed ? await fetcher() : response
    publishResponsePermissionVersion(retryResponse)
    return retryResponse
}

async function refreshAccessToken() {
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
        clearTokens()
        return false
    }

    if (!refreshPromise) {
        refreshPromise = fetch(`${API_BASE_URL}/api/auth/refresh`, {
            method: 'POST',
            headers: buildHeaders({auth: false}),
            body: JSON.stringify({refreshToken}),
        })
            .then(async (response) => {
                const data = await unwrapApiResponse<RefreshLoginResponse>(response)
                saveTokens(data)
                return Boolean(data.accessToken)
            })
            .catch(() => {
                clearTokens()
                return false
            })
            .finally(() => {
                refreshPromise = null
            })
    }

    return refreshPromise
}

function createUuidV7() {
    const bytes = new Uint8Array(16)
    crypto.getRandomValues(bytes)

    const timestamp = Date.now()
    bytes[0] = Math.floor(timestamp / 0x10000000000) & 0xff
    bytes[1] = Math.floor(timestamp / 0x100000000) & 0xff
    bytes[2] = Math.floor(timestamp / 0x1000000) & 0xff
    bytes[3] = Math.floor(timestamp / 0x10000) & 0xff
    bytes[4] = Math.floor(timestamp / 0x100) & 0xff
    bytes[5] = timestamp & 0xff
    bytes[6] = (bytes[6] & 0x0f) | 0x70
    bytes[8] = (bytes[8] & 0x3f) | 0x80

    const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0'))
    return `${hex.slice(0, 4).join('')}-${hex.slice(4, 6).join('')}-${hex.slice(6, 8).join('')}-${hex.slice(8, 10).join('')}-${hex.slice(10, 16).join('')}`
}
