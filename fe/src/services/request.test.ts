import {afterEach, beforeEach, describe, expect, test, vi} from 'vitest'
import {logout} from '../modules/auth/authService'
import {ApiRequestError} from '../types/api'
import {requestFormData, requestJson, streamJsonLines, streamServerSentEvents} from './request'

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

function streamResponse(chunks: string[]) {
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

function installDocumentCookie(cookie = '') {
    vi.stubGlobal('document', {
        cookie,
    })
}

describe('requestJson', () => {
    beforeEach(() => {
        axiosRequestMock.mockReset()
        installLocalStorage()
        installDocumentCookie('XSRF-TOKEN=csrf-request')
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

    test('includes browser client header and axios credentials without authorization header', async () => {
        axiosRequestMock.mockResolvedValueOnce(axiosResponse(apiResponse({name: 'Mario'})))

        await requestJson('/api/me/profile')

        const request = axiosRequestMock.mock.calls[0][0] as {
            headers: Record<string, string>
            withCredentials?: boolean
        }
        expect(request.headers).toMatchObject({
            'X-Client-Type': 'browser',
        })
        expect(request.withCredentials).toBe(true)
        expect(request.headers).not.toHaveProperty('Authorization')
    })

    test('initializes csrf before unsafe requests when csrf cookie is missing', async () => {
        installDocumentCookie()
        axiosRequestMock
            .mockImplementationOnce(() => {
                document.cookie = 'XSRF-TOKEN=csrf-init'
                return Promise.resolve(axiosResponse(apiResponse(null)))
            })
            .mockResolvedValueOnce(axiosResponse(apiResponse({loggedIn: true})))

        await requestJson('/api/auth/login', {
            method: 'POST',
            body: {username: 'mario', password: 'secret'},
            auth: false,
        })

        expect(axiosRequestMock).toHaveBeenNthCalledWith(1, expect.objectContaining({
            method: 'GET',
            url: '/api/auth/csrf',
            withCredentials: true,
        }))
        expect(axiosRequestMock.mock.calls[0][0].headers).toMatchObject({
            'X-Client-Type': 'browser',
        })
        expect(axiosRequestMock).toHaveBeenNthCalledWith(2, expect.objectContaining({
            method: 'POST',
            url: '/api/auth/login',
        }))
    })

    test('sends csrf header on unsafe requests when csrf cookie exists', async () => {
        installDocumentCookie('XSRF-TOKEN=csrf-1')
        axiosRequestMock.mockResolvedValueOnce(axiosResponse(apiResponse(null)))

        await requestJson('/api/auth/logout', {
            method: 'POST',
        })

        const request = axiosRequestMock.mock.calls[0][0] as { headers: Record<string, string> }
        expect(request.headers).toMatchObject({
            'X-XSRF-TOKEN': 'csrf-1',
        })
    })

    test('refreshes csrf and retries the original unsafe request once when csrf is invalid', async () => {
        installDocumentCookie('XSRF-TOKEN=old-csrf')
        axiosRequestMock
            .mockResolvedValueOnce(axiosResponse(
                apiResponse(null, {code: 'AUTH_CSRF_INVALID', message: 'csrf token is invalid'}),
                {status: 403},
            ))
            .mockImplementationOnce(() => {
                document.cookie = 'XSRF-TOKEN=new-csrf'
                return Promise.resolve(axiosResponse(apiResponse(null)))
            })
            .mockResolvedValueOnce(axiosResponse(apiResponse({loggedOut: true})))

        await expect(requestJson<{ loggedOut: boolean }>('/api/auth/logout', {
            method: 'POST',
        })).resolves.toEqual({loggedOut: true})

        expect(axiosRequestMock).toHaveBeenCalledTimes(3)
        expect(axiosRequestMock).toHaveBeenNthCalledWith(1, expect.objectContaining({
            method: 'POST',
            url: '/api/auth/logout',
        }))
        expect(axiosRequestMock.mock.calls[0][0].headers).toMatchObject({
            'X-XSRF-TOKEN': 'old-csrf',
        })
        expect(axiosRequestMock).toHaveBeenNthCalledWith(2, expect.objectContaining({
            method: 'GET',
            url: '/api/auth/csrf',
            withCredentials: true,
        }))
        expect(axiosRequestMock).toHaveBeenNthCalledWith(3, expect.objectContaining({
            method: 'POST',
            url: '/api/auth/logout',
        }))
        expect(axiosRequestMock.mock.calls[2][0].headers).toMatchObject({
            'X-XSRF-TOKEN': 'new-csrf',
        })
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

    test('refreshes with browser cookies and retries normal requests through axios after 401', async () => {
        installDocumentCookie('XSRF-TOKEN=csrf-refresh')
        const fetchMock = vi.fn()
        vi.stubGlobal('fetch', fetchMock)
        axiosRequestMock
            .mockResolvedValueOnce(axiosResponse(apiResponse(null), {status: 401}))
            .mockResolvedValueOnce(axiosResponse(apiResponse(null)))
            .mockResolvedValueOnce(axiosResponse(apiResponse({username: 'mario'})))

        await expect(requestJson<{ username: string }>('/api/me/profile')).resolves.toEqual({username: 'mario'})

        expect(axiosRequestMock).toHaveBeenNthCalledWith(1, expect.objectContaining({
            url: '/api/me/profile',
        }))
        expect(axiosRequestMock).toHaveBeenNthCalledWith(2, expect.objectContaining({
            data: undefined,
            method: 'POST',
            url: '/api/auth/refresh',
            withCredentials: true,
        }))
        expect(axiosRequestMock.mock.calls[1][0].headers).toMatchObject({
            'X-Client-Type': 'browser',
            'X-XSRF-TOKEN': 'csrf-refresh',
        })
        expect(axiosRequestMock.mock.calls[1][0].data).not.toMatchObject({
            refreshToken: expect.anything(),
        })
        expect(axiosRequestMock).toHaveBeenNthCalledWith(3, expect.objectContaining({
            url: '/api/me/profile',
        }))
        expect(fetchMock).not.toHaveBeenCalled()
    })

    test('renews csrf and retries cookie refresh once when refresh csrf is invalid', async () => {
        installDocumentCookie('XSRF-TOKEN=old-refresh-csrf')
        const fetchMock = vi.fn()
        vi.stubGlobal('fetch', fetchMock)
        axiosRequestMock
            .mockResolvedValueOnce(axiosResponse(apiResponse(null), {status: 401}))
            .mockResolvedValueOnce(axiosResponse(
                apiResponse(null, {code: 'AUTH_CSRF_INVALID', message: 'csrf token is invalid'}),
                {status: 403},
            ))
            .mockImplementationOnce(() => {
                document.cookie = 'XSRF-TOKEN=new-refresh-csrf'
                return Promise.resolve(axiosResponse(apiResponse(null)))
            })
            .mockResolvedValueOnce(axiosResponse(apiResponse(null)))
            .mockResolvedValueOnce(axiosResponse(apiResponse({username: 'mario'})))

        await expect(requestJson<{ username: string }>('/api/me/profile')).resolves.toEqual({username: 'mario'})

        expect(axiosRequestMock).toHaveBeenCalledTimes(5)
        expect(axiosRequestMock).toHaveBeenNthCalledWith(1, expect.objectContaining({
            method: 'GET',
            url: '/api/me/profile',
        }))
        expect(axiosRequestMock).toHaveBeenNthCalledWith(2, expect.objectContaining({
            data: undefined,
            method: 'POST',
            url: '/api/auth/refresh',
            withCredentials: true,
        }))
        expect(axiosRequestMock.mock.calls[1][0].headers).toMatchObject({
            'X-XSRF-TOKEN': 'old-refresh-csrf',
        })
        expect(axiosRequestMock).toHaveBeenNthCalledWith(3, expect.objectContaining({
            method: 'GET',
            url: '/api/auth/csrf',
            withCredentials: true,
        }))
        expect(axiosRequestMock).toHaveBeenNthCalledWith(4, expect.objectContaining({
            data: undefined,
            method: 'POST',
            url: '/api/auth/refresh',
            withCredentials: true,
        }))
        expect(axiosRequestMock.mock.calls[3][0].headers).toMatchObject({
            'X-XSRF-TOKEN': 'new-refresh-csrf',
        })
        expect(axiosRequestMock.mock.calls[3][0].data).not.toMatchObject({
            refreshToken: expect.anything(),
        })
        expect(axiosRequestMock).toHaveBeenNthCalledWith(5, expect.objectContaining({
            method: 'GET',
            url: '/api/me/profile',
        }))
        expect(fetchMock).not.toHaveBeenCalled()
    })
})

describe('requestFormData', () => {
    beforeEach(() => {
        axiosRequestMock.mockReset()
        installLocalStorage()
        installDocumentCookie('XSRF-TOKEN=csrf-form')
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
        installDocumentCookie('XSRF-TOKEN=csrf-stream')
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

    test('sends credentials and csrf header on streaming POST requests', async () => {
        installDocumentCookie('XSRF-TOKEN=csrf-stream')
        const fetchMock = vi.fn((..._args: Parameters<typeof fetch>): ReturnType<typeof fetch> => Promise.resolve(
            ndjsonResponse([
                '{"type":"done"}\n',
            ]),
        ))
        vi.stubGlobal('fetch', fetchMock)

        await streamJsonLines('/api/rag/chat/stream', {
            body: {question: 'hello'},
            signal: new AbortController().signal,
        }, vi.fn())

        const request = fetchMock.mock.calls[0][1] as {
            credentials?: RequestCredentials
            headers: Record<string, string>
        }
        expect(request.credentials).toBe('same-origin')
        expect(request.headers).toMatchObject({
            'X-XSRF-TOKEN': 'csrf-stream',
        })
    })

    test('refreshes csrf and retries streaming POST once when csrf is invalid', async () => {
        installDocumentCookie('XSRF-TOKEN=old-stream-csrf')
        let fetchCallCount = 0
        const fetchMock = vi.fn((..._args: Parameters<typeof fetch>): ReturnType<typeof fetch> => Promise.resolve(
            ++fetchCallCount === 1
                ? jsonResponse(
                    apiResponse(null, {code: 'AUTH_CSRF_INVALID', message: 'csrf token is invalid'}),
                    {status: 403},
                )
                : ndjsonResponse(['{"type":"done"}\n']),
        ))
        vi.stubGlobal('fetch', fetchMock)
        axiosRequestMock.mockImplementationOnce(() => {
            document.cookie = 'XSRF-TOKEN=new-stream-csrf'
            return Promise.resolve(axiosResponse(apiResponse(null)))
        })

        await streamJsonLines('/api/rag/chat/stream', {
            body: {question: 'hello'},
            signal: new AbortController().signal,
        }, vi.fn())

        expect(fetchMock).toHaveBeenCalledTimes(2)
        expect(axiosRequestMock).toHaveBeenCalledTimes(1)
        expect(axiosRequestMock).toHaveBeenCalledWith(expect.objectContaining({
            method: 'GET',
            url: '/api/auth/csrf',
            withCredentials: true,
        }))
        expect((fetchMock.mock.calls[0][1] as { headers: Record<string, string> }).headers).toMatchObject({
            'X-XSRF-TOKEN': 'old-stream-csrf',
        })
        expect((fetchMock.mock.calls[1][1] as { headers: Record<string, string> }).headers).toMatchObject({
            'X-XSRF-TOKEN': 'new-stream-csrf',
        })
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

describe('authService.logout', () => {
    beforeEach(() => {
        axiosRequestMock.mockReset()
        installLocalStorage()
        installDocumentCookie('XSRF-TOKEN=csrf-logout')
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    test('sends browser cookie logout request without requiring a stored refresh token', async () => {
        axiosRequestMock.mockResolvedValueOnce(axiosResponse(apiResponse(null)))

        await logout()

        expect(axiosRequestMock).toHaveBeenCalledWith(expect.objectContaining({
            data: undefined,
            method: 'POST',
            url: '/api/auth/logout',
            withCredentials: true,
        }))
        expect(axiosRequestMock.mock.calls[0][0].headers).toMatchObject({
            'X-Client-Type': 'browser',
            'X-XSRF-TOKEN': 'csrf-logout',
        })
    })
})

describe('streamServerSentEvents', () => {
    beforeEach(() => {
        axiosRequestMock.mockReset()
        installLocalStorage()
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    test('reads fragmented server-sent event data from streaming responses', async () => {
        const fetchMock = vi.fn((..._args: Parameters<typeof fetch>): ReturnType<typeof fetch> => Promise.resolve(streamResponse([
            'id: 1\nevent: ROOM_CREATED\ndata: {"seqNo":1,',
            '"eventType":"ROOM_CREATED"}\n\n',
            'id: 2\nevent: PLAYER_JOINED\ndata: {"seqNo":2,"eventType":"PLAYER_JOINED"}\n\n',
        ])))
        vi.stubGlobal('fetch', fetchMock)
        const events: unknown[] = []

        await streamServerSentEvents('/api/clocktower/rooms/7/events/stream?seatId=3', {
            signal: new AbortController().signal,
        }, (event) => events.push(event))

        expect(axiosRequestMock).not.toHaveBeenCalled()
        expect(events).toEqual([
            {seqNo: 1, eventType: 'ROOM_CREATED'},
            {seqNo: 2, eventType: 'PLAYER_JOINED'},
        ])
        expect(fetchMock).toHaveBeenCalledWith('/api/clocktower/rooms/7/events/stream?seatId=3', expect.objectContaining({
            method: 'GET',
            signal: expect.any(AbortSignal),
        }))
        const request = fetchMock.mock.calls[0][1] as { headers: Record<string, string> }
        expect(request.headers).toMatchObject({Accept: 'text/event-stream'})
        expect(request.headers).not.toHaveProperty('Content-Type')
    })
})
