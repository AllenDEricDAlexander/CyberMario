import {act, render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {
    Component as ClocktowerAdminAuditPage,
    normalizeAuditCriteria,
    parseAuditIds,
} from './ClocktowerAdminAuditPage'

vi.mock('./clocktowerService', () => {
    const emptyPage = {records: [], page: 1, size: 20, total: 0, totalPages: 0}
    return {
        getClocktowerAuditSummary: vi.fn().mockResolvedValue({
            roomCount: 0,
            gameCount: 0,
            eventCount: 0,
            conversationCount: 0,
            messageCount: 0,
            memberCount: 0,
            invitationCount: 0,
            banCount: 0,
        }),
        getClocktowerGameAudit: vi.fn(),
        getClocktowerRoomAudit: vi.fn(),
        listClocktowerAuditBans: vi.fn().mockResolvedValue(emptyPage),
        listClocktowerAuditConversations: vi.fn().mockResolvedValue(emptyPage),
        listClocktowerAuditEvents: vi.fn().mockResolvedValue(emptyPage),
        listClocktowerAuditGames: vi.fn().mockResolvedValue(emptyPage),
        listClocktowerAuditInvitations: vi.fn().mockResolvedValue(emptyPage),
        listClocktowerAuditMembers: vi.fn().mockResolvedValue(emptyPage),
        listClocktowerAuditMessages: vi.fn().mockResolvedValue(emptyPage),
        listClocktowerAuditRooms: vi.fn().mockResolvedValue(emptyPage),
    }
})

describe('ClocktowerAdminAuditPage', () => {
    test('renders audit lookup form and audit sections', () => {
        const markup = renderToStaticMarkup(<ClocktowerAdminAuditPage/>)

        expect(markup).toContain('钟楼审计')
        expect(markup).toContain('房间 ID')
        expect(markup).toContain('游戏 ID')
        expect(markup).toContain('会话 ID')
        expect(markup).toContain('房间名称')
        expect(markup).toContain('不填写条件时查询全部数据')
        expect(markup).toContain('房间')
        expect(markup).toContain('游戏')
        expect(markup).toContain('事件')
        expect(markup).toContain('会话')
        expect(markup).toContain('消息')
        expect(markup).toContain('邀请')
        expect(markup).toContain('成员')
        expect(markup).toContain('封禁')
    })

    test('accepts positive IDs and removes duplicates without changing their order', () => {
        expect(parseAuditIds(['2', 4, '2', '004'])).toEqual({ids: [2, 4], invalid: []})
        expect(parseAuditIds(['0', '-1', '2.5', 'room-2'])).toEqual({
            ids: [],
            invalid: ['0', '-1', '2.5', 'room-2'],
        })
    })

    test('normalizes multi-value filters while keeping an empty query valid', () => {
        expect(normalizeAuditCriteria({})).toEqual({})
        expect(normalizeAuditCriteria({
            roomIds: ['2', '2', 4],
            gameIds: ['11'],
            conversationIds: ['21', '22'],
            roomName: '  测试房间  ',
        })).toEqual({
            roomIds: [2, 4],
            gameIds: [11],
            conversationIds: [21, 22],
            roomName: '测试房间',
        })
    })

    test('loads every report with an empty query and submits one shared multi-value filter', async () => {
        const service = await import('./clocktowerService')
        const user = userEvent.setup()
        vi.clearAllMocks()
        render(<ClocktowerAdminAuditPage/>)

        await waitFor(() => expect(service.getClocktowerAuditSummary).toHaveBeenCalledWith({}))
        for (const loader of [
            service.listClocktowerAuditRooms,
            service.listClocktowerAuditGames,
            service.listClocktowerAuditEvents,
            service.listClocktowerAuditConversations,
            service.listClocktowerAuditMessages,
            service.listClocktowerAuditMembers,
            service.listClocktowerAuditInvitations,
            service.listClocktowerAuditBans,
        ]) {
            expect(loader).toHaveBeenCalledWith({page: 1, size: 20})
        }

        await user.type(screen.getByRole('combobox', {name: '房间 ID'}), '2{enter}4{enter}2{enter}')
        await user.type(screen.getByLabelText('房间名称'), ' 测试房间 ')
        await user.click(screen.getByRole('button', {name: /查询/}))

        const criteria = {roomIds: [2, 4], roomName: '测试房间'}
        await waitFor(() => expect(service.getClocktowerAuditSummary).toHaveBeenLastCalledWith(criteria))
        for (const loader of [
            service.listClocktowerAuditRooms,
            service.listClocktowerAuditGames,
            service.listClocktowerAuditEvents,
            service.listClocktowerAuditConversations,
            service.listClocktowerAuditMessages,
            service.listClocktowerAuditMembers,
            service.listClocktowerAuditInvitations,
            service.listClocktowerAuditBans,
        ]) {
            expect(loader).toHaveBeenLastCalledWith({...criteria, page: 1, size: 20})
        }
    })

    test('does not let a stale room request overwrite a newer filtered result', async () => {
        const service = await import('./clocktowerService')
        const user = userEvent.setup()
        let resolveInitial!: (value: ReturnType<typeof roomPage>) => void
        let resolveFiltered!: (value: ReturnType<typeof roomPage>) => void
        const initialRequest = new Promise<ReturnType<typeof roomPage>>((resolve) => {
            resolveInitial = resolve
        })
        const filteredRequest = new Promise<ReturnType<typeof roomPage>>((resolve) => {
            resolveFiltered = resolve
        })
        vi.mocked(service.listClocktowerAuditRooms)
            .mockReset()
            .mockImplementationOnce(() => initialRequest)
            .mockImplementationOnce(() => filteredRequest)
        render(<ClocktowerAdminAuditPage/>)

        await user.type(screen.getByRole('combobox', {name: '房间 ID'}), '2{enter}')
        await user.click(screen.getByRole('button', {name: /查询/}))
        await waitFor(() => expect(service.listClocktowerAuditRooms).toHaveBeenCalledTimes(2))

        await act(async () => {
            resolveFiltered(roomPage(2, '房间二'))
            await filteredRequest
        })
        expect(await screen.findByText('房间二')).toBeTruthy()
        await act(async () => {
            resolveInitial(roomPage(4, '房间四'))
            await initialRequest
        })
        expect(screen.queryByText('房间四')).toBeNull()
    })
})

function roomPage(roomId: number, roomName: string) {
    return {
        records: [{
            roomId,
            roomCode: `ROOM-${roomId}`,
            roomName,
            status: 'ACTIVE',
            visibility: 'PUBLIC',
            ownerUserId: 100,
            capacity: 15,
            currentMemberCount: 5,
            lastActiveAt: null,
            createdAt: null,
        }],
        page: 1,
        size: 20,
        total: 1,
        totalPages: 1,
    }
}
