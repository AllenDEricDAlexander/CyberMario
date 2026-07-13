import {renderToStaticMarkup} from 'react-dom/server'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {getClocktowerMicSession, startClocktowerDayMic} from '../clocktowerService'
import type {ClocktowerMicSessionView} from '../clocktowerTypes'
import {
    loadStorytellerMicSession,
    startStorytellerMicSession,
    StorytellerMicControlPanelContent,
} from './StorytellerMicControlPanel'

vi.mock('../clocktowerService', () => ({
    closeClocktowerMicSession: vi.fn(),
    extendClocktowerMicSession: vi.fn(),
    getClocktowerMicSession: vi.fn(),
    skipClocktowerMicTurn: vi.fn(),
    startClocktowerDayMic: vi.fn(),
}))

const session: ClocktowerMicSessionView = {
    sessionId: 51,
    gameId: 11,
    dayNo: 1,
    status: 'GRAB_MIC',
    currentHolderGameSeatId: 31,
    currentTurnId: 91,
    roundStartedAt: '2026-07-09T10:00:00Z',
    roundFinishedAt: '2026-07-09T10:05:00Z',
    grabStartedAt: '2026-07-09T10:05:00Z',
    grabEndsAt: '2026-07-09T10:10:00Z',
    closedAt: null,
    turns: [
        {
            turnId: 91,
            gameSeatId: 31,
            seatNo: 1,
            displayName: 'Alice',
            actorType: 'HUMAN',
            agentInstanceId: null,
            turnOrder: 1,
            stage: 'GRAB_MIC',
            acquisitionType: 'GRAB',
            status: 'ACTIVE',
            startedAt: '2026-07-09T10:06:00Z',
            endedAt: null,
            expiresAt: '2026-07-09T10:09:00Z',
        },
    ],
}

describe('StorytellerMicControlPanel', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    test('renders storyteller mic controls', () => {
        const markup = renderToStaticMarkup(
            <StorytellerMicControlPanelContent
                actionLoading={null}
                dayPhase
                loading={false}
                now={new Date('2026-07-09T10:07:00Z')}
                onClose={vi.fn()}
                onExtend={vi.fn()}
                onRefresh={vi.fn()}
                onSkip={vi.fn()}
                onStart={vi.fn()}
                session={session}
            />,
        )

        expect(markup).toContain('抢麦')
        expect(markup).toContain('Alice')
        expect(markup).toContain('跳过当前')
        expect(markup).toContain('关闭公聊')
        expect(markup).toContain('延长 2 分钟')
    })

    test('does not load a day mic during night phases', async () => {
        vi.mocked(getClocktowerMicSession).mockResolvedValue(session)

        await expect(loadStorytellerMicSession(11, 'FIRST_NIGHT')).resolves.toBeNull()
        await expect(loadStorytellerMicSession(11, 'NIGHT')).resolves.toBeNull()

        expect(getClocktowerMicSession).not.toHaveBeenCalled()
    })

    test('loads the current mic session during day', async () => {
        vi.mocked(getClocktowerMicSession).mockResolvedValue(session)

        await expect(loadStorytellerMicSession(11, 'DAY')).resolves.toBe(session)

        expect(getClocktowerMicSession).toHaveBeenCalledWith(11)
    })

    test('does not start a day mic during night phases', async () => {
        await expect(startStorytellerMicSession(11, 'NIGHT')).resolves.toBeNull()

        expect(startClocktowerDayMic).not.toHaveBeenCalled()
    })

    test('explains day availability and disables start during night', () => {
        const markup = renderToStaticMarkup(
            <StorytellerMicControlPanelContent
                actionLoading={null}
                dayPhase={false}
                loading={false}
                now={new Date('2026-07-09T10:07:00Z')}
                onClose={vi.fn()}
                onExtend={vi.fn()}
                onRefresh={vi.fn()}
                onSkip={vi.fn()}
                onStart={vi.fn()}
                session={null}
            />,
        )

        expect(markup).toContain('白天阶段可开启公聊麦序')
        expect(markup).toMatch(
            /<button[^>]*disabled=""[^>]*>[\s\S]*?<span>开启白天麦序<\/span><\/button>/,
        )
    })
})
