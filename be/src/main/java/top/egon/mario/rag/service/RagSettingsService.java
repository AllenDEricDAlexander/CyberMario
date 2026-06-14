package top.egon.mario.rag.service;

import top.egon.mario.rag.dto.response.RagSettingsResponse;

/**
 * Read-only RAG system settings service.
 */
public interface RagSettingsService {

    RagSettingsResponse settings();

}
