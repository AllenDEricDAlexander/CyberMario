package top.egon.mario.agent.tools.arxiv.dto;

/**
 * Background arXiv paper import status returned by the tool call.
 */
public record ArxivImportJob(
        String entryId,
        Long documentId,
        Long ragIngestionJobId,
        String status,
        String message
) {
}
