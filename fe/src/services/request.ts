import {ApiRequestError, type ApiResponse} from '../types/api'
import {clearTokens, getAccessToken, getRefreshToken, saveTokens} from './tokenStorage'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

type RequestOptions = {
    method?: string
    body?: unknown
    auth?: boolean
    headers?: HeadersInit
}

type StreamChunkHandler = (chunk: ChatResponse) => void
type JsonLineHandler<T> = (chunk: T) => void

type ChatResponse = {
    threadId: string
    message: string
    type: 'think' | 'message'
}

type RefreshLoginResponse = {
    accessToken?: string | null
    refreshToken?: string | null
}

let refreshPromise: Promise<boolean> | null = null

export async function requestJson<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
    return requestJsonInternal<T>(endpoint, options, true)
}

export async function requestFormData<T>(endpoint: string, formData: FormData, options: Omit<RequestOptions, 'body'> = {}): Promise<T> {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        method: options.method ?? 'POST',
        headers: buildHeaders(options, false),
        body: formData,
    })

    return unwrapJsonResponse<T>(response)
}

async function requestJsonInternal<T>(
    endpoint: string,
    options: RequestOptions,
    allowRefresh: boolean,
): Promise<T> {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        method: options.method ?? 'GET',
        headers: buildHeaders(options),
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })

    if (response.status === 401 && options.auth !== false && allowRefresh) {
        const refreshed = await refreshAccessToken()
        if (refreshed) {
            return requestJsonInternal<T>(endpoint, options, false)
        }
    }

    return unwrapJsonResponse<T>(response)
}

export async function streamServerSentEvents(
    endpoint: string,
    request: {
        body: unknown
        signal: AbortSignal
    },
    onChunk: StreamChunkHandler,
) {
    const response = await fetch(endpoint, {
        method: 'POST',
        headers: buildHeaders({headers: {Accept: 'application/x-ndjson'}}),
        body: JSON.stringify(request.body),
        signal: request.signal,
    })

    if (!response.ok) {
        throw new Error(`请求失败：HTTP ${response.status}`)
    }
    if (!response.body) {
        throw new Error('后端没有返回可读取的响应流')
    }

    const reader = response.body.getReader()
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
            try {
                onChunk(JSON.parse(trimmed) as ChatResponse)
            } catch {
                console.warn('NDJSON parse failed:', trimmed.slice(0, 80))
            }
        }
    }

    if (buffer.trim()) {
        try {
            onChunk(JSON.parse(buffer.trim()) as ChatResponse)
        } catch {
            console.warn('NDJSON final line parse failed:', buffer.trim().slice(0, 80))
        }
    }
}

export async function streamJsonLines<T>(
    endpoint: string,
    request: {
        body: unknown
        signal: AbortSignal
    },
    onChunk: JsonLineHandler<T>,
) {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        method: 'POST',
        headers: buildHeaders({headers: {Accept: 'application/x-ndjson'}}),
        body: JSON.stringify(request.body),
        signal: request.signal,
    })

    if (!response.ok) {
        throw new Error(`请求失败：HTTP ${response.status}`)
    }
    if (!response.body) {
        throw new Error('后端没有返回可读取的响应流')
    }

    const reader = response.body.getReader()
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
    if (includeJsonContentType && !headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json')
    }
    const accessToken = options.auth === false ? null : getAccessToken()
    if (accessToken) {
        headers.set('Authorization', `Bearer ${accessToken}`)
    }
    return headers
}

async function unwrapJsonResponse<T>(response: Response): Promise<T> {
    const text = await response.text()
    const payload = text ? (JSON.parse(text) as ApiResponse<T>) : null

    if (!response.ok) {
        throw new ApiRequestError(payload?.message ?? `请求失败：HTTP ${response.status}`, {
            code: payload?.code ?? `HTTP_${response.status}`,
            status: response.status,
            traceId: payload?.traceId,
        })
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

async function refreshAccessToken() {
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
        clearTokens()
        return false
    }

    if (!refreshPromise) {
        refreshPromise = fetch(`${API_BASE_URL}/api/auth/refresh`, {
            method: 'POST',
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({refreshToken}),
        })
            .then(async (response) => {
                const data = await unwrapJsonResponse<RefreshLoginResponse>(response)
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
