import {beforeEach, describe, expect, test, vi} from 'vitest'
import {getRagDocuments, getRagIngestionJobs, getRagKnowledgeBases, streamRagChat} from './ragService'

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

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/rag/knowledge-bases?page=1&size=20')
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/rag/documents?page=2&size=30&knowledgeBaseId=10')
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/rag/ingestion-jobs?page=3&size=40&knowledgeBaseId=20')
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
})
