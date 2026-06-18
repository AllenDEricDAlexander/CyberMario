import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as StorytellerGrimoirePage, GrimoireSeatList, RulingHistory, TaskList} from './StorytellerGrimoirePage'
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
    getClocktowerNightChecklist: vi.fn().mockResolvedValue({
        nightNo: 1,
        nightType: 'FIRST_NIGHT',
        steps: [],
        completed: false
    }),
    submitClocktowerStorytellerAction: vi.fn(),
    createClocktowerRuling: vi.fn(),
    listClocktowerRulings: vi.fn().mockResolvedValue([]),
    undoClocktowerRuling: vi.fn(),
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

    test('renders pending wake task with resolve action', () => {
        const markup = renderToStaticMarkup(
            <TaskList
                grimoire={{
                    roomId: 7,
                    phase: {phase: 'FIRST_NIGHT', dayNo: 0, nightNo: 1},
                    seats: [],
                    markers: [],
                    reminders: [],
                    pendingTasks: [
                        {
                            taskId: 9,
                            taskType: 'WAKE_ROLE',
                            phase: 'FIRST_NIGHT',
                            roleCode: 'POISONER',
                            seatId: 4,
                            status: 'PENDING',
                            note: '让投毒者选择一名玩家',
                        },
                    ],
                    ruleTraceEnabled: false,
                }}
                onResolve={async () => {
                }}
                resolvingTaskId={null}
            />,
        )

        expect(markup).toContain('唤醒角色')
        expect(markup).toContain('待处理')
        expect(markup).toContain('POISONER')
        expect(markup).toContain('完成')
    })

    test('renders grimoire seats with real and public life status plus ruling actions', () => {
        const markup = renderToStaticMarkup(
            <GrimoireSeatList
                grimoire={{
                    roomId: 7,
                    phase: {phase: 'DAY', dayNo: 1, nightNo: 1},
                    seats: [
                        {
                            seatId: 3,
                            seatNo: 1,
                            displayName: 'Alice',
                            roleCode: 'EMPATH',
                            roleType: 'TOWNSFOLK',
                            alignment: 'GOOD',
                            alive: true,
                            publicAlive: false,
                            lifeStatus: 'ALIVE',
                            publicLifeStatus: 'DEAD',
                            hasDeadVote: true,
                            connected: true,
                        },
                    ],
                    markers: [],
                    reminders: [],
                    pendingTasks: [],
                    ruleTraceEnabled: false,
                }}
                onQuickRuling={async () => {
                }}
                rulingBusy={false}
                rulingLoadingKey={null}
            />,
        )

        expect(markup).toContain('真实存活')
        expect(markup).toContain('公开死亡')
        expect(markup).toContain('判死亡')
        expect(markup).toContain('复活')
    })

    test('renders ruling history with undo state', () => {
        const markup = renderToStaticMarkup(
            <RulingHistory
                onUndo={async () => {
                }}
                rulingBusy={false}
                rulings={[
                    {
                        rulingId: 5,
                        roomId: 7,
                        rulingType: 'MARK_DEAD',
                        status: 'APPLIED',
                        targetSeatId: 3,
                        reason: 'NIGHT_DEATH',
                        note: '夜晚死亡',
                        publicNote: '一名玩家死亡',
                        visibility: 'PUBLIC',
                    },
                ]}
                undoingRulingId={null}
            />,
        )

        expect(markup).toContain('MARK_DEAD')
        expect(markup).toContain('NIGHT_DEATH')
        expect(markup).toContain('撤销')
    })
})
