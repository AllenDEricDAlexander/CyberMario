import axios, {type AxiosResponse} from 'axios'
import {ApiRequestError, type ApiResponse} from '../types/api'
import {
    PERMISSION_VERSION_HEADER,
    publishPermissionVersion,
    publishResponsePermissionVersion,
} from './permissionVersionEvents'
import {csrfHeaderFor, isUnsafeMethod, readCsrfToken, saveCsrfToken} from './csrfToken'

export const API_BASE_URL = String(import.meta.env.VITE_API_BASE_URL ?? '')
const TRACE_ID_HEADER = 'X-Trace-Id'
const CLIENT_TYPE_HEADER = 'X-Client-Type'
const CLIENT_TYPE_VALUE = 'browser'
const AUTH_CSRF_INVALID_CODE = 'AUTH_CSRF_INVALID'
const apiClient = axios.create({
    baseURL: API_BASE_URL || undefined,
    validateStatus: () => true,
})

type RequestOptions = {
    method?: string
    body?: unknown
    auth?: boolean
    headers?: HeadersInit
}

type JsonLineHandler<T> = (chunk: T) => void
type ServerSentEventHandler<T> = (event: T) => void
type CsrfTokenResponse = {
    headerName: string
    parameterName: string
    token: string
}

let refreshPromise: Promise<boolean> | null = null
let csrfPromise: Promise<void> | null = null

export async function requestJson<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
    return requestJsonInternal<T>(endpoint, options, true)
}

export async function requestFormData<T>(endpoint: string, formData: FormData, options: Omit<RequestOptions, 'body'> = {}): Promise<T> {
    const response = await axiosWithAuthRetry(
        () => apiClient.request<ApiResponse<T>>({
            data: formData,
            method: options.method ?? 'POST',
            headers: buildHeaders({...options, method: options.method ?? 'POST'}, false),
            url: endpoint,
            withCredentials: true,
        }),
        options,
        options.method ?? 'POST',
    )

    return unwrapAxiosApiResponse<T>(response)
}

async function requestJsonInternal<T>(
    endpoint: string,
    options: RequestOptions,
    allowRefresh: boolean,
): Promise<T> {
    const response = await axiosWithAuthRetry(
        () => apiClient.request<ApiResponse<T>>({
            data: options.body,
            method: options.method ?? 'GET',
            headers: buildHeaders(options),
            url: endpoint,
            withCredentials: true,
        }),
        options,
        options.method ?? 'GET',
        allowRefresh,
    )

    return unwrapAxiosApiResponse<T>(response)
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
            headers: buildHeaders({method: 'POST', headers: {Accept: 'application/x-ndjson'}}),
            body: JSON.stringify(request.body),
            credentials: 'include',
            signal: request.signal,
        }),
        {},
        'POST',
    )

    if (!response.ok) {
        await throwApiResponseError(response)
    }
    if (!response.body) {
        throw new Error('后端没有返回可读取的响应流')
    }

    await readJsonLines(response.body, onChunk)
}

export async function streamServerSentEvents<T>(
    endpoint: string,
    request: {
        signal: AbortSignal
    },
    onEvent: ServerSentEventHandler<T>,
) {
    const response = await fetchWithAuthRetry(
        () => fetch(`${API_BASE_URL}${endpoint}`, {
            method: 'GET',
            headers: buildHeaders({method: 'GET', headers: {Accept: 'text/event-stream'}}, false),
            credentials: 'include',
            signal: request.signal,
        }),
        {},
        'GET',
    )

    if (!response.ok) {
        await throwApiResponseError(response)
    }
    if (!response.body) {
        throw new Error('后端没有返回可读取的响应流')
    }

    await readServerSentEvents(response.body, onEvent)
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

async function readServerSentEvents<T>(body: ReadableStream<Uint8Array>, onEvent: ServerSentEventHandler<T>) {
    const reader = body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
        const {done, value} = await reader.read()
        if (done) {
            break
        }

        buffer += decoder.decode(value, {stream: true})
        const events = buffer.split(/\r?\n\r?\n/)
        buffer = events.pop() ?? ''

        events.forEach((eventBlock) => emitServerSentEvent(eventBlock, onEvent))
    }

    if (buffer.trim()) {
        emitServerSentEvent(buffer, onEvent)
    }
}

