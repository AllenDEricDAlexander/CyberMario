import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {StorytellerMicControlPanelContent} from './StorytellerMicControlPanel'
import type {ClocktowerMicSessionView} from '../clocktowerTypes'

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
    test('renders storyteller mic controls', () => {
        const markup = renderToStaticMarkup(
            <StorytellerMicControlPanelContent
                actionLoading={null}
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
})
