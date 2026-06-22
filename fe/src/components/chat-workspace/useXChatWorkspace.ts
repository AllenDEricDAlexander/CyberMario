import {useCallback, useEffect, useMemo, useRef, useState} from 'react'
import {useXChat} from '@ant-design/x-sdk'
import type {
    ChatWorkspaceMessage,
    ChatWorkspaceMessageInfo,
    ChatWorkspaceRequest,
} from './chatWorkspaceTypes'

type SetChatWorkspaceMessages = (
    messages: ChatWorkspaceMessage[] | ((messages: ChatWorkspaceMessage[]) => ChatWorkspaceMessage[])
) => boolean

export type UseXChatWorkspaceResult = {
    messages: ChatWorkspaceMessage[]
    messageInfos: ChatWorkspaceMessageInfo[]
    parsedMessages: ChatWorkspaceMessageInfo[]
    isDefaultMessagesRequesting: boolean
    setMessages: SetChatWorkspaceMessages
    updateAssistantMessage: (
        assistantId: string,
        updater: (message: ChatWorkspaceMessage) => ChatWorkspaceMessage
    ) => boolean
    removeMessage: (id: string | number) => boolean
    request: (requestParams: ChatWorkspaceRequest) => void
    abort: () => void
    isRequesting: boolean
}

export type UseXChatWorkspaceOptions = {
    conversationKey?: string
    defaultMessages: ChatWorkspaceMessage[]
    onRequest: (request: ChatWorkspaceRequest, assistantId: string) => Promise<void>
    onAbort: () => void
}

export function toMessageInfo(message: ChatWorkspaceMessage): ChatWorkspaceMessageInfo {
    return {
        id: message.id,
        message,
        status: message.status,
    }
}

export function createAssistantPlaceholder(id: string, question?: string): ChatWorkspaceMessage {
    return {
        id,
        role: 'assistant',
        content: '',
        question,
        status: 'loading',
    }
}

export function updateAssistantMessage(
    messages: ChatWorkspaceMessage[],
    assistantId: string,
    updater: (message: ChatWorkspaceMessage) => ChatWorkspaceMessage
): ChatWorkspaceMessage[] {
    let updated = false
    const nextMessages = messages.map(message => {
        if (message.id !== assistantId) {
            return message
        }

        updated = true
        return updater(message)
    })

    return updated ? nextMessages : messages
}

export function markMessageAborted(message: ChatWorkspaceMessage): ChatWorkspaceMessage {
    return {
        ...message,
        content: message.content.trim() ? message.content : 'Stopped.',
        status: 'abort',
    }
}

export function markMessageInfoAborted(messageInfo: ChatWorkspaceMessageInfo): ChatWorkspaceMessageInfo {
    return toMessageInfo(markMessageAborted({
        ...messageInfo.message,
        status: messageInfo.status,
    }))
}

export function markMessageSucceeded(message: ChatWorkspaceMessage): ChatWorkspaceMessage {
    if (message.status === 'error' || message.status === 'abort') {
        return message
    }

    return {
        ...message,
        status: 'success',
    }
}

export function getRequestErrorMessage(error: unknown): string {
    if (error instanceof Error && error.message.trim()) {
        return error.message
    }

    if (typeof error === 'string' && error.trim()) {
        return error
    }

    if (
        typeof error === 'object' &&
        error !== null &&
        'message' in error &&
        typeof error.message === 'string' &&
        error.message.trim()
    ) {
        return error.message
    }

    return 'Request failed.'
}

export function canUpdateRequestLifecycleState(
    isMounted: boolean,
    requestInFlightAssistantId: string | null,
    assistantId: string,
): boolean {
    return isMounted && requestInFlightAssistantId === assistantId
}

