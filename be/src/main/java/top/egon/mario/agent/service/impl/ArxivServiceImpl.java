package top.egon.mario.agent.service.impl;

import com.alibaba.cloud.ai.reader.arxiv.ArxivDocumentReader;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import top.egon.mario.agent.service.ArxivService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArxivServiceImpl implements ArxivService {


    @Override
    public List<Document> searchSummaries(String query) {
        ArxivDocumentReader reader = new ArxivDocumentReader(query, 5);
        return reader.getSummaries();
    }

    @Override
    public List<Document> readPapers(String query) {
        ArxivDocumentReader reader = new ArxivDocumentReader(query, 3);
        return reader.get();
    }
}
