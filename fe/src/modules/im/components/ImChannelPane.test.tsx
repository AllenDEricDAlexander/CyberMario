import {fireEvent, render, screen, waitFor} from '@testing-library/react'
import {App} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import type {PlatformChannelView, PlatformGroupView} from '../platformImTypes'
import {ImChannelPane, type ImChannelPaneProps} from './ImChannelPane'

describe('ImChannelPane', () => {
    test('starts with no default channel and never exposes a surface search entry', () => {
        const props = createProps({channels: []})
        render(<App><ImChannelPane {...props}/></App>)

        expect(screen.getByText('暂无频道，可自行创建')).toBeTruthy()
        expect(screen.queryByPlaceholderText(/搜索频道/)).toBeNull()
        expect(props.onListGroups).not.toHaveBeenCalled()
    })

    test('lists child groups only after loading a caller channel', async () => {
        const props = createProps()
        render(<App><ImChannelPane {...props}/></App>)

        await waitFor(() => expect(props.onListGroups).toHaveBeenCalledWith(7))
        const child = await screen.findByRole('button', {name: /Delivery/})
        fireEvent.click(child)
        expect(screen.getByText('仅频道成员可加入此群组')).toBeTruthy()
        expect(screen.getByRole('button', {name: /创建子群组/})).toBeTruthy()
    })

    test('joins a channel by its generated key', async () => {
        const props = createProps()
        render(<App><ImChannelPane {...props}/></App>)

        fireEvent.click(screen.getByRole('button', {name: '使用 Key 加入频道'}))
        fireEvent.change(screen.getByRole('textbox', {name: '频道唯一 Key'}), {
            target: {value: 'chn_0123456789abcdefghijkl'},
        })
        fireEvent.click(screen.getByRole('button', {name: /^加.*入$/}))

        await waitFor(() => expect(props.onApply).toHaveBeenCalledWith({
            joinKey: 'chn_0123456789abcdefghijkl',
        }))
    })
})

function createProps(overrides: Partial<ImChannelPaneProps> = {}): ImChannelPaneProps {
    const childGroup: PlatformGroupView = {
        id: 9,
        channelId: 7,
        contextType: 'PLATFORM',
        contextId: null,
        groupKey: 'delivery',
        joinKey: 'grp_delivery0000000000000',
        name: 'Delivery',
        ownerUserId: 1,
        joinPolicy: 'OPEN',
        status: 'ACTIVE',
        conversationId: 19,
        memberCount: 1,
        membershipStatus: 'ACTIVE',
        memberRole: 'OWNER',
    }
    return {
        currentUser: {userId: 1, accountNo: 'mario', displayName: 'Mario'},
        channels: [channel()],
        members: [],
        joinRequests: [],
        userResults: [],
        onRefresh: vi.fn().mockResolvedValue([channel()]),
        onCreate: vi.fn(),
        onListGroups: vi.fn().mockResolvedValue([childGroup]),
        onCreateGroup: vi.fn(),
        onOpenConversation: vi.fn().mockResolvedValue(undefined),
        onSearchUsers: vi.fn().mockResolvedValue(undefined),
        onInvite: vi.fn().mockResolvedValue(undefined),
        onApply: vi.fn().mockResolvedValue({
            status: 'ACTIVE',
            surfaceType: 'CHANNEL',
            surfaceId: 7,
            membershipId: 17,
        }),
        onCancel: vi.fn(),
        onLeave: vi.fn().mockResolvedValue(undefined),
        onLoadManagement: vi.fn().mockResolvedValue(undefined),
        onApprove: vi.fn().mockResolvedValue(undefined),
        onReject: vi.fn().mockResolvedValue(undefined),
        onRemoveMember: vi.fn().mockResolvedValue(undefined),
        onTransferOwnership: vi.fn().mockResolvedValue(undefined),
        ...overrides,
    }
}

function channel(): PlatformChannelView {
    return {
        id: 7,
        contextType: 'PLATFORM',
        contextId: null,
        channelKey: 'product',
        joinKey: 'chn_product00000000000000',
        name: 'Product',
        ownerUserId: 1,
        joinPolicy: 'APPROVAL',
        status: 'ACTIVE',
        mainConversationId: 17,
        memberCount: 1,
        lastActiveAt: null,
        membershipStatus: 'ACTIVE',
        memberRole: 'OWNER',
    }
}
