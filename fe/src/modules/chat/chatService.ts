import {streamJsonLines} from '../../services/request'
import type {ChatResponse} from './chatTypes'

const CHAT_ENDPOINT = '/demo/chat/stream'

export function streamChatResponse(
    request: {
        message: string
        threadId: string
        signal: AbortSignal
    },
    onChunk: (chunk: ChatResponse) => void,
) {
    return streamJsonLines<ChatResponse>(
        CHAT_ENDPOINT,
        {
            body: {
                message: request.message,
                threadId: request.threadId,
            },
            signal: request.signal,
        },
        onChunk,
    )
}
