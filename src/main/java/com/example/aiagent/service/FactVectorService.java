package com.example.aiagent.service;

import com.example.aiagent.model.FactItem;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FactVectorService {

    private final SimpleVectorStore vectorStore;
    private final File vectorFile = new File("vector_facts.json");

    public FactVectorService(EmbeddingModel embeddingModel) {
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        if (vectorFile.exists() && vectorFile.length() > 0) {
            System.out.println(
                    "--- [FactVectorService] Loading existing vector store from " + vectorFile.getName() + " ---");
            vectorStore.load(vectorFile);
        }
    }

    /**
     * 存储事实列表，相似度超过 0.90 的旧记录会被覆盖更新。
     * metadata 中会写入 storedAt：优先使用聊天记录的原始时间戳，
     * 若未回填（messageTimestamp == 0）则回退为当前系统时间。
     */
    public int store(List<FactItem> facts) {
        List<Document> documents = new java.util.ArrayList<>();
        int deduplicatedCount = 0;
        long fallbackTimestamp = Instant.now().toEpochMilli();

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
            // 优先使用聊天记录原始时间，未回填则用当前系统时间兼容旧数据
            long storedAt = fact.getMessageTimestamp() > 0 ? fact.getMessageTimestamp() : fallbackTimestamp;
            metadata.put("storedAt", storedAt);
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

    /**
     * 召回全量记忆（极低阈值 + 超大 topK），供 MemoryCompactionService 扫描使用。
     */
    public List<Document> recallAll() {
        SearchRequest request = SearchRequest.builder()
                .query("记忆 事实 经历 习惯")
                .topK(5000)
                .similarityThreshold(0.0)
                .build();
        return vectorStore.similaritySearch(request);
    }

    /** 将检索到的事实格式化为 Prompt 片段，含置信度低提示和相对时间标签 */
    public String formatAsPrompt(List<Document> memories) {
        if (memories.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【补充事实背景（若与当前话题无关请绝对无视，严禁强行生搬硬套！）】：\n");
        Instant now = Instant.now();
        for (Document doc : memories) {
            String content = doc.getText();
            String source = (String) doc.getMetadata().getOrDefault("sourceQuote", "未知");
            Object confObj = doc.getMetadata().get("confidence");
            double conf = (confObj instanceof Number n) ? n.doubleValue() : 1.0;

            // 计算相对时间标签
            String timeLabel = "";
            Object storedAtObj = doc.getMetadata().get("storedAt");
            if (storedAtObj instanceof Number storedAtNum) {
                Instant storedAt = Instant.ofEpochMilli(storedAtNum.longValue());
                long days = ChronoUnit.DAYS.between(storedAt, now);
                if (days == 0) {
                    timeLabel = "（今日）";
                } else if (days < 30) {
                    timeLabel = String.format("（%d 天前）", days);
                } else {
                    long months = ChronoUnit.MONTHS.between(storedAt, now);
                    timeLabel = String.format("（约 %d 个月前）", months);
                }
            }

            sb.append("- ").append(content).append(timeLabel);
            if (conf < 0.8) {
                sb.append(" (置信度较低，请谨慎参考)");
            }
            sb.append("\n  [证据摘录]: \"").append(source).append("\"\n");
        }
        return sb.toString();
    }

    // ─── 供 MemoryCompactionService 调用的内部操作方法 ─────────────────────────

    /** 批量删除指定 ID 的文档 */
    public void deleteDocuments(List<String> ids) {
        if (ids == null || ids.isEmpty())
            return;
        vectorStore.delete(ids);
    }

    /** 批量写入新文档（不持久化，由调用方统一调用 saveStore） */
    public void addDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty())
            return;
        vectorStore.add(documents);
    }

    /** 将当前内存中的向量库持久化到本地文件 */
    public void saveStore() {
        vectorStore.save(vectorFile);
        System.out.println("--- [FactVectorService] Vector store saved to " + vectorFile.getName() + " ---");
    }
}
