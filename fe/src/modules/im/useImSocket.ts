import {useCallback, useEffect, useMemo} from 'react'
import {API_BASE_URL} from '../../services/request'
import {createImWsTicket} from './imService'
import type {
    ImClientFrame,
    ImClientFrameFor,
    ImClientFrameType,
    ImMessagePushPayload,
    ImResyncPayload,
    ImServerFrameInput,
    MessageView,
    SendMessageRequest,
    UnreadView,
    WsTicketRequest,
    WsTicketView,
} from './imTypes'

type ImWebSocketLike = {
    readyState: number
    send(frame: string): void
    close(): void
    addEventListener(type: string, listener: (event?: {data?: string}) => void): void
    removeEventListener(type: string, listener: (event?: {data?: string}) => void): void
}

type ImWebSocketConstructor = {
    OPEN?: number
    new(url: string): ImWebSocketLike
}

type ImLocation = {
    protocol: string
    host: string
}

export type ImSocketOptions = {
    enabled?: boolean
    activeConversationId?: number
    lastSeq?: number
    pingIntervalMs?: number
    apiBaseUrl?: string
    location?: ImLocation
    WebSocketCtor?: ImWebSocketConstructor
    ticketLoader?: (request?: WsTicketRequest) => Promise<WsTicketView>
    onMessagePush?: (payload: ImMessagePushPayload) => void
    onSendAck?: (message: MessageView) => void
    onReadUpdate?: (unread: UnreadView) => void
    onResync?: (payload: ImResyncPayload) => void
    onError?: (error: unknown) => void
}

export type ImSocketController = {
    connect: () => Promise<void>
    sendMessage: (request: SendMessageRequest) => boolean
    markRead: (request: {conversationId: number; messageSeq: number}) => boolean
    reconnect: () => Promise<void>
    disconnect: () => void
}

export type ResolvedImSocketOptions = ImSocketOptions & Required<Pick<
    ImSocketOptions,
    'apiBaseUrl' | 'enabled' | 'pingIntervalMs' | 'ticketLoader'
>>

export function buildUseImSocketControllerOptions(options: ImSocketOptions): ImSocketOptions {
    return compactImSocketOptions(options)
}

export function resolveImSocketOptions(options: ImSocketOptions): ResolvedImSocketOptions {
    return {
        enabled: true,
        pingIntervalMs: 30000,
        ticketLoader: createImWsTicket,
        apiBaseUrl: API_BASE_URL,
        ...compactImSocketOptions(options),
    }
}

export function createImSocketController(options: ImSocketOptions): ImSocketController {
    const socketOptions = resolveImSocketOptions(options)
    let socket: ImWebSocketLike | undefined
    let pendingConnect: Promise<void> | undefined
    let connectionGeneration = 0
    let pingTimer: ReturnType<typeof setInterval> | undefined
    let requestSeq = 0
    const seenPushKeys = new Set<string>()

    async function connect() {
        if (!socketOptions.enabled || socket) {
            return
        }
        if (pendingConnect) {
            return pendingConnect
        }

        const generation = ++connectionGeneration
        const connectPromise = openSocket(generation)
        pendingConnect = connectPromise
        await connectPromise
    }

    async function openSocket(generation: number) {
        try {
            const ticket = await socketOptions.ticketLoader(
                socketOptions.activeConversationId ? {conversationId: socketOptions.activeConversationId} : undefined,
            )
            const WebSocketCtor = socketOptions.WebSocketCtor ?? globalThis.WebSocket
            if (!WebSocketCtor) {
                throw new Error('WebSocket is not available')
            }
            const nextSocket = new WebSocketCtor(buildImSocketUrl(ticket.ticket, socketOptions.apiBaseUrl, socketOptions.location))
            if (generation !== connectionGeneration) {
                nextSocket.close()
                return
            }
            socket = nextSocket
            socket.addEventListener('open', handleOpen)
            socket.addEventListener('message', handleMessage)
            socket.addEventListener('error', handleError)
            socket.addEventListener('close', handleClose)
        } catch (error) {
            if (generation === connectionGeneration) {
                socketOptions.onError?.(error)
            }
        } finally {
            if (generation === connectionGeneration) {
                pendingConnect = undefined
            }
        }
    }

    function handleOpen() {
        pingTimer = setInterval(() => {
            sendFrame('PING', {})
        }, socketOptions.pingIntervalMs)
    }

    function handleMessage(event?: {data?: string}) {
        if (!event?.data) {
            return
        }
        try {
            routeServerFrame(JSON.parse(event.data) as ImServerFrameInput)
        } catch (error) {
            socketOptions.onError?.(error)
        }
    }

    function handleError(error: unknown) {
        socketOptions.onError?.(error)
    }

    function handleClose() {
        clearPingTimer()
        socket = undefined
    }

    function routeServerFrame(frame: ImServerFrameInput) {
        const payload = framePayload(frame)
        if (frame.type === 'SEND_ACK' && isMessage(payload.message)) {
            socketOptions.onSendAck?.(payload.message)
            return
        }
        if (frame.type === 'READ_UPDATED' && isUnread(payload.unread)) {
            socketOptions.onReadUpdate?.(payload.unread)
            return
        }
        if (frame.type === 'READ_UPDATED' && payload.eventType === 'READ_UPDATED') {
            socketOptions.onMessagePush?.(payload as ImMessagePushPayload)
            return
        }
        if (frame.type === 'MESSAGE_PUSH') {
            if (payload.eventType === 'READ_UPDATED' && isUnread(payload.unread)) {
                socketOptions.onReadUpdate?.(payload.unread)
                return
            }
            const pushPayload = payload as ImMessagePushPayload
            if (isDuplicateMessagePush(pushPayload)) {
                return
            }
            socketOptions.onMessagePush?.(pushPayload)
            return
        }
        if (frame.type === 'RESYNC' && typeof payload.reason === 'string') {
            socketOptions.onResync?.(payload as ImResyncPayload)
        }
    }

    function isDuplicateMessagePush(payload: ImMessagePushPayload) {
        if (!payload.message?.clientMsgId || payload.message.senderUserId === null || payload.message.senderUserId === undefined) {
            return false
        }
        const key = `${payload.message.conversationId}:${payload.message.senderUserId}:${payload.message.clientMsgId}`
        if (seenPushKeys.has(key)) {
            return true
        }
        seenPushKeys.add(key)
        return false
    }

    function sendFrame<Type extends ImClientFrameType>(type: Type, payload: ImClientFrameFor<Type>['payload']) {
        const openState = socketOptions.WebSocketCtor?.OPEN ?? globalThis.WebSocket?.OPEN ?? 1
        if (!socket || socket.readyState !== openState) {
            return false
        }
        const cleanPayload = Object.fromEntries(Object.entries(payload).filter(([, value]) => value !== undefined))
        const frame: ImClientFrame = {
            type,
            requestId: `im-${Date.now()}-${++requestSeq}`,
            payload: cleanPayload,
        } as ImClientFrame
        try {
            socket.send(JSON.stringify(frame))
            return true
        } catch (error) {
            socketOptions.onError?.(error)
            return false
        }
    }

    function disconnect() {
        connectionGeneration++
        pendingConnect = undefined
        if (socket) {
            socket.removeEventListener('open', handleOpen)
            socket.removeEventListener('message', handleMessage)
            socket.removeEventListener('error', handleError)
            socket.removeEventListener('close', handleClose)
            socket.close()
            socket = undefined
        }
        clearPingTimer()
    }

    function clearPingTimer() {
        if (pingTimer) {
            clearInterval(pingTimer)
            pingTimer = undefined
        }
    }

    return {
        connect,
        sendMessage: (request) => sendFrame('SEND_MESSAGE', request),
        markRead: (request) => sendFrame('MARK_READ', request),
        reconnect: async () => {
            disconnect()
            await connect()
        },
        disconnect,
    }
}

