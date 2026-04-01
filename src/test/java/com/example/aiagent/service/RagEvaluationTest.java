package com.example.aiagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.util.List;

@SpringBootTest
public class RagEvaluationTest {

    @Autowired
    private FactVectorService factVectorService;

    public record RagTestCase(String question, String ground_truth_context) {
    }

    @Test
    public void testRagRecallHitRate() throws IOException {
        // 读取由 AI 生成的/手工准备的黄金测试集
        ObjectMapper mapper = new ObjectMapper();
        File datasetFile = new File("rag_golden_dataset.json");
        List<RagTestCase> testCases = mapper.readValue(datasetFile, new TypeReference<>() {
        });

        int totalCases = testCases.size();
        int hitCount = 0;

        System.out.println("================ RAG RECALL EVALUATION ================");

        for (int i = 0; i < testCases.size(); i++) {
            RagTestCase testCase = testCases.get(i);
            System.out.printf("Test Case %d: %s\n", i + 1, testCase.question());

            // 模拟实际的 RAG 检索（Top 3）
            List<Document> retrievedDocs = factVectorService.recall(testCase.question(), 3);

            boolean isHit = false;
            for (Document doc : retrievedDocs) {
                // 如果返回的文档正文，或者原话引用(sourceQuote)中包含了我们期望的 ground truth，就算 Hit
                String text = doc.getText();
                Object sourceQuoteObj = doc.getMetadata().get("sourceQuote");
                String sourceQuote = sourceQuoteObj != null ? sourceQuoteObj.toString() : "";

                if (text.contains(testCase.ground_truth_context())
                        || sourceQuote.contains(testCase.ground_truth_context())) {
                    isHit = true;
                    break;
                }
            }

            if (isHit) {
                hitCount++;
                System.out.println("  -> [HIT] Successfully retrieved relevant context.");
            } else {
                System.out.println(
                        "  -> [MISS] Failed to retrieve context. Expected: " + testCase.ground_truth_context());
                System.out.println("     Actual Retrieved Docs (Text + sourceQuote):");
                retrievedDocs.forEach(doc -> {
                    Object sq = doc.getMetadata().get("sourceQuote");
                    System.out.println("       - Text: " + doc.getText() + " | Quote: " + (sq != null ? sq : "null"));
                });
            }
            System.out.println("-".repeat(50));
        }

        double hitRate = (double) hitCount / totalCases * 100;
        System.out.println("================ EVALUATION SUMMARY ================");
        System.out.printf("Total Questions : %d\n", totalCases);
        System.out.printf("Total Hits      : %d\n", hitCount);
        System.out.printf("Hit@3 Rate      : %.2f%%\n", hitRate);
        System.out.println("====================================================");

        if (hitRate < 80.0) {
            System.out.println(
                    "[WARNING] Hit rate is below 80%. Consider revising chunking strategy or adding metadata to your vectors.");
        }
    }
}
