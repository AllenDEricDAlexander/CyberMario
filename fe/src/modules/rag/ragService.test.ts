import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    createRagFeedback,
    getRagRetrievalTrace,
    getRagSettings,
    getArxivToolLogs,
    getRagDocuments,
    getRagIngestionJobs,
    getRagKnowledgeBases,
    streamRagChat,
} from './ragService'

vi.mock('../../services/request', () => ({
    requestFormData: vi.fn(),
    requestJson: vi.fn(),
    streamJsonLines: vi.fn(),
}))

describe('ragService', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    test('builds encoded page query strings for normal JSON APIs', async () => {
        const {requestJson} = await import('../../services/request')

        void getRagKnowledgeBases({})
        void getRagDocuments({page: 2, size: 30, knowledgeBaseId: 10})
        void getRagIngestionJobs({page: 3, size: 40, knowledgeBaseId: 20})
        void getArxivToolLogs({page: 4, size: 50})

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/rag/knowledge-bases?page=1&size=20')
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/rag/documents?page=2&size=30&knowledgeBaseId=10')
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/rag/ingestion-jobs?page=3&size=40&knowledgeBaseId=20')
        expect(requestJson).toHaveBeenNthCalledWith(4, '/api/admin/agent/arxiv/logs?page=4&size=50')
    })

    test('uses NDJSON stream helper only for real streaming chat API', async () => {
        const {streamJsonLines} = await import('../../services/request')
        const signal = new AbortController().signal
        const onChunk = vi.fn()

        void streamRagChat({
            question: 'hello',
            knowledgeBaseIds: [1],
        }, signal, onChunk)

        expect(streamJsonLines).toHaveBeenCalledWith('/api/rag/chat/stream', {
            body: {
                question: 'hello',
                knowledgeBaseIds: [1],
            },
            signal,
        }, onChunk)
    })

    test('calls trace feedback and settings endpoints', async () => {
        const {requestJson} = await import('../../services/request')

        void getRagRetrievalTrace('trace-1')
        void getRagSettings()
        void createRagFeedback({feedbackType: 'HELPFUL', traceId: 'trace-1'})

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/rag/retrieval/traces/trace-1')
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/rag/settings')
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/rag/feedback', {
            method: 'POST',
            body: {feedbackType: 'HELPFUL', traceId: 'trace-1'},
        })
    })
})
