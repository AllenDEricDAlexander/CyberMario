import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {StorytellerNightTaskPanelContent} from './StorytellerNightTaskPanel'
import type {ClocktowerGameSeatResponse, ClocktowerNightTaskView} from '../clocktowerTypes'

const seats: ClocktowerGameSeatResponse[] = [
    {
        gameSeatId: 31,
        roomSeatId: 21,
        seatNo: 1,
        actorType: 'HUMAN',
        displayName: 'Alice',
        roleCode: 'POISONER',
        roleType: 'MINION',
        alignment: 'EVIL',
        lifeStatus: 'ALIVE',
        publicLifeStatus: 'ALIVE',
        hasDeadVote: true,
        traveler: false,
        status: 'ACTIVE',
    },
]

const tasks: ClocktowerNightTaskView[] = [
    {
        taskId: 91,
        gameId: 11,
        nightNo: 1,
        actorGameSeatId: 31,
        roleCode: 'POISONER',
        taskType: 'CHOOSE_TARGET',
        status: 'CHOSEN',
        mandatory: true,
        sortOrder: 10,
        choice: {targetGameSeatIds: [31]},
        result: {},
        metadata: {},
    },
]

describe('StorytellerNightTaskPanel', () => {
    test('renders night task controls', () => {
        const markup = renderToStaticMarkup(
            <StorytellerNightTaskPanelContent
                actionLoadingKey={null}
                loading={false}
                onManualTarget={vi.fn()}
                onRandom={vi.fn()}
                onRefresh={vi.fn()}
                onResolve={vi.fn()}
                onSkip={vi.fn()}
                seats={seats}
                tasks={tasks}
            />,
        )

        expect(markup).toContain('POISONER')
        expect(markup).toContain('CHOOSE_TARGET')
        expect(markup).toContain('确认')
        expect(markup).toContain('跳过')
        expect(markup).toContain('随机')
        expect(markup).toContain('手动选目标')
    })
})