export function useXChatWorkspace(options: UseXChatWorkspaceOptions): UseXChatWorkspaceResult {
    const {conversationKey, defaultMessages, onRequest, onAbort} = options
    const [isRequesting, setIsRequesting] = useState(false)
    const requestInFlightRef = useRef<string | null>(null)
    const isMountedRef = useRef(true)
    // Keep the SDK store key stable because backend session ids may arrive during streaming.
    // Pages reset messages explicitly with setMessages when switching conversations.
    const [sdkConversationKey] = useState(() => conversationKey || createMessageId('conversation'))
    const defaultMessageInfos = useMemo(() => defaultMessages.map(toMessageInfo), [defaultMessages])
    const parser = useCallback((message: ChatWorkspaceMessage) => message, [])

    useEffect(() => {
        isMountedRef.current = true

        return () => {
            isMountedRef.current = false
        }
    }, [])

    const chat = useXChat<ChatWorkspaceMessage, ChatWorkspaceMessage, ChatWorkspaceRequest>({
        conversationKey: sdkConversationKey,
        defaultMessages: defaultMessageInfos,
        parser,
    })
    const {
        messages: messageInfos,
        parsedMessages,
        isDefaultMessagesRequesting,
        removeMessage,
        setMessage: setSdkMessage,
        setMessages: setSdkMessages,
    } = chat

    const messages = useMemo(
        () => messageInfos.map(messageInfo => ({...messageInfo.message, status: messageInfo.status})),
        [messageInfos]
    )

    const setMessages = useCallback<SetChatWorkspaceMessages>(
        next => setSdkMessages(currentMessageInfos => {
            const currentMessages = currentMessageInfos.map(messageInfo => ({
                ...messageInfo.message,
                status: messageInfo.status,
            }))
            const nextMessages = typeof next === 'function' ? next(currentMessages) : next

            return nextMessages.map(toMessageInfo)
        }),
        [setSdkMessages]
    )

    const updateAssistantMessageById = useCallback(
        (assistantId: string, updater: (message: ChatWorkspaceMessage) => ChatWorkspaceMessage) =>
            setSdkMessage(assistantId, messageInfo => toMessageInfo(updater({
                ...messageInfo.message,
                status: messageInfo.status,
            }))),
        [setSdkMessage]
    )

    const request = useCallback((requestParams: ChatWorkspaceRequest) => {
        const message = requestParams.message.trim()

        if (!message || requestInFlightRef.current) {
            return
        }

        const userId = createMessageId('user')
        const assistantId = createMessageId('assistant')
        requestInFlightRef.current = assistantId

        const userMessage: ChatWorkspaceMessage = {
            id: userId,
            role: 'user',
            content: message,
            status: 'local',
        }
        const assistantMessage = createAssistantPlaceholder(assistantId, message)

        setSdkMessages(currentMessageInfos => [
            ...currentMessageInfos,
            toMessageInfo(userMessage),
            toMessageInfo(assistantMessage),
        ])
        setIsRequesting(true)
        void Promise.resolve()
            .then(() => onRequest({...requestParams, message}, assistantId))
            .catch(error => {
                if (!canUpdateRequestLifecycleState(isMountedRef.current, requestInFlightRef.current, assistantId)) {
                    return
                }

                const errorMessage = getRequestErrorMessage(error)

                setSdkMessage(assistantId, messageInfo => {
                    const currentMessage = {
                        ...messageInfo.message,
                        status: messageInfo.status,
                    }

                    return toMessageInfo({
                        ...currentMessage,
                        content: currentMessage.content.trim() ? currentMessage.content : errorMessage,
                        error: errorMessage,
                        status: 'error',
                    })
                })
            })
            .finally(() => {
                if (!canUpdateRequestLifecycleState(isMountedRef.current, requestInFlightRef.current, assistantId)) {
                    return
                }

                requestInFlightRef.current = null
                setIsRequesting(false)
            })
    }, [onRequest, setSdkMessage, setSdkMessages])

    const abort = useCallback(() => {
        const activeAssistantId = requestInFlightRef.current
        if (!isMountedRef.current) {
            return
        }

        if (activeAssistantId && canUpdateRequestLifecycleState(
            isMountedRef.current,
            requestInFlightRef.current,
            activeAssistantId,
        )) {
            setSdkMessage(activeAssistantId, markMessageInfoAborted)
        }

        onAbort()
        requestInFlightRef.current = null
        setIsRequesting(false)
    }, [onAbort, setSdkMessage])

    return {
        messages,
        messageInfos,
        parsedMessages,
        isDefaultMessagesRequesting,
        setMessages,
        updateAssistantMessage: updateAssistantMessageById,
        removeMessage,
        request,
        abort,
        isRequesting,
    }
}

function createMessageId(prefix: 'assistant' | 'conversation' | 'user'): string {
    const randomId = globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2)

    return `${prefix}-${randomId}`
}
