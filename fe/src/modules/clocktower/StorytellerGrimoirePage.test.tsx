import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as StorytellerGrimoirePage} from './StorytellerGrimoirePage'
import {NightChecklist} from './components/NightChecklist'

vi.mock('react-router', () => ({
    useParams: () => ({roomId: '7'}),
}))

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

    test('renders localized checklist night and step role type descriptions', () => {
        const markup = renderToStaticMarkup(
            <NightChecklist
                checklist={{
                    nightNo: 1,
                    nightType: {code: 1, desc: '首夜'},
                    steps: [
                        {
                            orderNo: 10,
                            seatId: 1,
                            roleCode: 'CHEF',
                            roleName: '厨师',
                            roleType: {code: 1, desc: '镇民'},
                            wakeRequired: true,
                            completed: false,
                        },
                    ],
                    completed: false,
                }}
            />,
        )

        expect(markup).toContain('首夜')
        expect(markup).toContain('厨师')
        expect(markup).toContain('镇民')
    })
})
