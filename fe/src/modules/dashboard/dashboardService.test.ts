import {beforeEach, describe, expect, test, vi} from 'vitest'
import {getModelAuditDashboard, getModelAuditUserOptions} from './dashboardService'

vi.mock('../../services/request', () => ({
    requestJson: vi.fn(),
}))

describe('dashboardService', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    test('builds encoded query strings for self and global dashboard APIs', async () => {
        const {requestJson} = await import('../../services/request')

        void getModelAuditDashboard({
            scope: 'SELF',
            startAt: '2026-06-01T00:00:00Z',
            endAt: '2026-06-14T00:00:00Z',
            provider: 'DASHSCOPE',
            model: 'qwen3.6-max-preview',
            scenario: 'AGENT_CHAT',
            status: 'SUCCESS',
        })
        void getModelAuditDashboard({
            scope: 'GLOBAL',
            userId: 7,
        })

        expect(requestJson).toHaveBeenNthCalledWith(1,
            '/api/agent/model-audit/dashboard/self?startAt=2026-06-01T00%3A00%3A00Z&endAt=2026-06-14T00%3A00%3A00Z&provider=DASHSCOPE&model=qwen3.6-max-preview&scenario=AGENT_CHAT&status=SUCCESS')
        expect(requestJson).toHaveBeenNthCalledWith(2,
            '/api/agent/model-audit/dashboard/global?userId=7')
    })

    test('builds user option selector query', async () => {
        const {requestJson} = await import('../../services/request')

        void getModelAuditUserOptions('ma rio', 30)

        expect(requestJson).toHaveBeenCalledWith('/api/agent/model-audit/dashboard/user-options?keyword=ma+rio&size=30')
    })
})
