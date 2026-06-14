import {requestFormData, requestJson, streamJsonLines} from '../../services/request'
import {buildSearchParams} from '../../services/urlSearch'
import type {
    KnowledgeBaseResponse,
    KnowledgeBaseUserResponse,
    RagChatRequest,
    RagChunkResponse,
    RagDocumentResponse,
    RagIngestionJobResponse,
    RagPage,
    RagStreamEvent,
    RetrievalSearchResponse,
} from './ragTypes'

type PageParams = {
    page?: number
    size?: number
}

export function getRagKnowledgeBases(params: PageParams) {
    return requestJson<RagPage<KnowledgeBaseResponse>>(`/api/rag/knowledge-bases?${buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
    })}`)
}

export function createRagKnowledgeBase(request: Partial<KnowledgeBaseResponse>) {
    return requestJson<KnowledgeBaseResponse>('/api/rag/knowledge-bases', {
        method: 'POST',
        body: request,
    })
}

export function updateRagKnowledgeBase(id: number, request: Partial<KnowledgeBaseResponse>) {
    return requestJson<KnowledgeBaseResponse>(`/api/rag/knowledge-bases/${id}`, {
        method: 'PUT',
        body: request,
    })
}

export function deleteRagKnowledgeBase(id: number) {
    return requestJson<void>(`/api/rag/knowledge-bases/${id}`, {
        method: 'DELETE',
    })
}

export function getRagKnowledgeBaseUsers(id: number) {
    return requestJson<KnowledgeBaseUserResponse[]>(`/api/rag/knowledge-bases/${id}/users`)
}

export function replaceRagKnowledgeBaseUsers(id: number, users: Array<{ userId: number; accessLevel: string }>) {
    return requestJson<KnowledgeBaseUserResponse[]>(`/api/rag/knowledge-bases/${id}/users`, {
        method: 'PUT',
        body: {users},
    })
}

export function getRagDocuments(params: PageParams & { knowledgeBaseId?: number }) {
    return requestJson<RagPage<RagDocumentResponse>>(`/api/rag/documents?${buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
        knowledgeBaseId: params.knowledgeBaseId,
    })}`)
}

export function uploadRagDocuments(request: { knowledgeBaseId: number; files: File[]; parseImmediately: boolean }) {
    const formData = new FormData()
    request.files.forEach((file) => formData.append('files', file))
    return requestFormData<{ documents: RagDocumentResponse[]; jobIds: number[] }>(
        `/api/rag/documents/upload?knowledgeBaseId=${request.knowledgeBaseId}&parseImmediately=${request.parseImmediately}`,
        formData,
    )
}

export function importRagText(request: {
    knowledgeBaseId: number;
    title: string;
    content: string;
    parseImmediately: boolean
}) {
    return requestJson<RagDocumentResponse>('/api/rag/documents/import-text', {
        method: 'POST',
        body: request,
    })
}

export function deleteRagDocument(id: number) {
    return requestJson<void>(`/api/rag/documents/${id}`, {
        method: 'DELETE',
    })
}

export function reindexRagDocument(id: number) {
    return requestJson<RagDocumentResponse>(`/api/rag/documents/${id}/reindex`, {
        method: 'POST',
    })
}

export function getRagChunks(documentId: number, params: PageParams) {
    return requestJson<RagPage<RagChunkResponse>>(`/api/rag/documents/${documentId}/chunks?page=${params.page ?? 1}&size=${params.size ?? 20}`)
}

export function updateRagChunkEnabled(id: number, enabled: boolean) {
    return requestJson<void>(`/api/rag/chunks/${id}/enabled`, {
        method: 'PATCH',
        body: {enabled},
    })
}

export function getRagIngestionJobs(params: PageParams & { knowledgeBaseId?: number }) {
    return requestJson<RagPage<RagIngestionJobResponse>>(`/api/rag/ingestion-jobs?${buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
        knowledgeBaseId: params.knowledgeBaseId,
    })}`)
}

export function retryRagIngestionJob(id: number) {
    return requestJson<RagIngestionJobResponse>(`/api/rag/ingestion-jobs/${id}/retry`, {
        method: 'POST',
    })
}

export function cancelRagIngestionJob(id: number) {
    return requestJson<void>(`/api/rag/ingestion-jobs/${id}/cancel`, {
        method: 'POST',
    })
}

export function searchRagRetrieval(request: {
    query: string
    knowledgeBaseIds: number[]
    topK: number
    similarityThreshold: number
    searchMode: string
}) {
    return requestJson<RetrievalSearchResponse>('/api/rag/retrieval/search', {
        method: 'POST',
        body: request,
    })
}

export function streamRagChat(request: RagChatRequest, signal: AbortSignal, onChunk: (event: RagStreamEvent) => void) {
    return streamJsonLines<RagStreamEvent>('/api/rag/chat/stream', {body: request, signal}, onChunk)
}
