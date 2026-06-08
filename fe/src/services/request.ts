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
            Accept: 'text/event-stream',
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
    let dataLines: string[] = []

    while (true) {
        const {done, value} = await reader.read()
        if (done) {
            break
        }

        buffer += decoder.decode(value, {stream: true})
        const lines = buffer.replaceAll('\r\n', '\n').split('\n')
        buffer = lines.pop() ?? ''

        for (const line of lines) {
            dataLines = await processServerSentEventLine(line, dataLines, onChunk)
        }
    }

    buffer += decoder.decode()
    const finalLines = buffer.replaceAll('\r\n', '\n').split('\n')
    for (const line of finalLines) {
        dataLines = await processServerSentEventLine(line, dataLines, onChunk)
    }
    await processEventDataLines(dataLines, onChunk)
}

export function resolveErrorMessage(error: unknown) {
    if (error instanceof Error) {
        return error.message
    }
    return '请求失败，请稍后重试'
}

function parseServerSentEvent(event: string): ChatResponse | null {
    const data = event.trim()
    if (!data || data === '[DONE]') {
        return null
    }

    try {
        return JSON.parse(data) as ChatResponse
    } catch {
        return {threadId: '', message: event}
    }
}

type ServerSentEventLine =
    | { type: 'data'; value: string }
    | { type: 'dispatch' }
    | { type: 'raw'; value: string }

// SSE frames are committed by a blank line, so data lines must be buffered before parsing.
function readServerSentEventLine(line: string): ServerSentEventLine | null {
    const trimmed = line.trim()
    if (!trimmed) {
        return {type: 'dispatch'}
    }

    if (trimmed.startsWith(':')) {
        return null
    }

    if (line.startsWith('data:')) {
        return {type: 'data', value: line.slice(5).trimStart()}
    }

    if (line === 'data') {
        return {type: 'data', value: ''}
    }

    if (trimmed.startsWith('{') || trimmed === '[DONE]') {
        return {type: 'raw', value: trimmed}
    }

    return null
}

async function processServerSentEventLine(
    line: string,
    dataLines: string[],
    onChunk: StreamChunkHandler,
) {
    const eventLine = readServerSentEventLine(line)
    if (eventLine === null) {
        return dataLines
    }

    if (eventLine.type === 'dispatch') {
        return processEventDataLines(dataLines, onChunk)
    }

    if (eventLine.type === 'raw') {
        await processEventDataLines(dataLines, onChunk)
        await processEventDataLines([eventLine.value], onChunk)
        return []
    }

    dataLines.push(eventLine.value)
    return dataLines
}

async function processEventDataLines(
    lines: string[],
    onChunk: StreamChunkHandler,
) {
    if (lines.length === 0) {
        return []
    }

    const chunk = parseServerSentEvent(lines.join('\n'))
    if (chunk) {
        onChunk(chunk)
        await yieldAfterStreamChunk()
    }

    return []
}

function yieldAfterStreamChunk() {
    return new Promise<void>((resolve) => {
        setTimeout(resolve, 0)
    })
}
