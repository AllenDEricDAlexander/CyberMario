import {Tabs, type TabsProps} from 'antd'
import {isValidElement, type ElementType, type ReactElement, type ReactNode} from 'react'
import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {
    Component as StorytellerGrimoirePage,
    FlowPanel,
    GrimoireSeatList,
    RulingForm,
    RulingHistory,
    StorytellerGameSurface,
    TaskList,
    rulingTypeOptions,
} from './StorytellerGrimoirePage'
import {StorytellerGameFlowPanel} from './components/StorytellerGameFlowPanel'
import {StorytellerMicControlPanel} from './components/StorytellerMicControlPanel'
import {NightChecklist} from './components/NightChecklist'
import type {ClocktowerGameViewResponse} from './clocktowerTypes'

vi.mock('react-router', () => ({
    Link: ({children, to}: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
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
    getClocktowerFlow: vi.fn().mockResolvedValue({
        roomId: 7,
        phase: {phase: 'FIRST_NIGHT', dayNo: 0, nightNo: 1},
        nextTransition: 'COMPLETE_FIRST_NIGHT',
        advanceAllowed: false,
        blockingReasons: ['CLOCKTOWER_NIGHT_TASKS_PENDING'],
        nightTaskSummary: {total: 2, pending: 2, done: 0, skipped: 0},
        openNomination: null,
        executionCandidate: {
            resolved: false,
            executable: false,
            nominationId: null,
            nomineeSeatId: null,
            voteCount: 0,
            threshold: 3,
            reason: 'NO_CLOSED_NOMINATION',
        },
        victoryCandidate: null,
    }),
    advanceClocktowerFlow: vi.fn(),
    skipClocktowerNightTask: vi.fn(),
    closeClocktowerNomination: vi.fn(),
    confirmClocktowerExecution: vi.fn(),
    submitClocktowerStorytellerAction: vi.fn(),
    createClocktowerRuling: vi.fn(),
    listClocktowerRulings: vi.fn().mockResolvedValue([]),
    undoClocktowerRuling: vi.fn(),
    getClocktowerGameAgents: vi.fn().mockResolvedValue([]),
    pauseClocktowerAgent: vi.fn(),
    resumeClocktowerAgent: vi.fn(),
    runClocktowerAgentNow: vi.fn(),
    getClocktowerAgentMemory: vi.fn().mockResolvedValue([]),
    getClocktowerAgentTasks: vi.fn().mockResolvedValue([]),
    getClocktowerMicSession: vi.fn().mockResolvedValue(null),
    startClocktowerDayMic: vi.fn(),
    skipClocktowerMicTurn: vi.fn(),
    extendClocktowerMicSession: vi.fn(),
    closeClocktowerMicSession: vi.fn(),
    getClocktowerNightTasks: vi.fn().mockResolvedValue([]),
    resolveClocktowerNightTask: vi.fn(),
    skipClocktowerGameNightTask: vi.fn(),
    randomChoiceClocktowerNightTask: vi.fn(),
    getClocktowerGameFlow: vi.fn().mockResolvedValue({
        gameId: 11,
        status: 'RUNNING',
        phase: 'NIGHT',
        dayNo: 0,
        nightNo: 1,
        advanceAllowed: true,
        blockingReasons: [],
        nextPhase: 'DAY',
        counters: {},
    }),
    advanceClocktowerGameFlow: vi.fn(),
}))

function findElementByType<Props>(node: ReactNode, type: ElementType): ReactElement<Props> | null {
    if (Array.isArray(node)) {
        for (const child of node as ReactNode[]) {
            const match = findElementByType<Props>(child, type)
            if (match) {
                return match
            }
        }
        return null
    }
    if (!isValidElement(node)) {
        return null
    }
    if (node.type === type) {
        return node as ReactElement<Props>
    }
    const element = node as ReactElement<{children?: ReactNode}>
    return findElementByType<Props>(element.props.children, type)
}

function storytellerGameView(): ClocktowerGameViewResponse {
    return {
        gameId: 11,
        roomId: 7,
        gameNo: 1,
        status: 'RUNNING',
        phase: 'NIGHT',
        viewerMode: 'STORYTELLER',
        mySeat: null,
        publicSeats: [],
        grimoire: [
            {
                gameSeatId: 31,
                roomSeatId: 3,
                seatNo: 1,
                userId: 101,
                displayName: 'Alice',
                roleCode: 'EMPATH',
                roleType: 'TOWNSFOLK',
                alignment: 'GOOD',
                lifeStatus: 'ALIVE',
                publicLifeStatus: 'ALIVE',
                hasDeadVote: true,
                traveler: false,
                status: 'ACTIVE',
            },
        ],
        availableActions: [],
        events: [],
        conversations: [
            {
                conversationId: 201,
                roomId: 7,
                gameId: 11,
                channelKey: 'PUBLIC',
                groupKey: 'PUBLIC',
                conversationType: 'GROUP',
                messageSeq: 3,
            },
            {
                conversationId: 202,
                roomId: 7,
                gameId: 11,
                channelKey: 'SPECTATOR',
                groupKey: 'SPECTATOR',
                conversationType: 'GROUP',
                messageSeq: 2,
            },
        ],
    }
}

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

    test('renders skipped night step reason', () => {
        const markup = renderToStaticMarkup(
            <NightChecklist
                checklist={{
                    nightNo: 1,
                    nightType: 'FIRST_NIGHT',
                    steps: [
                        {
                            orderNo: 20,
                            seatId: 2,
                            roleCode: 'EMPATH',
                            roleName: '共情者',
                            roleType: 'TOWNSFOLK',
                            wakeRequired: true,
                            skipReason: '已由说书人跳过',
                            completed: false,
                        },
                    ],
                    completed: false,
                }}
            />,
        )

        expect(markup).toContain('已跳过')
        expect(markup).toContain('已由说书人跳过')
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

    test('renders flow panel with blocking reason', () => {
        const markup = renderToStaticMarkup(
            <FlowPanel
                flow={{
                    roomId: 7,
                    phase: {phase: 'FIRST_NIGHT', dayNo: 0, nightNo: 1},
                    nextTransition: 'COMPLETE_FIRST_NIGHT',
                    advanceAllowed: false,
                    blockingReasons: ['CLOCKTOWER_NIGHT_TASKS_PENDING'],
                    nightTaskSummary: {total: 2, pending: 2, done: 0, skipped: 0},
                    openNomination: null,
                    executionCandidate: {
                        resolved: false,
                        executable: false,
                        nominationId: null,
                        nomineeSeatId: null,
                        voteCount: 0,
                        threshold: 3,
                        reason: 'NO_CLOSED_NOMINATION',
                    },
                    victoryCandidate: null,
                }}
                loading={false}
                onAdvance={() => Promise.resolve()}
                onConfirmExecution={() => Promise.resolve()}
                onConfirmNoExecution={() => Promise.resolve()}
            />,
        )

        expect(markup).toContain('流程')
        expect(markup).toContain('首夜')
        expect(markup).toContain('待处理 2')
        expect(markup).toContain('夜晚任务未完成')
    })

    test('renders execution resolution controls with required note', () => {
        const markup = renderToStaticMarkup(
            <FlowPanel
                flow={{
                    roomId: 7,
                    phase: {phase: 'EXECUTION', dayNo: 1, nightNo: 1},
                    nextTransition: 'START_NIGHT',
                    advanceAllowed: false,
                    blockingReasons: ['CLOCKTOWER_EXECUTION_NOT_RESOLVED'],
                    nightTaskSummary: {total: 0, pending: 0, done: 0, skipped: 0},
                    openNomination: null,
                    executionCandidate: {
                        resolved: false,
                        executable: true,
                        nominationId: 12,
                        nomineeSeatId: 3,
                        voteCount: 4,
                        threshold: 3,
                        reason: 'EXECUTION_CANDIDATE',
                    },
                    victoryCandidate: null,
                }}
                loading={false}
                onAdvance={() => Promise.resolve()}
                onConfirmExecution={() => Promise.resolve()}
                onConfirmNoExecution={() => Promise.resolve()}
            />,
        )

        expect(markup).toContain('处决结算')
        expect(markup).toContain('结算原因')
        expect(markup).toContain('确认处决但不死亡')
        expect(markup).toContain('确认处决并标记死亡')
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

    test('renders full ruling form controls', () => {
        const markup = renderToStaticMarkup(
            <RulingForm
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
                            publicAlive: true,
                            lifeStatus: 'ALIVE',
                            publicLifeStatus: 'ALIVE',
                            hasDeadVote: true,
                            connected: true,
                        },
                    ],
                    markers: [],
                    reminders: [],
                    pendingTasks: [],
                    ruleTraceEnabled: false,
                }}
                loading={false}
                onSubmit={() => Promise.resolve(true)}
            />,
        )

        expect(markup).toContain('裁定类型')
        expect(markup).toContain('设置公开生死')
        expect(markup).toContain('提名 ID')
        expect(markup).toContain('裁定原因')
        expect(markup).toContain('提交裁定')
        expect(rulingTypeOptions.map((option) => option.label)).toEqual([
            '判死亡',
            '复活',
            '设置公开生死',
            '处决玩家',
            '跳过处决',
            '结束游戏',
            '关闭提名',
            '重开提名',
            '作废提名',
        ])
    })

    test('renders storyteller game surface console tabs', () => {
        const markup = renderToStaticMarkup(
            <StorytellerGameSurface
                roomName="Friday"
                view={storytellerGameView()}
            />,
        )

        expect(markup).toContain('Agent')
        expect(markup).toContain('麦序')
        expect(markup).toContain('夜晚任务')
        expect(markup).toContain('聊天监控')
    })

    test('wires GAME_V2 flow and phase-aware mic tabs', () => {
        const onGameChanged = vi.fn().mockResolvedValue(undefined)
        const surface = StorytellerGameSurface({
            roomName: 'Friday',
            view: storytellerGameView(),
            onGameChanged,
        })
        const tabs = findElementByType<TabsProps>(surface, Tabs)
        const flowItem = tabs?.props.items?.find((item) => item.key === 'flow')
        const micItem = tabs?.props.items?.find((item) => item.key === 'mic')

        expect(isValidElement(flowItem?.children)).toBe(true)
        expect(isValidElement(micItem?.children)).toBe(true)
        if (!isValidElement(flowItem?.children) || !isValidElement(micItem?.children)) {
            throw new Error('storyteller console tab content missing')
        }

        const flowProps = flowItem.children.props as {
            gameId: number
            onGameChanged?: () => Promise<void>
        }
        const micProps = micItem.children.props as {gameId: number; phase: string}
        expect(flowItem.children.type).toBe(StorytellerGameFlowPanel)
        expect(flowProps).toMatchObject({gameId: 11, onGameChanged})
        expect(micItem.children.type).toBe(StorytellerMicControlPanel)
        expect(micProps).toMatchObject({gameId: 11, phase: 'NIGHT'})
    })

    test('renders storyteller game surface with grimoire and player chat monitor excluding spectator channel', () => {
        const markup = renderToStaticMarkup(
            <StorytellerGameSurface
                roomName="测试房间"
                view={storytellerGameView()}
            />,
        )

        expect(markup).toContain('说书人魔典')
        expect(markup).toContain('聊天监控')
        expect(markup).toContain('EMPATH')
        expect(markup).toContain('玩家公聊')
        expect(markup).not.toContain('旁观席')
        expect(markup).not.toContain('/clocktower/rooms/7/grimoire')
        expect(markup).not.toContain('完整魔典')
        expect(markup).not.toContain('进入裁定')
    })
})
