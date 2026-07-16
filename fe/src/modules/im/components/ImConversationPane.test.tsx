import {fireEvent, render, screen} from '@testing-library/react'
import {describe, expect, test, vi} from 'vitest'
import type {PlatformConversationView} from '../platformImTypes'
import {ImConversationPane, sortPlatformConversations} from './ImConversationPane'

describe('ImConversationPane', () => {
    test('pins the public channel and orders the remaining conversations by latest activity', () => {
        expect(sortPlatformConversations([
            conversation({conversationId: 2, title: 'Older', lastActiveAt: '2026-07-15T12:00:00Z'}),
            conversation({conversationId: 1, title: '公共频道', displayType: 'PUBLIC_CHANNEL'}),
            conversation({conversationId: 3, title: 'Newer', lastActiveAt: '2026-07-16T12:00:00Z'}),
        ]).map((item) => item.title)).toEqual(['公共频道', 'Newer', 'Older'])
    })

    test('renders exact unread badges and selects a conversation with a native button', () => {
        const onSelect = vi.fn()
        render(
            <ImConversationPane
                conversations={[conversation({conversationId: 3, title: 'Team', unreadCount: 12})]}
                selectedConversationId={3}
                onRefresh={vi.fn()}
                onSelect={onSelect}
            />,
        )

        expect(document.querySelector('[title="12"]')).toBeTruthy()
        const row = screen.getByRole('button', {name: /Team/})
        expect(row.getAttribute('aria-current')).toBe('true')
        fireEvent.click(row)
        expect(onSelect).toHaveBeenCalledWith(3)
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
