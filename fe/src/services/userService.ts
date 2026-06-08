import {API_BASE_URL, streamServerSentEvents} from './request'
import type {ChatResponse} from '../types/chat'

const CHAT_ENDPOINT = `${API_BASE_URL}/demo/chat/stream`

export function streamChatResponse(
    request: {
        message: string
        threadId: string
        signal: AbortSignal
    },
    onChunk: (chunk: ChatResponse) => void,
) {
    return streamServerSentEvents(
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
