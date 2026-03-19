package com.example.aiagent.service;

import com.example.aiagent.model.FactItem;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
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
     * 事实存储 2.0：彻底剥离瞬时情绪，只存储客观事实及其固有属性（时间、来源、置信度等）
     */
    public int store(List<FactItem> facts) {
        List<Document> documents = facts.stream()
                .map(fact -> {
                    Map<String, Object> metadata = new HashMap<>();
                    // 仅保留与事实客观属性相关的元数据
                    addIfNotNull(metadata, "sourceQuote", fact.getSourceQuote());
                    addIfNotNull(metadata, "category", fact.getCategory());
                    addIfNotNull(metadata, "confidence", fact.getConfidence());
                    addIfNotNull(metadata, "scope", fact.getScope());
                    addIfNotNull(metadata, "time", fact.getTime());

                    // 删除之前强行写入 emotion、catchphrases、metaphors 的逻辑
                    // 让事实保持纯粹的“干”状态，避免 ChatService 的混音器提取到过期的历史情绪

                    return new Document(fact.getContent(), metadata);
                })
                .toList();
        vectorStore.add(documents);
        System.out.println("--- [FactVectorService] Saving vector store to " + vectorFile.getName() + " ---");
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
                .similarityThreshold(0.50) // 最终权衡阈值：兼顾召回率与抗干扰
                .build();
        return vectorStore.similaritySearch(request);
    }

    /**
     * 格式化事实 Prompt 2.0：增加证据链和置信度提示
     */
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
