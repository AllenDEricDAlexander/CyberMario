import type {PageResult} from '../../types/api'

export type RagKnowledgeBaseStatus = 'ENABLED' | 'DISABLED'
export type RagAccessLevel = 'READ' | 'WRITE' | 'MANAGE'
export type RagDocumentStatus = 'UPLOADED' | 'PARSING' | 'CHUNKING' | 'EMBEDDING' | 'INDEXED' | 'FAILED' | 'DELETED'
export type RagFileType = 'MD' | 'TXT' | 'PDF' | 'DOCX' | 'TEXT'
export type RagDocumentSourceType = 'UPLOAD' | 'TEXT'
export type RagJobStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELED'
export type RagJobStep = 'UPLOAD' | 'PARSE' | 'CHUNK' | 'EMBEDDING' | 'INDEX' | 'DONE'
export type RagSearchMode = 'VECTOR' | 'KEYWORD' | 'HYBRID' | 'HYBRID_RERANK'

export type KnowledgeBaseResponse = {
    id: number
    code: string
    name: string
    description?: string
    defaultTopK: number
    defaultSimilarityThreshold: number
    status: RagKnowledgeBaseStatus
    createdAt?: string
    updatedAt?: string
}

export type KnowledgeBaseUserResponse = {
    id: number
    knowledgeBaseId: number
    userId: number
    accessLevel: RagAccessLevel
}

export type RagDocumentResponse = {
    id: number
    userId: number
    knowledgeBaseId: number
    fileObjectId?: number
    displayName: string
    sourceType: RagDocumentSourceType
    fileType: RagFileType
    contentHash: string
    status: RagDocumentStatus
    chunkCount: number
    indexedChunkCount: number
    errorMessage?: string
    createdAt?: string
    updatedAt?: string
}

export type RagChunkResponse = {
    id: number
    documentId: number
    knowledgeBaseId: number
    chunkIndex: number
    content: string
    contentPreview: string
    tokenCount: number
    enabled: boolean
    metadataJson?: string
    createdAt?: string
}

export type RagIngestionJobResponse = {
    id: number
    documentId: number
    knowledgeBaseId: number
    status: RagJobStatus
    currentStep: RagJobStep
    progress: number
    chunkCount: number
    successCount: number
    failedCount: number
    errorMessage?: string
    startedAt?: string
    finishedAt?: string
    createdAt?: string
}

export type SourceReferenceResponse = {
    sourceId: string
    knowledgeBaseId: number
    knowledgeBaseName: string
    documentId: number
    documentName: string
    chunkId: number
    chunkIndex: number
    score: number
    content: string
    metadata: Record<string, unknown>
}

export type RetrievalSearchResponse = {
    query: string
    results: SourceReferenceResponse[]
    costMs: number
}

export type RagChatRequest = {
    sessionId?: string
    question: string
    knowledgeBaseIds: number[]
    retrievalOptions?: {
        topK?: number
        similarityThreshold?: number
        searchMode?: RagSearchMode
    }
    withSources?: boolean
}

export type RagStreamEvent =
    | { type: 'metadata'; data: { messageId: string; traceId: string } }
    | { type: 'retrieval'; data: { sources: SourceReferenceResponse[]; topK: number } }
    | { type: 'delta'; data: { content: string } }
    | { type: 'done'; data: { finishReason: string } }
    | { type: 'error'; data: { code: string; message: string; traceId?: string } }

export type RagPage<T> = PageResult<T>
