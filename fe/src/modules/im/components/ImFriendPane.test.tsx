import {fireEvent, render, screen, waitFor} from '@testing-library/react'
import {App} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import {ImFriendPane, type ImFriendPaneProps} from './ImFriendPane'

describe('ImFriendPane', () => {
    test('accepts an incoming friend request from the bounded request list', async () => {
        const props = createProps()
        render(<App><ImFriendPane {...props}/></App>)

        fireEvent.click(screen.getByText('收到 1'))
        fireEvent.click(await screen.findByRole('button', {name: /接\s*受/}))

        await waitFor(() => expect(props.onAcceptRequest).toHaveBeenCalledWith(7))
    })
})

function createProps(): ImFriendPaneProps {
    return {
        friends: [],
        incomingRequests: [{
            id: 7,
            requesterUserId: 22,
            recipientUserId: 1,
            peerUserId: 22,
            peerAccountNo: 'luigi',
            peerDisplayName: 'Luigi',
            peerAvailable: true,
            status: 'PENDING',
            requestMessage: '一起聊天吧',
            requestedAt: '2026-07-16T12:00:00Z',
        }],
        outgoingRequests: [],
        userResults: [],
        onRefresh: vi.fn().mockResolvedValue(undefined),
        onSearchUsers: vi.fn().mockResolvedValue(undefined),
        onRequestFriend: vi.fn().mockResolvedValue(undefined),
        onAcceptRequest: vi.fn().mockResolvedValue(undefined),
        onRejectRequest: vi.fn().mockResolvedValue(undefined),
        onCancelRequest: vi.fn().mockResolvedValue(undefined),
        onUpdateRemark: vi.fn().mockResolvedValue(undefined),
        onRemoveFriend: vi.fn().mockResolvedValue(undefined),
        onOpenDm: vi.fn().mockResolvedValue(undefined),
    }
}
