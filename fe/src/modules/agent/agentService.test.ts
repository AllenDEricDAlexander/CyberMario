import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    createAgentPreset,
    deleteAgentPreset,
    getAgentConversationAuditMessages,
    getAgentConversationAudits,
    getAgentRunAuditEvents,
    getAgentRunAuditDetail,
    getAgentRunAudits,
    getAgentPresets,
    streamAgentDebugChat,
    updateAgentPreset,
    updateAgentPresetStatus,
} from './agentService'

vi.mock('../../services/request', () => ({
    requestJson: vi.fn(),
    streamJsonLines: vi.fn(),
}))

describe('agentService', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    test('builds preset management requests', async () => {
        const {requestJson} = await import('../../services/request')

        void getAgentPresets({page: 2, size: 30})
        void createAgentPreset({name: 'Research', enabled: true})
        void updateAgentPreset(9, {name: 'Research v2', enabled: false})
        void updateAgentPresetStatus(9, false)
        void deleteAgentPreset(9)

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/agent/presets?page=2&size=30')
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/agent/presets', {
            method: 'POST',
            body: {name: 'Research', enabled: true},
        })
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/agent/presets/9', {
            method: 'PUT',
            body: {name: 'Research v2', enabled: false},
        })
        expect(requestJson).toHaveBeenNthCalledWith(4, '/api/agent/presets/9/status', {
            method: 'PATCH',
            body: {enabled: false},
        })
        expect(requestJson).toHaveBeenNthCalledWith(5, '/api/agent/presets/9', {
            method: 'DELETE',
        })
    })

    test('streams debug chat through ndjson helper', async () => {
        const {streamJsonLines} = await import('../../services/request')
        const signal = new AbortController().signal
        const onChunk = vi.fn()

        void streamAgentDebugChat({
            message: 'hello',
            threadId: 'thread-1',
            presetId: 9,
            overrides: {systemPrompt: 'prompt'},
        }, signal, onChunk)

        expect(streamJsonLines).toHaveBeenCalledWith('/api/agent/debug/chat/stream', {
            body: {
                message: 'hello',
                threadId: 'thread-1',
                presetId: 9,
                overrides: {systemPrompt: 'prompt'},
            },
            signal,
        }, onChunk)
    })

    test('streams debug chat with registered backend tool names', async () => {
        const {streamJsonLines} = await import('../../services/request')
        const signal = new AbortController().signal
        const onChunk = vi.fn()

        void streamAgentDebugChat({
            message: 'hello',
            overrides: {
                toolConfig: {
                    enabledToolNames: ['searchWikipedia', 'searchDuckDuckGoNews', 'searchBraveWeb', 'searchArxiv'],
                },
            },
        }, signal, onChunk)

        expect(streamJsonLines).toHaveBeenCalledWith('/api/agent/debug/chat/stream', {
            body: {
                message: 'hello',
                overrides: {
                    toolConfig: {
                        enabledToolNames: ['searchWikipedia', 'searchDuckDuckGoNews', 'searchBraveWeb', 'searchArxiv'],
                    },
                },
            },
            signal,
        }, onChunk)
    })

    test('builds audit list and message requests', async () => {
        const {requestJson} = await import('../../services/request')

        void getAgentConversationAudits({
            page: 3,
            size: 40,
            username: 'luigi',
            status: 'SUCCESS',
            threadId: 'thread-1',
        })
        void getAgentConversationAuditMessages(12)

        expect(requestJson).toHaveBeenNthCalledWith(
            1,
            '/api/admin/agent/conversation-audits?page=3&size=40&username=luigi&threadId=thread-1&status=SUCCESS',
        )
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/admin/agent/conversation-audits/12/messages')
    })

    test('builds run audit list and event requests', async () => {
        const {requestJson} = await import('../../services/request')

        void getAgentRunAudits({
            page: 2,
            size: 30,
            startAt: '2026-06-15T00:00:00.000Z',
            endAt: '2026-06-16T00:00:00.000Z',
            username: 'luigi',
            threadId: 'thread-1',
            requestId: 'request-1',
            traceId: 'trace-1',
            toolName: 'docs_search',
            mcpServerCode: 'docs',
            status: 'FAILED',
        })
        void getAgentRunAuditDetail(12)
        void getAgentRunAuditEvents(12)

        expect(requestJson).toHaveBeenNthCalledWith(
            1,
            '/api/admin/agent/run-audits?page=2&size=30&startAt=2026-06-15T00%3A00%3A00.000Z&endAt=2026-06-16T00%3A00%3A00.000Z&username=luigi&threadId=thread-1&requestId=request-1&traceId=trace-1&toolName=docs_search&mcpServerCode=docs&status=FAILED',
        )
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/admin/agent/run-audits/12')
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/admin/agent/run-audits/12/events')
    })
})
