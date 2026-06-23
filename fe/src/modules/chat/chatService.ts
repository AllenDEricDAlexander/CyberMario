import {streamJsonLines} from '../../services/request'
import type {ChatRequest, ChatResponse} from './chatTypes'

const CHAT_ENDPOINT = '/demo/chat/stream'

export function streamChatResponse(
    request: ChatRequest & { signal: AbortSignal },
    onChunk: (chunk: ChatResponse) => void,
) {
    return streamJsonLines<ChatResponse>(
        CHAT_ENDPOINT,
        {
            body: {
                message: request.message,
                threadId: request.threadId,
                sessionId: request.sessionId,
                memoryContextEnabled: request.memoryContextEnabled,
            },
            signal: request.signal,
        },
        onChunk,
    )
}
