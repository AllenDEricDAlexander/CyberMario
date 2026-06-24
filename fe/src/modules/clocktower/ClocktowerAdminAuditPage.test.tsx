import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as ClocktowerAdminAuditPage} from './ClocktowerAdminAuditPage'

vi.mock('./clocktowerService', () => ({
    getClocktowerGameAudit: vi.fn(),
    getClocktowerRoomAudit: vi.fn(),
    listClocktowerAdminChatMessages: vi.fn(),
}))

describe('ClocktowerAdminAuditPage', () => {
    test('renders audit lookup form and audit sections', () => {
        const markup = renderToStaticMarkup(<ClocktowerAdminAuditPage/>)

        expect(markup).toContain('钟楼审计')
        expect(markup).toContain('房间 ID')
        expect(markup).toContain('游戏 ID')
        expect(markup).toContain('会话 ID')
        expect(markup).toContain('房间')
        expect(markup).toContain('游戏')
        expect(markup).toContain('聊天')
        expect(markup).toContain('邀请')
        expect(markup).toContain('成员')
        expect(markup).toContain('封禁')
    })
})
