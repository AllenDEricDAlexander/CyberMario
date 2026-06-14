package top.egon.mario.agent.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.document.Document;

import java.util.List;

public interface ArxivService {
    List<Document> searchSummaries(@NotBlank String query);

    List<Document> readPapers(@NotBlank String query);
}
