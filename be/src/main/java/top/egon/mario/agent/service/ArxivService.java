package top.egon.mario.agent.service;

import org.springframework.ai.document.Document;

import java.util.List;

public interface ArxivService {
    List<Document> searchSummaries(String query);

    List<Document> readPapers(String query);
}
