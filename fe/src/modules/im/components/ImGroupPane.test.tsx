import {fireEvent, render, screen, waitFor} from '@testing-library/react'
import {App} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import {ImGroupPane, type ImGroupPaneProps} from './ImGroupPane'

describe('ImGroupPane', () => {
    test('loads owner management data and approves a pending join request', async () => {
        const props = createProps()
        render(<App><ImGroupPane {...props}/></App>)

        await waitFor(() => expect(props.onLoadManagement).toHaveBeenCalledWith('GROUP', 4))
        fireEvent.click(screen.getByRole('button', {name: /通.*过/}))
        await waitFor(() => expect(props.onApprove).toHaveBeenCalledWith('GROUP', 4, 9))
    })

    test('shows only caller groups and supports joining by the generated key', async () => {
        const props = createProps()
        render(<App><ImGroupPane {...props}/></App>)

        expect(screen.getByText('仅显示你已加入的独立群组')).toBeTruthy()
        fireEvent.click(screen.getByRole('button', {name: '创建独立群组'}))
        expect(screen.getByText(/可通过系统生成的唯一 Key/)).toBeTruthy()
        fireEvent.click(screen.getByRole('button', {name: 'Cancel'}))
        fireEvent.click(screen.getByRole('button', {name: '使用 Key 加入群组'}))
        fireEvent.change(screen.getByRole('textbox', {name: '群组唯一 Key'}), {
            target: {value: 'grp_0123456789abcdefghijkl'},
        })
        fireEvent.click(screen.getByRole('button', {name: /^加.*入$/}))
        await waitFor(() => expect(props.onJoin).toHaveBeenCalledWith({
            joinKey: 'grp_0123456789abcdefghijkl',
        }))
    })
})

function createProps(): ImGroupPaneProps {
    return {
        currentUser: {userId: 1, accountNo: 'mario', displayName: 'Mario'},
        groups: [{
            id: 4,
            contextType: 'PLATFORM',
            contextId: null,
            groupKey: 'team',
            joinKey: 'grp_team0000000000000000',
            name: 'Team',
            ownerUserId: 1,
            joinPolicy: 'APPROVAL',
            status: 'ACTIVE',
            conversationId: 10,
            memberCount: 1,
            membershipStatus: 'ACTIVE',
            memberRole: 'OWNER',
            canRead: true,
            canPost: true,
        }],
        members: [],
        userResults: [],
        joinRequests: [{
            joinRequestId: 9,
            userId: 22,
            accountNo: 'luigi',
            displayName: 'Luigi',
            available: true,
            status: 'PENDING',
            requestedAt: '2026-07-16T12:00:00Z',
        }],
        onRefresh: vi.fn().mockResolvedValue(undefined),
        onCreate: vi.fn().mockResolvedValue({id: 4}),
        onJoin: vi.fn().mockResolvedValue({
            status: 'ACTIVE',
            surfaceType: 'GROUP',
            surfaceId: 4,
            membershipId: 11,
        }),
        onInvite: vi.fn().mockResolvedValue(undefined),
        onLeave: vi.fn().mockResolvedValue(undefined),
        onLoadManagement: vi.fn().mockResolvedValue(undefined),
        onApprove: vi.fn().mockResolvedValue(undefined),
        onReject: vi.fn().mockResolvedValue(undefined),
        onRemoveMember: vi.fn().mockResolvedValue(undefined),
        onOpenConversation: vi.fn().mockResolvedValue(undefined),
        onSearchUsers: vi.fn().mockResolvedValue(undefined),
        onTransferOwnership: vi.fn().mockResolvedValue(undefined),
    }
}
