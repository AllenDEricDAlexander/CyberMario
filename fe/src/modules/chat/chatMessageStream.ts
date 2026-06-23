import type {ChatMessage, ChatResponse} from './chatTypes'

const EXACT_SNAPSHOT_DEDUP_MIN_LENGTH = 16

export function appendChatChunk(message: ChatMessage, chunk: ChatResponse): ChatMessage {
    const chunkText = chunk.message ?? ''
    if (chunk.type === 'error') {
        return {
            ...message,
            content: chunkText || '模型调用失败，请检查配置后重试。',
        }
    }
    if (chunk.type === 'think') {
        return {
            ...message,
            thinkContent: mergeStreamText(message.thinkContent ?? '', chunkText),
        }
    }

    return {
        ...message,
        content: mergeStreamText(message.content, chunkText),
    }
}

export function mergeStreamText(currentText: string | null | undefined, chunkText: string | null | undefined): string {
    const current = currentText ?? ''
    const chunk = chunkText ?? ''

    if (!chunk) {
        return current
    }
    if (!current) {
        return chunk
    }
    if (chunk === current && chunk.length >= EXACT_SNAPSHOT_DEDUP_MIN_LENGTH) {
        return current
    }
    if (chunk.length > current.length && chunk.startsWith(current)) {
        return chunk
    }
    return `${current}${chunk}`
}
