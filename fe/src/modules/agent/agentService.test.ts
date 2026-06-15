import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    createAgentPreset,
    deleteAgentPreset,
    getAgentConversationAuditMessages,
    getAgentConversationAudits,
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
})
