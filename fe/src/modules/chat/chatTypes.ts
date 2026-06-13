export type ChatRole = 'assistant' | 'user'

export type ChatMessage = {
    id: string
    role: ChatRole
    content: string
    thinkContent?: string
}

export type ChatResponse = {
    threadId: string
    message: string
    type: 'think' | 'message'
}
