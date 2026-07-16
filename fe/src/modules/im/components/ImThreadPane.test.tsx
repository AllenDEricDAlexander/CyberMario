import {fireEvent, render, screen} from '@testing-library/react'
import {App} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import type {PlatformConversationView} from '../platformImTypes'
import {ImThreadPane} from './ImThreadPane'

vi.mock('../../../components/chat-workspace', () => ({
    ChatMessageList: (props: {
        messages: Array<{clientMsgId?: string; content: string; status: string}>
        onReload?: (message: {clientMsgId?: string; content: string; status: string}) => void
        canReloadMessage?: (message: {clientMsgId?: string; content: string; status: string}) => boolean
    }) => (
        <div>
            {props.messages.map((message) => (
                <div key={message.clientMsgId ?? message.content}>
                    <span>{message.content}</span>
                    {props.canReloadMessage?.(message) && (
                        <button onClick={() => props.onReload?.(message)} type="button">重试消息</button>
                    )}
                </div>
            ))}
        </div>
    ),
    ChatSender: (props: {disabled?: boolean}) => (
        <button disabled={props.disabled} type="button">发送消息</button>
    ),
}))

describe('ImThreadPane', () => {
    test('keeps historical removed-friend DM readable and offers a friend request instead of sending', () => {
        const onAddFriend = vi.fn().mockResolvedValue(undefined)
        renderPane({
            conversation: conversation({canPost: false, displayType: 'DM', peerUserId: 22}),
            onAddFriend,
        })

        expect(screen.getByText('历史消息')).toBeTruthy()
        expect(screen.getByRole('button', {name: '发送消息'}).hasAttribute('disabled')).toBe(true)
        fireEvent.click(screen.getByRole('button', {name: '重新添加好友'}))
        expect(onAddFriend).toHaveBeenCalledWith(22)
    })

    test('exposes retry only for a failed optimistic row with its original client message id', () => {
        const onRetry = vi.fn().mockResolvedValue(true)
        renderPane({
            conversation: conversation(),
            messages: [{
                id: -1,
                conversationId: 10,
                senderUserId: 1,
                messageSeq: 2,
                clientMsgId: 'client-2',
                messageType: 'TEXT',
                content: '失败消息',
                status: 'FAILED',
                optimistic: true,
                error: 'offline',
            }],
            onRetry,
        })

        fireEvent.click(screen.getByRole('button', {name: '重试消息'}))
        expect(onRetry).toHaveBeenCalledWith(10, 'client-2')
    })
})

function renderPane(overrides: Partial<React.ComponentProps<typeof ImThreadPane>> = {}) {
    const props: React.ComponentProps<typeof ImThreadPane> = {
        conversation: conversation(),
        currentUser: {userId: 1, accountNo: 'mario', displayName: 'Mario'},
        messages: [{
            id: 1,
            conversationId: 10,
            senderUserId: 22,
            messageSeq: 1,
            clientMsgId: 'server-1',
            messageType: 'TEXT',
            content: '历史消息',
            status: 'VISIBLE',
        }],
        onSend: vi.fn().mockResolvedValue(true),
        onRetry: vi.fn().mockResolvedValue(true),
        onAddFriend: vi.fn().mockResolvedValue(undefined),
        onApplyJoin: vi.fn(),
        onCancelJoin: vi.fn(),
        onLeave: vi.fn(),
        ...overrides,
    }
    return render(<App><ImThreadPane {...props}/></App>)
}

function conversation(overrides: Partial<PlatformConversationView> = {}): PlatformConversationView {
    return {
        conversationId: 10,
        conversationType: 'DM',
        displayType: 'DM',
        title: 'Luigi',
        peerUserId: 22,
        canRead: true,
        canPost: true,
        messageSeq: 1,
        status: 'ACTIVE',
        unreadCount: 0,
        ...overrides,
    }
}
