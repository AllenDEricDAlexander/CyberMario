import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {
    PublicMicPanelContent,
    canGrabClocktowerMic,
    currentMicTurn,
    formatMicRemaining,
    isMicHolder,
} from './PublicMicPanel'
import type {ClocktowerMicSessionView} from '../clocktowerTypes'

const session: ClocktowerMicSessionView = {
    sessionId: 51,
    gameId: 11,
    dayNo: 1,
    status: 'GRAB_MIC',
    currentHolderGameSeatId: null,
    currentTurnId: null,
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
            stage: 'ROUND_ROBIN',
            acquisitionType: 'ROUND_ROBIN',
            status: 'DONE',
            startedAt: '2026-07-09T10:00:00Z',
            endedAt: '2026-07-09T10:01:00Z',
            expiresAt: '2026-07-09T10:01:00Z',
        },
        {
            turnId: 92,
            gameSeatId: 32,
            seatNo: 2,
            displayName: 'Agent Bob',
            actorType: 'AGENT',
            agentInstanceId: 802,
            turnOrder: 2,
            stage: 'ROUND_ROBIN',
            acquisitionType: 'ROUND_ROBIN',
            status: 'DONE',
            startedAt: '2026-07-09T10:01:00Z',
            endedAt: '2026-07-09T10:02:00Z',
            expiresAt: '2026-07-09T10:02:00Z',
        },
    ],
}

describe('PublicMicPanel', () => {
    test('formats remaining seconds as mm:ss', () => {
        expect(formatMicRemaining(0)).toBe('00:00')
        expect(formatMicRemaining(31)).toBe('00:31')
        expect(formatMicRemaining(252)).toBe('04:12')
    })

    test('derives current holder and grab availability', () => {
        expect(currentMicTurn(session)).toBeUndefined()
        expect(isMicHolder(session, 31)).toBe(false)
        expect(canGrabClocktowerMic(session, 'PLAYER', 31, new Date('2026-07-09T10:06:00Z'))).toBe(true)
        expect(canGrabClocktowerMic(session, 'PLAYER', 31, new Date('2026-07-09T10:11:00Z'))).toBe(false)
    })

    test('renders grab stage with agent badge and queue', () => {
        const markup = renderToStaticMarkup(
            <PublicMicPanelContent
                actionLoading={null}
                gameId={11}
                myGameSeatId={31}
                now={new Date('2026-07-09T10:06:00Z')}
                onExtend={vi.fn()}
                onFinish={vi.fn()}
                onGrab={vi.fn()}
                onRelease={vi.fn()}
                onSkip={vi.fn()}
                onSubmitSpeech={vi.fn()}
                session={session}
                submittingSpeech={false}
                viewerMode="PLAYER"
            />,
        )

        expect(markup).toContain('抢麦')
        expect(markup).toContain('04:00')
        expect(markup).toContain('Agent Bob')
        expect(markup).toContain('Agent')
    })
})
