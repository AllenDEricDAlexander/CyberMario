import {afterEach, beforeEach, describe, expect, test, vi} from 'vitest'
import {ApiRequestError} from '../types/api'
import {requestJson, streamJsonLines} from './request'

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
        installLocalStorage()
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    test('unwraps normal JSON responses including backend Mono responses', async () => {
        const fetchMock = vi.fn(async () => jsonResponse(apiResponse({name: 'CyberMario'})))
        vi.stubGlobal('fetch', fetchMock)

        await expect(requestJson<{ name: string }>('/api/profile', {auth: false})).resolves.toEqual({
            name: 'CyberMario',
        })
        expect(fetchMock).toHaveBeenCalledWith('/api/profile', expect.objectContaining({
            method: 'GET',
        }))
    })

    test('rejects business errors with ApiRequestError details', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(
            apiResponse(null, {code: 'RBAC_DENIED', message: '无权访问', traceId: 'trace-1'}),
        )))

        await expect(requestJson('/api/admin/users', {auth: false})).rejects.toMatchObject({
            name: 'ApiRequestError',
            code: 'RBAC_DENIED',
            message: '无权访问',
            status: 200,
            traceId: 'trace-1',
        })
    })
})

describe('streamJsonLines', () => {
    beforeEach(() => {
        installLocalStorage()
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    test('reads fragmented NDJSON chunks from streaming responses', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => ndjsonResponse([
            '{"type":"delta","data":{"content":"Hel',
            'lo"}}\n{"type":"done","data":{"finishReason":"stop"}}\n',
        ])))
        const chunks: unknown[] = []

        await streamJsonLines('/api/rag/chat/stream', {
            body: {question: 'hello'},
            signal: new AbortController().signal,
        }, (chunk) => chunks.push(chunk))

        expect(chunks).toEqual([
            {type: 'delta', data: {content: 'Hello'}},
            {type: 'done', data: {finishReason: 'stop'}},
        ])
    })

    test('uses backend ApiResponse details for failed streaming responses', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(
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
