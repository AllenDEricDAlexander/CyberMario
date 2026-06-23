export type ChatRole = 'assistant' | 'user'

export type ChatMessage = {
    id: string
    role: ChatRole
    content: string
    thinkContent?: string
}

export type ChatRequest = {
    message: string
    threadId?: string
    sessionId?: string
    memoryContextEnabled?: boolean
}

export type ChatResponse = {
    threadId: string
    message: string
    type: 'think' | 'message' | 'error'
}
