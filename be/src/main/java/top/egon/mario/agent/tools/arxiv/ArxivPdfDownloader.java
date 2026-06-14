package top.egon.mario.agent.tools.arxiv;

import top.egon.mario.agent.tools.arxiv.dto.ArxivPaper;

import java.nio.file.Path;

/**
 * Downloads an arXiv paper PDF to a temporary file.
 */
@FunctionalInterface
public interface ArxivPdfDownloader {

    Path download(ArxivPaper paper);

}
