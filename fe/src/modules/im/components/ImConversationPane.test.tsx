import {fireEvent, render, screen} from '@testing-library/react'
import {describe, expect, test, vi} from 'vitest'
import type {PlatformConversationView} from '../platformImTypes'
import {buildPlatformConversationTree, ImConversationPane, sortPlatformConversations} from './ImConversationPane'

describe('ImConversationPane', () => {
    test('orders all conversations by latest activity without pinning a default channel', () => {
        expect(sortPlatformConversations([
            conversation({conversationId: 2, title: 'Older', lastActiveAt: '2026-07-15T12:00:00Z'}),
            conversation({conversationId: 1, title: 'Channel', displayType: 'CHANNEL'}),
            conversation({conversationId: 3, title: 'Newer', lastActiveAt: '2026-07-16T12:00:00Z'}),
        ]).map((item) => item.title)).toEqual(['Newer', 'Older', 'Channel'])
    })

    test('renders exact unread badges and selects a tree conversation', () => {
        const onSelect = vi.fn()
        render(
            <ImConversationPane
                conversations={[
                    conversation({conversationId: 3, title: 'Team', unreadCount: 12}),
                    conversation({conversationId: 2, title: 'Selected'}),
                ]}
                selectedConversationId={2}
                onRefresh={vi.fn()}
                onSelect={onSelect}
            />,
        )

        expect(document.querySelector('[title="12"]')).toBeTruthy()
        expect(screen.getByText('Selected').closest('.platform-im-conversation-row')
            ?.getAttribute('aria-current')).toBe('true')
        const row = screen.getByText('Team')
        fireEvent.click(row)
        expect(onSelect).toHaveBeenCalledWith(3)
    })

    test('nests channel groups under their parent channel and keeps standalone conversations at root', () => {
        const channel = conversation({
            conversationId: 1,
            displayType: 'CHANNEL',
            ownerSurfaceType: 'CHANNEL',
            surfaceId: 7,
            title: 'Product',
        })
        const childGroup = conversation({
            conversationId: 2,
            channelId: 7,
            surfaceId: 8,
            title: 'Delivery',
        })
        const standaloneGroup = conversation({
            conversationId: 3,
            surfaceId: 9,
            title: 'Book Club',
        })

        const tree = buildPlatformConversationTree([childGroup, standaloneGroup, channel])

        expect(tree.map((node) => node.conversation.title)).toEqual(['Book Club', 'Product'])
        expect(tree[1]?.children?.map((node) => node.conversation.title)).toEqual(['Delivery'])
    })
})

function conversation(overrides: Partial<PlatformConversationView> = {}): PlatformConversationView {
    return {
        conversationId: 10,
        conversationType: 'GROUP',
        displayType: 'GROUP',
        title: 'Group',
        ownerSurfaceType: 'GROUP',
        surfaceId: 20,
        surfaceKey: 'group',
        membershipStatus: 'ACTIVE',
        memberRole: 'MEMBER',
        canRead: true,
        canPost: true,
        messageSeq: 0,
        lastActiveAt: '2026-07-14T12:00:00Z',
        status: 'ACTIVE',
        unreadCount: 0,
        ...overrides,
    }
}
