import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {
    ClocktowerInvitationDrawerContent,
    ClocktowerInvitationDrawerFooter,
} from './ClocktowerInvitationDrawer'

describe('ClocktowerInvitationDrawer', () => {
    test('renders invitation form actions and reservation table content', () => {
        const markup = renderToStaticMarkup(
            <>
                <ClocktowerInvitationDrawerContent
                    maxSeatNo={7}
                    reservations={[
                        {
                            invitationId: 44,
                            inviteeUserId: 206,
                            targetSeatNo: 3,
                            expiresAt: '2026-06-25T00:00:00Z',
                        },
                    ]}
                />
                <ClocktowerInvitationDrawerFooter onClose={vi.fn()} onSubmit={vi.fn()}/>
            </>,
        )

        expect(markup).toContain('受邀用户 ID')
        expect(markup).toContain('邀请类型')
        expect(markup).toContain('目标座位')
        expect(markup).toContain('创建邀请')
        expect(markup).toContain('邀请 ID')
        expect(markup).toContain('受邀用户')
        expect(markup).toContain('预留座位')
        expect(markup).toContain('44')
        expect(markup).toContain('206')
        expect(markup).toContain('#3')
    })
})
