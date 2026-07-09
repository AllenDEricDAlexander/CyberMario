import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {StorytellerAgentPanelContent} from './StorytellerAgentPanel'
import type {ClocktowerAgentConsoleView} from '../clocktowerTypes'

const agents: ClocktowerAgentConsoleView[] = [
    {
        agentInstanceId: 81,
        actorId: 71,
        gameSeatId: 31,
        seatNo: 2,
        displayName: 'Agent Bob',
        profileName: 'Default Agent',
        status: 'ACTIVE',
        autoMode: 'PAUSED',
        roleCode: 'IMP',
        alignment: 'EVIL',
        recentTaskStatus: 'PENDING',
        recentTaskTriggerType: 'ST_RUN_NOW',
        recentTaskResult: {},
        recentError: null,
    },
]

describe('StorytellerAgentPanel', () => {
    test('renders agent control rows', () => {
        const markup = renderToStaticMarkup(
            <StorytellerAgentPanelContent
                actionLoadingKey={null}
                agents={agents}
                loading={false}
                onOpenMemory={vi.fn()}
                onPause={vi.fn()}
                onRefresh={vi.fn()}
                onResume={vi.fn()}
                onRunNow={vi.fn()}
            />,
        )

        expect(markup).toContain('Agent Bob')
        expect(markup).toContain('PAUSED')
        expect(markup).toContain('IMP')
        expect(markup).toContain('查看记忆')
        expect(markup).toContain('立即运行')
    })
})
