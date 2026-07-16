import {fireEvent, render, screen, waitFor} from '@testing-library/react'
import {App} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import {createGroupKey, ImGroupPane, type ImGroupPaneProps} from './ImGroupPane'

describe('ImGroupPane', () => {
    test('loads owner management data and approves a pending join request', async () => {
        const props = createProps()
        render(<App><ImGroupPane {...props}/></App>)

        await waitFor(() => expect(props.onLoadManagement).toHaveBeenCalledWith('GROUP', 4))
        fireEvent.click(screen.getByRole('button', {name: 'Approve'}))
        await waitFor(() => expect(props.onApprove).toHaveBeenCalledWith('GROUP', 4, 9))
    })

    test('creates bounded display-derived keys without exposing an invite-only policy', () => {
        expect(createGroupKey('Mario Team')).toMatch(/^mario-team-[a-z0-9-]{8}$/)
        expect(JSON.stringify(createProps().groups)).not.toContain('INVITE_ONLY')
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
        onCreate: vi.fn().mockResolvedValue(undefined),
        onApply: vi.fn(),
        onCancel: vi.fn(),
        onLeave: vi.fn().mockResolvedValue(undefined),
        onLoadManagement: vi.fn().mockResolvedValue(undefined),
        onApprove: vi.fn().mockResolvedValue(undefined),
        onReject: vi.fn().mockResolvedValue(undefined),
        onRemoveMember: vi.fn().mockResolvedValue(undefined),
        onOpenConversation: vi.fn().mockResolvedValue(undefined),
    }
}
