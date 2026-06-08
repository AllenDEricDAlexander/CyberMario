import type {ChatResponse} from '../types/chat'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export async function streamServerSentEvents(
    endpoint: string,
    request: {
        body: unknown
        signal: AbortSignal
    },
    onChunk: (chunk: ChatResponse) => void,
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
            const lineData = normalizeSseDataLine(line)
            if (lineData === null) {
                continue
            }

            if (!lineData) {
                dataLines = processEventDataLines(dataLines, onChunk)
            } else {
                dataLines.push(lineData)
            }
        }
    }

    buffer += decoder.decode()
    const finalLines = buffer.replaceAll('\r\n', '\n').split('\n')
    for (const line of finalLines) {
        const lineData = normalizeSseDataLine(line)
        if (lineData === null) {
            continue
        }

        if (!lineData) {
            dataLines = processEventDataLines(dataLines, onChunk)
        } else {
            dataLines.push(lineData)
        }
    }
    processEventDataLines(dataLines, onChunk)
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
        return {threadId: '', message: data}
    }
}

function normalizeSseDataLine(line: string): string | null {
    if (line.startsWith(':')) {
        return null
    }

    if (!line.startsWith('data:')) {
        return null
    }

    return line.slice(5).trimStart()
}

function processEventDataLines(
    lines: string[],
    onChunk: (chunk: ChatResponse) => void,
) {
    if (lines.length === 0) {
        return []
    }

    const chunk = parseServerSentEvent(lines.join('\n'))
    if (chunk) {
        onChunk(chunk)
    }

    return []
}
