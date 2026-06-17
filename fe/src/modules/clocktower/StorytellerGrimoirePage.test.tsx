import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as StorytellerGrimoirePage} from './StorytellerGrimoirePage'

vi.mock('react-router', async () => {
    const actual = await vi.importActual<typeof import('react-router')>('react-router')
    return {...actual, useParams: () => ({roomId: '7'})}
})

vi.mock('./clocktowerService', () => ({
    getClocktowerGrimoire: vi.fn().mockResolvedValue({
        roomId: 7,
        phase: {phase: 'FIRST_NIGHT', dayNo: 0, nightNo: 1},
        seats: [],
        markers: [],
        reminders: [],
        pendingTasks: [],
        ruleTraceEnabled: false,
    }),
    getClocktowerNightChecklist: vi.fn().mockResolvedValue({nightNo: 1, nightType: 'FIRST_NIGHT', steps: [], completed: false}),
    submitClocktowerStorytellerAction: vi.fn(),
}))

describe('StorytellerGrimoirePage', () => {
    test('renders grimoire controls', () => {
        const markup = renderToStaticMarkup(<StorytellerGrimoirePage/>)

        expect(markup).toContain('说书人魔典')
        expect(markup).toContain('夜晚顺序')
        expect(markup).toContain('待处理任务')
    })
})