export function useImSocket(options: ImSocketOptions): ImSocketController {
    const {
        enabled,
        activeConversationId,
        lastSeq,
        pingIntervalMs,
        apiBaseUrl,
        location,
        WebSocketCtor,
        ticketLoader,
        onMessagePush,
        onSendAck,
        onReadUpdate,
        onResync,
        onError,
    } = options
    const controller = useMemo(() => createImSocketController({
        ...buildUseImSocketControllerOptions({
            enabled,
            activeConversationId,
            lastSeq,
            pingIntervalMs,
            apiBaseUrl,
            location,
            WebSocketCtor,
            ticketLoader,
            onMessagePush,
            onSendAck,
            onReadUpdate,
            onResync,
            onError,
        }),
    }), [
        enabled,
        activeConversationId,
        lastSeq,
        pingIntervalMs,
        apiBaseUrl,
        location,
        WebSocketCtor,
        ticketLoader,
        onMessagePush,
        onSendAck,
        onReadUpdate,
        onResync,
        onError,
    ])

    useEffect(() => {
        void controller.connect()
        return () => controller.disconnect()
    }, [controller])

    return {
        connect: controller.connect,
        sendMessage: useCallback((request) => controller.sendMessage(request), [controller]),
        markRead: useCallback((request) => controller.markRead(request), [controller]),
        reconnect: controller.reconnect,
        disconnect: controller.disconnect,
    }
}

export function buildImSocketUrl(ticket: string, apiBaseUrl = '', location?: ImLocation) {
    const baseLocation = location ?? currentLocation()
    const search = new URLSearchParams({ticket}).toString()
    const baseOriginProtocol = baseLocation.protocol === 'https:' ? 'https:' : 'http:'
    const base = new URL(apiBaseUrl || '/', `${baseOriginProtocol}//${baseLocation.host}`)

    base.protocol = base.protocol === 'https:' ? 'wss:' : 'ws:'
    base.pathname = buildImSocketPath(base.pathname)
    base.search = search
    base.hash = ''
    return base.toString()
}

function buildImSocketPath(apiBasePath: string) {
    const normalized = apiBasePath.startsWith('/') ? apiBasePath : `/${apiBasePath}`
    const trimmed = normalized.replace(/\/+$/, '')
    const prefix = trimmed.endsWith('/api') ? trimmed.slice(0, -4) : trimmed
    return `${prefix}/ws/im`.replace(/^\/\//, '/')
}

function currentLocation(): ImLocation {
    if (typeof window !== 'undefined') {
        return window.location
    }
    return {protocol: 'http:', host: 'localhost'}
}

function compactImSocketOptions(options: ImSocketOptions): ImSocketOptions {
    return Object.fromEntries(Object.entries(options).filter(([, value]) => value !== undefined))
}

function framePayload(frame: ImServerFrameInput): Record<string, unknown> {
    return typeof frame.payload === 'object' && frame.payload !== null ? frame.payload : {}
}

function isMessage(value: unknown): value is MessageView {
    return typeof value === 'object' && value !== null && typeof (value as MessageView).conversationId === 'number'
}

function isUnread(value: unknown): value is UnreadView {
    return typeof value === 'object' && value !== null && typeof (value as UnreadView).conversationId === 'number'
}
