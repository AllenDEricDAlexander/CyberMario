package top.egon.mario.agent.service.impl;

import com.alibaba.cloud.ai.reader.arxiv.ArxivDocumentReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.agent.service.ArxivService;
import top.egon.mario.common.utils.LogUtil;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Validated
public class ArxivServiceImpl implements ArxivService {


    @Override
    public List<Document> searchSummaries(String query) {
        LogUtil.info(log).log("arxiv summary search started, queryLength={}", query == null ? 0 : query.length());
        ArxivDocumentReader reader = new ArxivDocumentReader(query, 5);
        List<Document> summaries = reader.getSummaries();
        LogUtil.info(log).log("arxiv summary search completed, resultCount={}", summaries.size());
        return summaries;
    }

    @Override
    public List<Document> readPapers(String query) {
        LogUtil.info(log).log("arxiv paper read started, queryLength={}", query == null ? 0 : query.length());
        ArxivDocumentReader reader = new ArxivDocumentReader(query, 3);
        List<Document> papers = reader.get();
        LogUtil.info(log).log("arxiv paper read completed, resultCount={}", papers.size());
        return papers;
    }
}
