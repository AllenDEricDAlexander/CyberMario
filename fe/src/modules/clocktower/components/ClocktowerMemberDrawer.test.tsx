import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {ClocktowerMemberTable} from './ClocktowerMemberDrawer'

describe('ClocktowerMemberDrawer', () => {
    test('renders member table columns status tags and action affordance', () => {
        const markup = renderToStaticMarkup(
            <ClocktowerMemberTable
                actionUserId={101}
                currentUserId={999}
                members={[
                    {
                        memberId: 21,
                        userId: 101,
                        memberType: 'PLAYER',
                        status: 'ACTIVE',
                        seatNo: 1,
                        displayName: '玩家一',
                    },
                    {
                        memberId: 22,
                        userId: 102,
                        memberType: 'SPECTATOR',
                        status: 'LEFT',
                        seatNo: null,
                        displayName: '',
                    },
                ]}
                onKickMember={vi.fn()}
            />,
        )

        expect(markup).toContain('用户 ID')
        expect(markup).toContain('成员')
        expect(markup).toContain('座位')
        expect(markup).toContain('类型')
        expect(markup).toContain('状态')
        expect(markup).toContain('操作')
        expect(markup).toContain('玩家一')
        expect(markup).toContain('用户 102')
        expect(markup).toContain('PLAYER')
        expect(markup).toContain('SPECTATOR')
        expect(markup).toContain('ACTIVE')
        expect(markup).toContain('LEFT')
        expect(markup).toContain('成员 101 操作')
    })

    test('suppresses destructive actions for the owner and current user', () => {
        const markup = renderToStaticMarkup(
            <ClocktowerMemberTable
                actionUserId={null}
                currentUserId={102}
                members={[
                    {
                        memberId: 20,
                        userId: 101,
                        memberType: 'OWNER',
                        status: 'ACTIVE',
                        seatNo: null,
                        displayName: '房主',
                    },
                    {
                        memberId: 21,
                        userId: 102,
                        memberType: 'PLAYER',
                        status: 'ACTIVE',
                        seatNo: 1,
                        displayName: '当前用户',
                    },
                    {
                        memberId: 22,
                        userId: 103,
                        memberType: 'PLAYER',
                        status: 'ACTIVE',
                        seatNo: 2,
                        displayName: '其他玩家',
                    },
                ]}
                onKickMember={vi.fn()}
            />,
        )

        expect(markup).not.toContain('成员 101 操作')
        expect(markup).not.toContain('成员 102 操作')
        expect(markup).toContain('成员 103 操作')
    })
})
