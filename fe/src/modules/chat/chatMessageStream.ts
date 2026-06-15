import type {ChatMessage, ChatResponse} from './chatTypes'

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

function mergeStreamText(currentText: string, chunkText: string) {
    if (!chunkText) {
        return currentText
    }
    if (!currentText || chunkText.startsWith(currentText)) {
        return chunkText
    }
    if (currentText.endsWith(chunkText)) {
        return currentText
    }
    return `${currentText}${chunkText}`
}
