import {afterEach, beforeEach, describe, expect, test, vi} from 'vitest'
import {ApiRequestError} from '../types/api'
import {requestFormData, requestJson, streamJsonLines} from './request'

const {axiosRequestMock} = vi.hoisted(() => ({
    axiosRequestMock: vi.fn(),
}))

vi.mock('axios', () => ({
    default: {
        create: vi.fn(() => ({
            request: axiosRequestMock,
        })),
    },
}))

function apiResponse<T>(data: T, options: { code?: string; message?: string; traceId?: string } = {}) {
    return {
        code: options.code ?? '0',
        message: options.message ?? 'OK',
        data,
        traceId: options.traceId,
    }
}

function jsonResponse(body: unknown, init?: ResponseInit) {
    return new Response(JSON.stringify(body), {
        headers: {'Content-Type': 'application/json'},
        ...init,
    })
}

function axiosResponse(body: unknown, init: { status?: number; headers?: Record<string, string> } = {}) {
    return {
        data: body,
        headers: init.headers ?? {},
        status: init.status ?? 200,
    }
}

function ndjsonResponse(chunks: string[]) {
    return new Response(new ReadableStream({
        start(controller) {
            const encoder = new TextEncoder()
            chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)))
            controller.close()
        },
    }))
}

function installLocalStorage() {
    const store = new Map<string, string>()
    vi.stubGlobal('localStorage', {
        getItem: (key: string) => store.get(key) ?? null,
        setItem: (key: string, value: string) => store.set(key, value),
        removeItem: (key: string) => store.delete(key),
        clear: () => store.clear(),
    })
}