function emitServerSentEvent<T>(eventBlock: string, onEvent: ServerSentEventHandler<T>) {
    const data = eventBlock
        .split(/\r?\n/)
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice('data:'.length).trimStart())
        .join('\n')
        .trim()
    if (!data) {
        return
    }
    onEvent(JSON.parse(data) as T)
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
    const headers = createHeadersRecord(options.headers)
    if (!hasHeader(headers, 'Accept')) {
        setHeader(headers, 'Accept', 'application/json')
    }
    if (!hasHeader(headers, CLIENT_TYPE_HEADER)) {
        setHeader(headers, CLIENT_TYPE_HEADER, CLIENT_TYPE_VALUE)
    }
    if (!hasHeader(headers, TRACE_ID_HEADER)) {
        setHeader(headers, TRACE_ID_HEADER, createUuidV7())
    }
    if (includeJsonContentType && !hasHeader(headers, 'Content-Type')) {
        setHeader(headers, 'Content-Type', 'application/json')
    }
    Object.entries(csrfHeaderFor(options.method)).forEach(([name, value]) => {
        setHeader(headers, name, value)
    })
    return headers
}

function unwrapAxiosApiResponse<T>(response: AxiosResponse<ApiResponse<T> | string>): T {
    const payload = parseAxiosApiResponse<T>(response.data)

    if (response.status < 200 || response.status >= 300) {
        throw toApiRequestError(response.status, payload)
    }

    return unwrapParsedApiResponse(response.status, payload)
}

function unwrapParsedApiResponse<T>(status: number, payload: ApiResponse<T> | null) {
    if (!payload) {
        return undefined as T
    }

    if (payload.code !== '0') {
        throw new ApiRequestError(payload.message || '请求失败', {
            code: payload.code,
            status,
            traceId: payload.traceId,
        })
    }

    return payload.data
}

async function throwApiResponseError(response: Response): Promise<never> {
    const text = await response.text()
    throw toApiRequestError(response.status, parseTextApiResponse<unknown>(text))
}

function parseTextApiResponse<T>(text: string) {
    if (!text) {
        return null
    }
    try {
        return JSON.parse(text) as ApiResponse<T>
    } catch {
        return null
    }
}

function parseAxiosApiResponse<T>(data: ApiResponse<T> | string) {
    if (!data) {
        return null
    }
    if (typeof data === 'string') {
        return parseTextApiResponse<T>(data)
    }
    return data
}

