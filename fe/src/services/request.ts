import type {ChatResponse} from '../types/chat'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

type StreamChunkHandler = (chunk: ChatResponse) => void

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
        headers: {
            Accept: 'application/x-ndjson',
            'Content-Type': 'application/json',
        },
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

export function resolveErrorMessage(error: unknown) {
    if (error instanceof Error) {
        return error.message
    }
    return '请求失败，请稍后重试'
}

