package top.egon.mario.agent.tools.arxiv.po.enums;

/**
 * Runtime status for arXiv tool search and import logs.
 */
public enum ArxivToolLogStatus {

    SEARCHED,
    IMPORT_PENDING,
    IMPORT_RUNNING,
    IMPORT_SUCCESS,
    IMPORT_FAILED,
    IMPORT_SKIPPED

}
