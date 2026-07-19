import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {ImSurfaceBrowser} from './ImSurfaceBrowser'
import type {ChannelView, GroupView} from '../imTypes'

const channel: ChannelView = {
    id: 1,
    contextType: 'clocktower',
    contextId: 101,
    channelKey: 'town-square',
    joinKey: 'chn_townsquare00000000000',
    name: 'Town Square',
    ownerUserId: 10,
    visibility: 'PUBLIC',
    joinPolicy: 'OPEN',
    status: 'ACTIVE',
    announcement: 'Night phase starts at 8.',
    mainConversationId: 101,
    memberCount: 12,
    lastActiveAt: '2026-06-28T10:00:00Z',
    membershipStatus: 'ACTIVE',
    memberRole: 'MEMBER',
    canRead: true,
    canPost: true,
    unreadCount: 3,
}

const group: GroupView = {
    id: 2,
    channelId: 1,
    contextType: 'clocktower',
    contextId: 101,
    groupKey: 'storytellers',
    joinKey: 'grp_storytellers000000000',
    name: 'Storytellers',
    ownerUserId: 10,
    joinPolicy: 'APPROVAL',
    status: 'ACTIVE',
    announcement: null,
    conversationId: 102,
    memberCount: 2,
    lastActiveAt: null,
    membershipStatus: 'PENDING',
    memberRole: null,
    canRead: false,
    canPost: false,
    unreadCount: 0,
}

describe('ImSurfaceBrowser', () => {
    test('renders a scan-friendly combined channel and group list with metadata', () => {
        const markup = renderToStaticMarkup(
            <ImSurfaceBrowser
                activeSurface={{surfaceType: 'GROUP', surfaceId: 2}}
                channels={[channel]}
                groups={[group]}
                loading={false}
                onRefresh={vi.fn()}
                onSelectSurface={vi.fn()}
            />,
        )

        expect(markup).toContain('Town Square')
        expect(markup).toContain('Channel')
        expect(markup).toContain('Open')
        expect(markup).toContain('12 members')
        expect(markup).toContain('Night phase starts at 8.')
        expect(markup).toContain('Storytellers')
        expect(markup).toContain('Group')
        expect(markup).toContain('Approval')
        expect(markup).toContain('Pending')
        expect(markup).toContain('3')
        expect(markup).toContain('ant-list-item')
    })

    test('renders backend LEFT and BANNED membership statuses', () => {
        const markup = renderToStaticMarkup(
            <ImSurfaceBrowser
                channels={[{...channel, membershipStatus: 'LEFT'}]}
                groups={[{...group, membershipStatus: 'BANNED'}]}
                loading={false}
                onRefresh={vi.fn()}
                onSelectSurface={vi.fn()}
            />,
        )

        expect(markup).toContain('Left')
        expect(markup).toContain('Banned')
    })

    test('renders loading and empty states without exposing implementation details', () => {
        const loadingMarkup = renderToStaticMarkup(
            <ImSurfaceBrowser
                channels={[]}
                groups={[]}
                loading
                onRefresh={vi.fn()}
                onSelectSurface={vi.fn()}
            />,
        )
        const emptyMarkup = renderToStaticMarkup(
            <ImSurfaceBrowser
                channels={[]}
                groups={[]}
                loading={false}
                onRefresh={vi.fn()}
                onSelectSurface={vi.fn()}
            />,
        )

        expect(loadingMarkup).toContain('Loading channels and groups')
        expect(emptyMarkup).toContain('No channels or groups yet')
        expect(emptyMarkup).not.toContain('payload')
        expect(emptyMarkup).not.toContain('DTO')
    })
})
