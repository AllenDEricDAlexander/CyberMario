import {fireEvent, render, screen, waitFor} from '@testing-library/react'
import {App} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import type {PlatformInvitationView} from '../platformImTypes'
import {ImInvitationPane} from './ImInvitationPane'

describe('ImInvitationPane', () => {
    test('accepts a direct channel invitation without a search-join flow', async () => {
        const onAccept = vi.fn().mockResolvedValue(undefined)
        render(
            <App>
                <ImInvitationPane
                    invitations={[invitation()]}
                    onAccept={onAccept}
                    onRefresh={vi.fn().mockResolvedValue([invitation()])}
                    onReject={vi.fn().mockResolvedValue(undefined)}
                />
            </App>,
        )

        expect(screen.getByText('来自 Luigi')).toBeTruthy()
        fireEvent.click(screen.getByRole('button', {name: /接受邀请/}))
        await waitFor(() => expect(onAccept).toHaveBeenCalledWith(12))
    })
})

function invitation(): PlatformInvitationView {
    return {
        invitationId: 12,
        surfaceType: 'CHANNEL',
        surfaceId: 7,
        surfaceName: 'Product',
        inviterUserId: 2,
        inviterDisplayName: 'Luigi',
        status: 'PENDING',
        message: 'Join us',
        createdAt: '2026-07-17T02:00:00Z',
    }
}
