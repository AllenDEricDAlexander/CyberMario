import {afterEach, beforeEach, describe, expect, test, vi} from 'vitest'
import {renderToStaticMarkup} from 'react-dom/server'
import {API_BASE_URL} from '../../services/request'
import {createImWsTicket} from './imService'
import {
    buildImSocketUrl,
    buildUseImSocketControllerOptions,
    createImSocketController,
    resolveImSocketOptions,
    useImSocket,
} from './useImSocket'
import type {MessageView, UnreadView, WsTicketView} from './imTypes'

type Listener = (event?: {data?: string}) => void

class MockWebSocket {
    static instances: MockWebSocket[] = []
    static OPEN = 1
    url: string
    readyState = MockWebSocket.OPEN
    sent: string[] = []
    closed = false
    listeners = new Map<string, Listener[]>()

    constructor(url: string) {
        this.url = url
        MockWebSocket.instances.push(this)
    }

    addEventListener(type: string, listener: Listener) {
        const listeners = this.listeners.get(type) ?? []
        listeners.push(listener)
        this.listeners.set(type, listeners)
    }

    removeEventListener(type: string, listener: Listener) {
        this.listeners.set(type, (this.listeners.get(type) ?? []).filter((item) => item !== listener))
    }

    send(frame: string) {
        this.sent.push(frame)
    }

    close() {
        this.closed = true
    }

    emit(type: string, data?: unknown) {
        for (const listener of this.listeners.get(type) ?? []) {
            listener(data === undefined ? undefined : {data: JSON.stringify(data)})
        }
    }
}

const ticket: WsTicketView = {
    ticket: 'ticket value',
    expiresAt: '2026-06-28T12:00:00Z',
}

const message: MessageView = {
    id: 10,
    conversationId: 2,
    senderUserId: 99,
    messageSeq: 5,
    clientMsgId: 'client-1',
    messageType: 'TEXT',
    content: 'Hello',
    payloadJson: null,
    status: 'SENT',
    sentAt: '2026-06-28T10:00:00Z',
    editedAt: null,
    deletedAt: null,
    metadataJson: null,
}

const unread: UnreadView = {
    conversationId: 2,
    userId: 99,
    lastReadSeq: 5,
    unreadCount: 0,
}