describe('requestJson', () => {
    beforeEach(() => {
        axiosRequestMock.mockReset()
        installLocalStorage()
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    test('unwraps normal JSON responses including backend Mono responses', async () => {
        const fetchMock = vi.fn(() => jsonResponse(apiResponse({name: 'CyberMario'})))
        vi.stubGlobal('fetch', fetchMock)
        axiosRequestMock.mockResolvedValueOnce(axiosResponse(apiResponse({name: 'CyberMario'})))

        await expect(requestJson<{ name: string }>('/api/profile', {auth: false})).resolves.toEqual({
            name: 'CyberMario',
        })
        expect(axiosRequestMock).toHaveBeenCalledWith(expect.objectContaining({
            data: undefined,
            method: 'GET',
            url: '/api/profile',
        }))
        expect(fetchMock).not.toHaveBeenCalled()
    })

    test('rejects business errors with ApiRequestError details', async () => {
        axiosRequestMock.mockResolvedValueOnce(axiosResponse(
            apiResponse(null, {code: 'RBAC_DENIED', message: '无权访问', traceId: 'trace-1'}),
        ))

        await expect(requestJson('/api/admin/users', {auth: false})).rejects.toMatchObject({
            name: 'ApiRequestError',
            code: 'RBAC_DENIED',
            message: '无权访问',
            status: 200,
            traceId: 'trace-1',
        })
    })

    test('uses friendly fallback messages for empty forbidden responses', async () => {
        axiosRequestMock.mockResolvedValueOnce(axiosResponse('', {status: 403}))

        await expect(requestJson('/api/me/profile')).rejects.toMatchObject({
            name: 'ApiRequestError',
            code: 'HTTP_403',
            message: '没有权限执行该操作',
            status: 403,
        })
    })

    test('refreshes access token and retries normal requests through axios', async () => {
        localStorage.setItem('cyber-mario-access-token', 'old-access-token')
        localStorage.setItem('cyber-mario-refresh-token', 'refresh-token')
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(jsonResponse(apiResponse(null), {status: 401}))
            .mockResolvedValueOnce(jsonResponse(apiResponse({
                accessToken: 'new-access-token',
                refreshToken: 'new-refresh-token',
                accessTokenExpiresInSeconds: 3600,
                refreshTokenExpiresInSeconds: 7200,
            })))
            .mockResolvedValueOnce(jsonResponse(apiResponse({username: 'mario'})))
        vi.stubGlobal('fetch', fetchMock)
        axiosRequestMock
            .mockResolvedValueOnce(axiosResponse(apiResponse(null), {status: 401}))
            .mockResolvedValueOnce(axiosResponse(apiResponse({
                accessToken: 'new-access-token',
                refreshToken: 'new-refresh-token',
                accessTokenExpiresInSeconds: 3600,
                refreshTokenExpiresInSeconds: 7200,
            })))
            .mockResolvedValueOnce(axiosResponse(apiResponse({username: 'mario'})))

        await expect(requestJson<{ username: string }>('/api/me/profile')).resolves.toEqual({username: 'mario'})

        expect(axiosRequestMock).toHaveBeenNthCalledWith(1, expect.objectContaining({
            url: '/api/me/profile',
        }))
        expect(axiosRequestMock).toHaveBeenNthCalledWith(2, expect.objectContaining({
            data: {refreshToken: 'refresh-token'},
            method: 'POST',
            url: '/api/auth/refresh',
        }))
        expect(axiosRequestMock).toHaveBeenNthCalledWith(3, expect.objectContaining({
            url: '/api/me/profile',
        }))
        expect(fetchMock).not.toHaveBeenCalled()
        expect(localStorage.getItem('cyber-mario-access-token')).toBe('new-access-token')
    })
})

describe('requestFormData', () => {
    beforeEach(() => {
        axiosRequestMock.mockReset()
        installLocalStorage()
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    test('sends multipart form data through axios without JSON content type', async () => {
        const formData = new FormData()
        formData.append('file', new Blob(['hello']), 'hello.txt')
        const fetchMock = vi.fn(() => jsonResponse(apiResponse({uploaded: true})))
        vi.stubGlobal('fetch', fetchMock)
        axiosRequestMock.mockResolvedValueOnce(axiosResponse(apiResponse({uploaded: true})))

        await expect(requestFormData<{ uploaded: boolean }>('/api/rag/documents/upload', formData, {
            auth: false,
        })).resolves.toEqual({uploaded: true})

        expect(axiosRequestMock).toHaveBeenCalledWith(expect.objectContaining({
            data: formData,
            method: 'POST',
            url: '/api/rag/documents/upload',
        }))
        const axiosRequest = axiosRequestMock.mock.calls[0][0] as { headers: Record<string, string> }
        expect(axiosRequest.headers).toMatchObject({
            Accept: 'application/json',
        })
        expect(axiosRequest.headers).not.toHaveProperty('Content-Type')
        expect(fetchMock).not.toHaveBeenCalled()
    })
})

describe('streamJsonLines', () => {
    beforeEach(() => {
        axiosRequestMock.mockReset()
        installLocalStorage()
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    test('reads fragmented NDJSON chunks from streaming responses', async () => {
        vi.stubGlobal('fetch', vi.fn(() => ndjsonResponse([
            '{"type":"delta","data":{"content":"Hel',
            'lo"}}\n{"type":"done","data":{"finishReason":"stop"}}\n',
        ])))
        const chunks: unknown[] = []

        await streamJsonLines('/api/rag/chat/stream', {
            body: {question: 'hello'},
            signal: new AbortController().signal,
        }, (chunk) => chunks.push(chunk))

        expect(axiosRequestMock).not.toHaveBeenCalled()
        expect(chunks).toEqual([
            {type: 'delta', data: {content: 'Hello'}},
            {type: 'done', data: {finishReason: 'stop'}},
        ])
    })

    test('uses backend ApiResponse details for failed streaming responses', async () => {
        vi.stubGlobal('fetch', vi.fn(() => jsonResponse(
            apiResponse(null, {code: 'RAG_STREAM_FAILED', message: '流式请求失败', traceId: 'trace-stream'}),
            {status: 503},
        )))

        await expect(streamJsonLines('/api/rag/chat/stream', {
            body: {question: 'hello'},
            signal: new AbortController().signal,
        }, vi.fn())).rejects.toMatchObject({
            name: 'ApiRequestError',
            code: 'RAG_STREAM_FAILED',
            message: '流式请求失败',
            status: 503,
            traceId: 'trace-stream',
        } satisfies Partial<ApiRequestError>)
    })
})