function toApiRequestError(status: number, payload: ApiResponse<unknown> | null) {
    return new ApiRequestError(payload?.message ?? defaultHttpErrorMessage(status), {
        code: payload?.code ?? `HTTP_${status}`,
        status,
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
    method: string,
    allowRefresh = true,
) {
    await ensureCsrfFor(method)

    let response = await fetcher()
    if (await isCsrfInvalidFetchResponse(response)) {
        await refreshCsrf()
        response = await fetcher()
    }
    if (response.status !== 401 || options.auth === false || !allowRefresh) {
        publishResponsePermissionVersion(response)
        return response
    }

    const refreshed = await refreshAccessToken()
    const retryResponse = refreshed ? await fetcher() : response
    publishResponsePermissionVersion(retryResponse)
    return retryResponse
}

async function axiosWithAuthRetry<T>(
    requester: () => Promise<AxiosResponse<ApiResponse<T> | string>>,
    options: Pick<RequestOptions, 'auth'>,
    method: string,
    allowRefresh = true,
) {
    await ensureCsrfFor(method)

    let response = await requester()
    if (isCsrfInvalidAxiosResponse(response)) {
        await refreshCsrf()
        response = await requester()
    }
    if (response.status !== 401 || options.auth === false || !allowRefresh) {
        publishAxiosResponsePermissionVersion(response)
        return response
    }

    const refreshed = await refreshAccessToken()
    const retryResponse = refreshed ? await requester() : response
    publishAxiosResponsePermissionVersion(retryResponse)
    return retryResponse
}

async function refreshAccessToken() {
    if (!refreshPromise) {
        refreshPromise = ensureCsrfFor('POST')
            .then(() => requestAccessTokenRefresh())
            .then(async (response) => {
                if (!isCsrfInvalidAxiosResponse(response)) {
                    return response
                }
                await refreshCsrf()
                return requestAccessTokenRefresh()
            })
            .then((response) => {
                unwrapAxiosApiResponse<unknown>(response)
                return true
            })
            .catch(() => false)
            .finally(() => {
                refreshPromise = null
            })
    }

    return refreshPromise
}

function requestAccessTokenRefresh() {
    return apiClient.request<ApiResponse<unknown>>({
        data: undefined,
        method: 'POST',
        headers: buildHeaders({auth: false, method: 'POST'}),
        url: '/api/auth/refresh',
        withCredentials: true,
    })
}

async function ensureCsrfFor(method?: string) {
    if (!isUnsafeMethod(method) || readCsrfToken()) {
        return
    }
    await refreshCsrf()
}

async function refreshCsrf() {
    if (!csrfPromise) {
        csrfPromise = apiClient.request<ApiResponse<CsrfTokenResponse>>({
            method: 'GET',
            headers: buildHeaders({auth: false, method: 'GET'}, false),
            url: '/api/auth/csrf',
            withCredentials: true,
        })
            .then((response) => {
                const csrfToken = unwrapAxiosApiResponse<CsrfTokenResponse>(response)
                saveCsrfToken(csrfToken.token)
            })
            .finally(() => {
                csrfPromise = null
            })
    }

    return csrfPromise
}

function isCsrfInvalidAxiosResponse(response: AxiosResponse<ApiResponse<unknown> | string>) {
    return response.status === 403 && parseAxiosApiResponse<unknown>(response.data)?.code === AUTH_CSRF_INVALID_CODE
}

async function isCsrfInvalidFetchResponse(response: Response) {
    if (response.status !== 403) {
        return false
    }
    try {
        const payload = parseTextApiResponse<unknown>(await response.clone().text())
        return payload?.code === AUTH_CSRF_INVALID_CODE
    } catch {
        return false
    }
}

function createHeadersRecord(init?: HeadersInit) {
    const headers: Record<string, string> = {}
    if (!init) {
        return headers
    }
    if (init instanceof Headers) {
        init.forEach((value, key) => {
            headers[key] = value
        })
        return headers
    }
    if (Array.isArray(init)) {
        init.forEach(([key, value]) => {
            headers[key] = value
        })
        return headers
    }
    Object.entries(init).forEach(([key, value]) => {
        headers[key] = value
    })
    return headers
}

function hasHeader(headers: Record<string, string>, name: string) {
    return Object.keys(headers).some((key) => key.toLowerCase() === name.toLowerCase())
}

function setHeader(headers: Record<string, string>, name: string, value: string) {
    const existingKey = Object.keys(headers).find((key) => key.toLowerCase() === name.toLowerCase())
    if (existingKey) {
        delete headers[existingKey]
    }
    headers[name] = value
}

function publishAxiosResponsePermissionVersion(response: AxiosResponse<unknown>) {
    const headers = response.headers as Record<string, string | string[] | number | boolean | null | undefined>
    const permissionVersion = headers[PERMISSION_VERSION_HEADER] ?? headers[PERMISSION_VERSION_HEADER.toLowerCase()]
    publishPermissionVersion(toHeaderString(permissionVersion))
}

function toHeaderString(value: string | string[] | number | boolean | null | undefined) {
    if (Array.isArray(value)) {
        return value.join(', ')
    }
    if (value === null || value === undefined) {
        return value
    }
    return String(value)
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
