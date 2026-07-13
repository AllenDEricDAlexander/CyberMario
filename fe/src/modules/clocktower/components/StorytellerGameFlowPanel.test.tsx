import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {advanceClocktowerGameFlow} from '../clocktowerService'
import type {ClocktowerGameAdvanceResult, ClocktowerGameFlowView} from '../clocktowerTypes'
import {
    advanceAndReloadClocktowerGameFlow,
    StorytellerGameFlowPanelContent,
} from './StorytellerGameFlowPanel'

vi.mock('../clocktowerService', () => ({
    advanceClocktowerGameFlow: vi.fn(),
    getClocktowerGameFlow: vi.fn(),
}))

const blockedFlow: ClocktowerGameFlowView = {
    gameId: 11,
    status: 'RUNNING',
    phase: 'FIRST_NIGHT',
    dayNo: 0,
    nightNo: 1,
    advanceAllowed: false,
    blockingReasons: ['PENDING_NIGHT_TASKS'],
    nextPhase: 'DAY',
    counters: {nightPendingMandatoryCount: 2},
}

describe('StorytellerGameFlowPanel', () => {
    test('renders the current flow and disables blocked advancement', () => {
        const markup = renderToStaticMarkup(
            <StorytellerGameFlowPanelContent
                actionLoading={false}
                flow={blockedFlow}
                loading={false}
                onAdvance={vi.fn()}
                onRefresh={vi.fn()}
            />,
        )

        expect(markup).toContain('FIRST_NIGHT')
        expect(markup).toContain('第 0 天')
        expect(markup).toContain('第 1 夜')
        expect(markup).toContain('DAY')
        expect(markup).toContain('PENDING_NIGHT_TASKS')
        expect(markup).toContain('nightPendingMandatoryCount')
        expect(markup).toContain('disabled=""')
    })

    test('renders an explicit loading state before flow is available', () => {
        const markup = renderToStaticMarkup(
            <StorytellerGameFlowPanelContent
                actionLoading={false}
                flow={null}
                loading
                onAdvance={vi.fn()}
                onRefresh={vi.fn()}
            />,
        )

        expect(markup).toContain('正在加载流程')
    })

    test('advances before reloading the parent game view', async () => {
        const calls: string[] = []
        const result: ClocktowerGameAdvanceResult = {
            gameId: 11,
            previousPhase: 'FIRST_NIGHT',
            phase: 'DAY',
            advanced: true,
            forced: false,
            flow: {
                ...blockedFlow,
                phase: 'DAY',
                dayNo: 1,
                advanceAllowed: false,
                blockingReasons: ['MIC_ROUND_ROBIN_NOT_FINISHED'],
                nextPhase: 'NOMINATION',
            },
        }
        vi.mocked(advanceClocktowerGameFlow).mockImplementation(async () => {
            calls.push('advance')
            return result
        })
        const onGameChanged = vi.fn(async () => {
            calls.push('reload')
        })

        const flow = await advanceAndReloadClocktowerGameFlow(11, onGameChanged)

        expect(advanceClocktowerGameFlow).toHaveBeenCalledWith(11)
        expect(onGameChanged).toHaveBeenCalledOnce()
        expect(calls).toEqual(['advance', 'reload'])
        expect(flow.phase).toBe('DAY')
    })
})