describe('createImSocketController', () => {
    beforeEach(() => {
        vi.useFakeTimers()
        MockWebSocket.instances = []
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    test('mints a scoped ticket, opens the socket URL, and pings without a redundant subscription frame', async () => {
        const ticketLoader = vi.fn().mockResolvedValue(ticket)
        const controller = createImSocketController({
            enabled: true,
            activeConversationId: 2,
            lastSeq: 4,
            pingIntervalMs: 1000,
            WebSocketCtor: MockWebSocket,
            ticketLoader,
            location: {protocol: 'https:', host: 'example.com'},
        })

        await controller.connect()
        const socket = MockWebSocket.instances[0]
        socket.emit('open')
        vi.advanceTimersByTime(1000)

        expect(socket.url).toBe('wss://example.com/ws/im?ticket=ticket+value')
        expect(ticketLoader).toHaveBeenCalledWith({conversationId: 2})
        expect(socket.sent.map(parseSentFrame)).toMatchObject([
            {type: 'PING', payload: {}},
        ])
    })

    test('reconnect mints a fresh ticket and keeps sequence recovery on the RESYNC callback', async () => {
        const ticketLoader = vi.fn().mockResolvedValue(ticket)
        const onResync = vi.fn()
        const controller = createImSocketController({
            enabled: true,
            activeConversationId: 2,
            lastSeq: 7,
            WebSocketCtor: MockWebSocket,
            ticketLoader,
            location: {protocol: 'https:', host: 'example.com'},
            onResync,
        })

        await controller.connect()
        const first = MockWebSocket.instances[0]
        first.emit('open')
        first.emit('message', {type: 'RESYNC', payload: {reason: 'gap', conversationId: 2, messageSeq: 9}})
        await controller.reconnect()

        expect(first.closed).toBe(true)
        expect(MockWebSocket.instances).toHaveLength(2)
        expect(ticketLoader).toHaveBeenNthCalledWith(1, {conversationId: 2})
        expect(ticketLoader).toHaveBeenNthCalledWith(2, {conversationId: 2})
        expect(onResync).toHaveBeenCalledWith({reason: 'gap', conversationId: 2, messageSeq: 9})
    })

    test('preserves enabled and ping defaults when optional controller options are explicit undefined', async () => {
        const ticketLoader = vi.fn().mockResolvedValue(ticket)
        const controller = createImSocketController({
            enabled: undefined,
            pingIntervalMs: undefined,
            WebSocketCtor: MockWebSocket,
            ticketLoader,
            location: {protocol: 'https:', host: 'example.com'},
        })

        await controller.connect()
        const socket = MockWebSocket.instances[0]
        socket.emit('open')
        vi.advanceTimersByTime(29999)

        expect(ticketLoader).toHaveBeenCalledTimes(1)
        expect(socket.sent.map(parseSentFrame)).toEqual([])

        vi.advanceTimersByTime(1)

        expect(socket.sent.map(parseSentFrame)).toMatchObject([
            {type: 'PING', payload: {}},
        ])
    })

    test('coalesces duplicate connect calls while ticket loading is pending', async () => {
        let resolveTicket: (nextTicket: WsTicketView) => void = () => undefined
        const ticketLoader = vi.fn(() => new Promise<WsTicketView>((resolve) => {
            resolveTicket = resolve
        }))
        const controller = createImSocketController({
            enabled: true,
            WebSocketCtor: MockWebSocket,
            ticketLoader,
            location: {protocol: 'https:', host: 'example.com'},
        })

        const firstConnect = controller.connect()
        const secondConnect = controller.connect()
        resolveTicket(ticket)
        await Promise.all([firstConnect, secondConnect])

        expect(ticketLoader).toHaveBeenCalledTimes(1)
        expect(MockWebSocket.instances).toHaveLength(1)
    })

    test('does not attach a stale socket when disconnected before ticket loading resolves', async () => {
        let resolveTicket: (nextTicket: WsTicketView) => void = () => undefined
        const ticketLoader = vi.fn(() => new Promise<WsTicketView>((resolve) => {
            resolveTicket = resolve
        }))
        const controller = createImSocketController({
            enabled: true,
            WebSocketCtor: MockWebSocket,
            ticketLoader,
            location: {protocol: 'https:', host: 'example.com'},
        })

        const connect = controller.connect()
        controller.disconnect()
        resolveTicket(ticket)
        await connect

        const staleSocket = MockWebSocket.instances[0]
        expect(staleSocket.closed).toBe(true)
        expect(Array.from(staleSocket.listeners.values()).flat()).toHaveLength(0)
    })

    test('returns false without queueing message and read frames when disconnected', () => {
        const controller = createImSocketController({
            enabled: false,
            WebSocketCtor: MockWebSocket,
            ticketLoader: vi.fn().mockResolvedValue(ticket),
            location: {protocol: 'http:', host: 'localhost:5173'},
        })

        expect(controller.sendMessage({
            conversationId: 2,
            clientMsgId: 'client-2',
            messageType: 'TEXT',
            content: 'Hello',
        })).toBe(false)
        expect(controller.markRead({conversationId: 2, messageSeq: 6})).toBe(false)
        expect(MockWebSocket.instances).toEqual([])
    })

    test('sends message and read frames with request ids when connected', async () => {
        const controller = createImSocketController({
            enabled: true,
            WebSocketCtor: MockWebSocket,
            ticketLoader: vi.fn().mockResolvedValue(ticket),
            location: {protocol: 'http:', host: 'localhost:5173'},
        })

        await controller.connect()
        const socket = MockWebSocket.instances[0]
        socket.emit('open')
        expect(controller.sendMessage({
            conversationId: 2,
            clientMsgId: 'client-2',
            messageType: 'TEXT',
            content: 'Hello',
        })).toBe(true)
        expect(controller.markRead({conversationId: 2, messageSeq: 6})).toBe(true)

        const sentFrames = socket.sent.map(parseSentFrame)

        expect(sentFrames).toMatchObject([
            {
                type: 'SEND_MESSAGE',
                payload: {conversationId: 2, clientMsgId: 'client-2', messageType: 'TEXT', content: 'Hello'},
            },
            {
                type: 'MARK_READ',
                payload: {conversationId: 2, messageSeq: 6},
            },
        ])
        expect(sentFrames.every((frame) => typeof frame.requestId === 'string' && /^im-/.test(frame.requestId))).toBe(true)
    })

    test('returns false and reports an error when socket send throws synchronously', async () => {
        const onError = vi.fn()
        const controller = createImSocketController({
            enabled: true,
            WebSocketCtor: MockWebSocket,
            ticketLoader: vi.fn().mockResolvedValue(ticket),
            location: {protocol: 'http:', host: 'localhost:5173'},
            onError,
        })

        await controller.connect()
        const socket = MockWebSocket.instances[0]
        const error = new Error('send failed')
        vi.spyOn(socket, 'send').mockImplementation(() => {
            throw error
        })
        socket.emit('open')

        expect(controller.sendMessage({
            conversationId: 2,
            clientMsgId: 'client-2',
            messageType: 'TEXT',
            content: 'Hello',
        })).toBe(false)
        expect(onError).toHaveBeenCalledWith(error)
    })

    test('routes server frames, supports realtime read payloads, dedupes detailed message pushes, and closes on disconnect', async () => {
        const onMessagePush = vi.fn()
        const onSendAck = vi.fn()
        const onReadUpdate = vi.fn()
        const onResync = vi.fn()
        const controller = createImSocketController({
            enabled: true,
            WebSocketCtor: MockWebSocket,
            ticketLoader: vi.fn().mockResolvedValue(ticket),
            location: {protocol: 'https:', host: 'example.com'},
            onMessagePush,
            onSendAck,
            onReadUpdate,
            onResync,
        })

        await controller.connect()
        const socket = MockWebSocket.instances[0]
        socket.emit('open')
        socket.emit('message', {type: 'SEND_ACK', payload: {message}})
        socket.emit('message', {type: 'READ_UPDATED', payload: {unread}})
        socket.emit('message', {type: 'MESSAGE_PUSH', payload: {eventType: 'READ_UPDATED', unread}})
        socket.emit('message', {type: 'MESSAGE_PUSH', payload: {eventType: 'MESSAGE_CREATED', message}})
        socket.emit('message', {type: 'MESSAGE_PUSH', payload: {eventType: 'MESSAGE_CREATED', message}})
        socket.emit('message', {type: 'RESYNC', payload: {reason: 'gap', conversationId: 2, messageSeq: 8}})
        controller.disconnect()

        expect(onSendAck).toHaveBeenCalledWith(message)
        expect(onReadUpdate).toHaveBeenCalledTimes(2)
        expect(onReadUpdate).toHaveBeenLastCalledWith(unread)
        expect(onMessagePush).toHaveBeenCalledTimes(1)
        expect(onMessagePush).toHaveBeenCalledWith({eventType: 'MESSAGE_CREATED', message})
        expect(onResync).toHaveBeenCalledWith({reason: 'gap', conversationId: 2, messageSeq: 8})
        expect(socket.closed).toBe(true)
    })

    test('routes outbox-shaped READ_UPDATED frames as generic realtime hints', async () => {
        const onMessagePush = vi.fn()
        const onReadUpdate = vi.fn()
        const controller = createImSocketController({
            enabled: true,
            WebSocketCtor: MockWebSocket,
            ticketLoader: vi.fn().mockResolvedValue(ticket),
            location: {protocol: 'https:', host: 'example.com'},
            onMessagePush,
            onReadUpdate,
        })

        await controller.connect()
        const socket = MockWebSocket.instances[0]
        const payload = {
            eventType: 'READ_UPDATED',
            conversationId: 2,
            messageId: 10,
            messageSeq: 6,
        }
        socket.emit('message', {type: 'READ_UPDATED', payload})

        expect(onReadUpdate).not.toHaveBeenCalled()
        expect(onMessagePush).toHaveBeenCalledWith(payload)
    })
})

describe('buildImSocketUrl', () => {
    test('uses current host /ws/im path when API base is empty', () => {
        expect(buildImSocketUrl('ticket value', '', {protocol: 'http:', host: 'localhost:5173'})).toBe(
            'ws://localhost:5173/ws/im?ticket=ticket+value',
        )
    })

    test('converts absolute API base URLs to backend websocket URLs', () => {
        expect(buildImSocketUrl('ticket value', 'https://api.example.com/api')).toBe(
            'wss://api.example.com/ws/im?ticket=ticket+value',
        )
    })

    test('preserves relative non-api base paths before the websocket path', () => {
        expect(buildImSocketUrl('ticket value', '/backend/api', {protocol: 'https:', host: 'example.com'})).toBe(
            'wss://example.com/backend/ws/im?ticket=ticket+value',
        )
    })
})

describe('useImSocket', () => {
    test('builds controller options without undefined keys so controller defaults survive', () => {
        const controllerOptions = buildUseImSocketControllerOptions({})
        const resolvedOptions = resolveImSocketOptions(controllerOptions)

        expect(Object.hasOwn(controllerOptions, 'enabled')).toBe(false)
        expect(Object.hasOwn(controllerOptions, 'ticketLoader')).toBe(false)
        expect(Object.hasOwn(controllerOptions, 'pingIntervalMs')).toBe(false)
        expect(resolvedOptions.enabled).toBe(true)
        expect(resolvedOptions.ticketLoader).toBe(createImWsTicket)
        expect(resolvedOptions.pingIntervalMs).toBe(30000)
        expect(resolvedOptions.apiBaseUrl).toBe(API_BASE_URL)
    })

    test('keeps the React hook render-safe when disabled', () => {
        const markup = renderToStaticMarkup(
            <HookHost
                WebSocketCtor={MockWebSocket}
                ticketLoader={vi.fn().mockResolvedValue(ticket)}
            />,
        )

        expect(markup).toBe('')
    })
})

function HookHost(props: {
    WebSocketCtor: typeof MockWebSocket
    ticketLoader: () => Promise<WsTicketView>
}) {
    useImSocket({
        enabled: false,
        activeConversationId: 2,
        WebSocketCtor: props.WebSocketCtor,
        ticketLoader: props.ticketLoader,
        location: {protocol: 'https:', host: 'example.com'},
    })
    return null
}

function parseSentFrame(frame: string): Record<string, unknown> {
    const parsed: unknown = JSON.parse(frame)
    return isRecord(parsed) ? parsed : {}
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value)
}
