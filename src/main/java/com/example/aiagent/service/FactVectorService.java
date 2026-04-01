package com.example.aiagent.service;

import com.example.aiagent.model.FactItem;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FactVectorService {

    private final SimpleVectorStore vectorStore;
    private final File vectorFile = new File("vector_facts.json");

    public FactVectorService(EmbeddingModel embeddingModel) {
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        if (vectorFile.exists()) {
            System.out.println(
                    "--- [FactVectorService] Loading existing vector store from " + vectorFile.getName() + " ---");
            vectorStore.load(vectorFile);
        }
    }

    /**
     * 存储事实列表，相似度超过 0.90 的旧记录会被覆盖更新
     *
     * TODO: 在 metadata 中回填系统时间戳，formatAsPrompt 时计算相对时间，防止时空错乱
     * TODO: 实现定时记忆压缩任务，让模型扫描并合并相互矛盾的旧记录
     */
    public int store(List<FactItem> facts) {
        List<Document> documents = new java.util.ArrayList<>();
        int deduplicatedCount = 0;

        for (FactItem fact : facts) {
            // 高阈值检索，判断是否为重复记忆
            SearchRequest request = SearchRequest.builder()
                    .query(fact.getContent())
                    .topK(1)
                    .similarityThreshold(0.90)
                    .build();

            List<Document> existing = vectorStore.similaritySearch(request);
            if (!existing.isEmpty()) {
                // 删除旧记录，实现覆盖更新
                vectorStore.delete(List.of(existing.get(0).getId()));
                deduplicatedCount++;
                System.out.println("--- [FactVectorService] Deduplication triggered: Overwriting old memory: "
                        + existing.get(0).getText() + " ---");
            }

            Map<String, Object> metadata = new HashMap<>();
            addIfNotNull(metadata, "sourceQuote", fact.getExactSourceQuote());
            addIfNotNull(metadata, "confidence", fact.getConfidence());
            documents.add(new Document(fact.getContent(), metadata));
        }

        vectorStore.add(documents);
        System.out.println("--- [FactVectorService] Saving vector store to " + vectorFile.getName() + ". Deduplicated "
                + deduplicatedCount + " items ---");
        vectorStore.save(vectorFile);
        return documents.size();
    }

    private void addIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    public List<Document> recall(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.50)
                .build();
        return vectorStore.similaritySearch(request);
    }

    /** 将检索到的事实格式化为 Prompt 片段，含置信度低提示 */
    public String formatAsPrompt(List<Document> memories) {
        if (memories.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【补充事实背景（若与当前话题无关请绝对无视，严禁强行生搬硬套！）】：\n");
        for (Document doc : memories) {
            String content = doc.getText();
            String source = (String) doc.getMetadata().getOrDefault("sourceQuote", "未知");
            Object confObj = doc.getMetadata().get("confidence");
            double conf = (confObj instanceof Number n) ? n.doubleValue() : 1.0;

            sb.append("- ").append(content);
            if (conf < 0.8) {
                sb.append(" (置信度较低，请谨慎参考)");
            }
            sb.append("\n  [证据摘录]: \"").append(source).append("\"\n");
        }
        return sb.toString();
    }
}
