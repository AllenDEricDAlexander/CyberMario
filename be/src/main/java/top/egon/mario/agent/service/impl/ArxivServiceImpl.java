package top.egon.mario.agent.service.impl;

import com.alibaba.cloud.ai.reader.arxiv.ArxivDocumentReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import top.egon.mario.agent.service.ArxivService;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArxivServiceImpl implements ArxivService {


    @Override
    public List<Document> searchSummaries(String query) {
        log.info("arxiv summary search started, queryLength={}", query == null ? 0 : query.length());
        ArxivDocumentReader reader = new ArxivDocumentReader(query, 5);
        List<Document> summaries = reader.getSummaries();
        log.info("arxiv summary search completed, resultCount={}", summaries.size());
        return summaries;
    }

    @Override
    public List<Document> readPapers(String query) {
        log.info("arxiv paper read started, queryLength={}", query == null ? 0 : query.length());
        ArxivDocumentReader reader = new ArxivDocumentReader(query, 3);
        List<Document> papers = reader.get();
        log.info("arxiv paper read completed, resultCount={}", papers.size());
        return papers;
    }
}
