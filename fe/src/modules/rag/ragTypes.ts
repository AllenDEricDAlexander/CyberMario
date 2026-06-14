import type {PageResult} from '../../types/api'

export type RagKnowledgeBaseStatus = 'ENABLED' | 'DISABLED'
export type RagAccessLevel = 'READ' | 'WRITE' | 'MANAGE'
export type RagDocumentStatus = 'UPLOADED' | 'PARSING' | 'CHUNKING' | 'EMBEDDING' | 'INDEXED' | 'FAILED' | 'DELETED'
export type RagFileType = 'MD' | 'TXT' | 'PDF' | 'DOCX' | 'TEXT'
export type RagDocumentSourceType = 'UPLOAD' | 'TEXT' | 'ARXIV'
export type RagJobStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELED'
export type RagJobStep = 'UPLOAD' | 'PARSE' | 'CHUNK' | 'EMBEDDING' | 'INDEX' | 'DONE'
export type RagSearchMode = 'VECTOR' | 'KEYWORD' | 'HYBRID' | 'HYBRID_RERANK'
export type ArxivToolLogStatus =
    | 'SEARCHED'
    | 'IMPORT_PENDING'
    | 'IMPORT_RUNNING'
    | 'IMPORT_SUCCESS'
    | 'IMPORT_FAILED'
    | 'IMPORT_SKIPPED'

export type KnowledgeBaseResponse = {
    id: number
    code: string
    name: string
    description?: string
    defaultTopK: number
    defaultSimilarityThreshold: number
    defaultSearchMode: RagSearchMode
    rerankEnabled: boolean
    vectorWeight: number
    keywordWeight: number
    candidateTopK: number
    contextTopK: number
    chunkSize: number
    chunkOverlap: number
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
    sourceUri?: string
    parserType?: string
    chunkStrategy?: string
    embeddingModel?: string
    embeddingDimension?: number
    indexedAt?: string
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
    contentHash?: string
    headingPath?: string
    startOffset?: number
    endOffset?: number
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

export type ArxivToolLogResponse = {
    id: number
    requestId: string
    requestUserId?: number
    requestUsername?: string
    query: string
    maxResults: number
    includeFullText: boolean
    resultCount: number
    knowledgeBaseId?: number
    entryId?: string
    title?: string
    pdfUrl?: string
    status: ArxivToolLogStatus
    documentId?: number
    ragIngestionJobId?: number
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
    vectorScore?: number
    keywordScore?: number
    fusionScore?: number
    rerankScore?: number
    matchedBy?: string
    content: string
    metadata: Record<string, unknown>
}

export type RetrievalSearchResponse = {
    query: string
    searchMode: RagSearchMode
    traceId: string
    results: SourceReferenceResponse[]
    stages: {
        vector: SourceReferenceResponse[]
        keyword: SourceReferenceResponse[]
        fused: SourceReferenceResponse[]
        reranked: SourceReferenceResponse[]
    }
    costMs: number
}

export type RagChatRequest = {
    sessionId?: string
    question: string
    knowledgeBaseIds: number[]
    retrievalOptions?: {
        topK?: number
        candidateTopK?: number
        similarityThreshold?: number
        searchMode?: RagSearchMode
        rerankEnabled?: boolean
    }
    withSources?: boolean
}

export type RagStreamEvent =
    | { type: 'metadata'; data: { messageId: string; traceId: string; searchMode?: RagSearchMode } }
    | { type: 'retrieval'; data: { sources: SourceReferenceResponse[]; topK: number } }
    | { type: 'delta'; data: { content: string } }
    | { type: 'done'; data: { finishReason: string } }
    | { type: 'error'; data: { code: string; message: string; traceId?: string } }

export type RagPage<T> = PageResult<T>

export type RagRetrievalTraceResponse = {
    traceId: string
    userId?: number
    query: string
    searchMode: RagSearchMode
    rerankEnabled: boolean
    degraded: boolean
    degradeReason?: string
    costMs: number
    vector: SourceReferenceResponse[]
    keyword: SourceReferenceResponse[]
    fused: SourceReferenceResponse[]
    reranked: SourceReferenceResponse[]
    createdAt?: string
}

export type RagFeedbackType = 'HELPFUL' | 'NOT_HELPFUL' | 'BAD_SOURCE' | 'NO_ANSWER'

export type RagFeedbackRequest = {
    traceId?: string
    messageId?: string
    feedbackType: RagFeedbackType
    question?: string
    answer?: string
    sourceChunkIds?: number[]
    comment?: string
}

export type RagSettingsResponse = {
    chatModel: string
    embeddingModel: string
    embeddingDimension: number
    defaultSearchMode: RagSearchMode
    defaultTopK: number
    candidateTopK: number
    contextTopK: number
    defaultSimilarityThreshold: number
    rerankEnabled: boolean
    rerankModel: string
}
