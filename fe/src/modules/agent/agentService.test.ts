import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    createAgentPreset,
    archiveAgentMemorySession,
    createAgentMemorySession,
    deleteAgentPreset,
    deleteAgentMemorySession,
    getAgentLongTermMemory,
    getAgentLongTermMemoryVersions,
    getAgentMemoryExtractions,
    getAgentMemoryMessages,
    getAgentMemorySessions,
    getAgentConversationAuditMessages,
    getAgentConversationAudits,
    getAgentRunAuditEvents,
    getAgentRunAuditDetail,
    getAgentRunAudits,
    getAgentPresets,
    releaseAgentMemorySession,
    restoreAgentMemorySession,
    streamAgentDebugChat,
    updateAgentMemorySession,
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
            sessionId: 'session-1',
            memoryEnabled: true,
            longTermExtractionEnabled: false,
            presetId: 9,
            overrides: {systemPrompt: 'prompt'},
        }, signal, onChunk)

        expect(streamJsonLines).toHaveBeenCalledWith('/api/agent/debug/chat/stream', {
            body: {
                message: 'hello',
                threadId: 'thread-1',
                sessionId: 'session-1',
                memoryEnabled: true,
                longTermExtractionEnabled: false,
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

    test('builds memory session requests', async () => {
        const {requestJson} = await import('../../services/request')

        void getAgentMemorySessions({page: 2, size: 30, entryType: 'AGENT_CHAT'})
        void createAgentMemorySession({entryType: 'AGENT_CHAT', title: 'Chat'})
        void updateAgentMemorySession('session-1', {memoryEnabled: false})
        void releaseAgentMemorySession('session-1')
        void restoreAgentMemorySession('session-1')
        void archiveAgentMemorySession('session-1')
        void deleteAgentMemorySession('session-1')
        void getAgentMemoryMessages('session-1')
        void getAgentLongTermMemory()
        void getAgentLongTermMemoryVersions()
        void getAgentMemoryExtractions()

        expect(requestJson).toHaveBeenNthCalledWith(
            1,
            '/api/agent/memory/sessions?page=2&size=30&entryType=AGENT_CHAT',
        )
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/agent/memory/sessions', {
            method: 'POST',
            body: {entryType: 'AGENT_CHAT', title: 'Chat'},
        })
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/agent/memory/sessions/session-1', {
            method: 'PATCH',
            body: {memoryEnabled: false},
        })
        expect(requestJson).toHaveBeenNthCalledWith(4, '/api/agent/memory/sessions/session-1/release', {
            method: 'POST',
        })
        expect(requestJson).toHaveBeenNthCalledWith(5, '/api/agent/memory/sessions/session-1/restore', {
            method: 'POST',
        })
        expect(requestJson).toHaveBeenNthCalledWith(6, '/api/agent/memory/sessions/session-1/archive', {
            method: 'POST',
        })
        expect(requestJson).toHaveBeenNthCalledWith(7, '/api/agent/memory/sessions/session-1', {
            method: 'DELETE',
        })
        expect(requestJson).toHaveBeenNthCalledWith(8, '/api/agent/memory/sessions/session-1/messages')
        expect(requestJson).toHaveBeenNthCalledWith(9, '/api/agent/memory/long-term')
        expect(requestJson).toHaveBeenNthCalledWith(10, '/api/agent/memory/long-term/versions')
        expect(requestJson).toHaveBeenNthCalledWith(11, '/api/agent/memory/extractions')
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
