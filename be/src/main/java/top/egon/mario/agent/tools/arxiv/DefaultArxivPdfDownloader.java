package top.egon.mario.agent.tools.arxiv;

import org.springframework.stereotype.Component;
import top.egon.mario.agent.tools.arxiv.dto.ArxivPaper;
import top.egon.mario.rag.service.RagException;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * HTTP downloader for arXiv PDFs used by background imports.
 */
@Component
public class DefaultArxivPdfDownloader implements ArxivPdfDownloader {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public Path download(ArxivPaper paper) {
        try {
            Path tempFile = Files.createTempFile("arxiv-paper-", ".pdf");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(paper.pdfUrl()))
                    .header("User-Agent", "CyberMario-arxiv-import/1.0")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new RagException("ARXIV_PDF_DOWNLOAD_FAILED", "arXiv PDF download failed");
            }
            try (InputStream inputStream = response.body()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return tempFile;
        } catch (Exception e) {
            throw new RagException("ARXIV_PDF_DOWNLOAD_FAILED", "arXiv PDF download failed");
        }
    }

}
