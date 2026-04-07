package com.example.aiagent.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 定时记忆压缩服务（Memory Compaction）。
 *
 * <p>
 * 每天凌晨 3 点自动运行，召回向量库中的全量事实记忆，
 * 调用大模型识别"相互矛盾或高度重复"的记忆对，
 * 将其合并为一条更权威的记忆，删除旧记录后写入合并结果。
 *
 * <p>
 * 设计原则：
 * <ul>
 * <li>合并记忆保留最早的 {@code storedAt} 时间戳，避免切断时间线</li>
 * <li>新增 {@code compactedAt} 字段，标记本次压缩时间</li>
 * <li>模型只需返回需要合并的对，不冲突的记忆保持原样</li>
 * </ul>
 */
@Service
public class MemoryCompactionService {

    private final FactVectorService factVectorService;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MemoryCompactionService(FactVectorService factVectorService,
            ChatModel chatModel) {
        this.factVectorService = factVectorService;
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    // ─── 大模型返回结构 ───────────────────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConflictPair {
        /** 被合并掉的原始事实文本（完整匹配用） */
        private String oldFact;
        /** 保留/更新的原始事实文本（完整匹配用） */
        private String newFact;
        /** 合并后的权威事实 */
        private String mergedFact;
    }

    // ─── 核心压缩逻辑 ─────────────────────────────────────────────────────────

    /**
     * 定时任务入口，每天 03:00 自动触发。
     * 也可通过调用此方法手动触发（例如在测试中直接 invoke）。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void compact() {
        System.out.println("--- [MemoryCompaction] Starting memory compaction task ---");

        // 1. 召回全量记忆
        List<Document> allDocs = factVectorService.recallAll();
        if (allDocs.size() < 2) {
            System.out.println(
                    "--- [MemoryCompaction] Too few memories to compact (" + allDocs.size() + "), skipping ---");
            return;
        }
        System.out.println("--- [MemoryCompaction] Total memories to scan: " + allDocs.size() + " ---");

        // 2. 构建 Prompt，要求大模型只输出需要合并的对
        String factList = buildFactList(allDocs);
        String prompt = buildCompactionPrompt(factList);

        // 3. 调用大模型，解析返回的 JSON 数组
        List<ConflictPair> conflicts;
        try {
            String responseText = chatClient.prompt().user(prompt).call().content();
            // 提取 JSON 数组部分（防止模型在前后加说明文字）
            String json = extractJsonArray(responseText);
            conflicts = objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            System.err.println("--- [MemoryCompaction] Failed to parse model response: " + e.getMessage() + " ---");
            return;
        }

        if (conflicts.isEmpty()) {
            System.out.println("--- [MemoryCompaction] No conflicts detected, compaction complete ---");
            return;
        }
        System.out.println("--- [MemoryCompaction] Detected " + conflicts.size() + " conflict pair(s), merging ---");

        // 4. 逐对合并：删旧写新
        long compactedAt = Instant.now().toEpochMilli();
        List<String> toDelete = new ArrayList<>();
        List<Document> toAdd = new ArrayList<>();

        for (ConflictPair pair : conflicts) {
            // 从全量文档中找出需要删除的文档
            Document oldDoc = findByText(allDocs, pair.getOldFact());
            Document newDoc = findByText(allDocs, pair.getNewFact());

            if (oldDoc == null || newDoc == null) {
                System.err.println("--- [MemoryCompaction] Could not find documents for pair: ["
                        + pair.getOldFact() + "] / [" + pair.getNewFact() + "] ---");
                continue;
            }

            // 标记两条旧记忆均需删除
            toDelete.add(oldDoc.getId());
            toDelete.add(newDoc.getId());

            // 合并记忆：保留最早的 storedAt
            long oldStoredAt = getLongMeta(oldDoc, "storedAt", compactedAt);
            long newStoredAt = getLongMeta(newDoc, "storedAt", compactedAt);
            long earliestStoredAt = Math.min(oldStoredAt, newStoredAt);

            Map<String, Object> metadata = new HashMap<>();
            // 尽量复用更新记忆的 sourceQuote
            Object sourceQuote = newDoc.getMetadata().getOrDefault("sourceQuote",
                    oldDoc.getMetadata().get("sourceQuote"));
            if (sourceQuote != null) {
                metadata.put("sourceQuote", sourceQuote);
            }
            Object confidence = newDoc.getMetadata().getOrDefault("confidence",
                    oldDoc.getMetadata().get("confidence"));
            if (confidence != null) {
                metadata.put("confidence", confidence);
            }
            metadata.put("storedAt", earliestStoredAt);
            metadata.put("compactedAt", compactedAt);

            toAdd.add(new Document(pair.getMergedFact(), metadata));

            System.out.printf("--- [MemoryCompaction] Merging:%n  OLD: %s%n  NEW: %s%n  MERGED: %s%n",
                    pair.getOldFact(), pair.getNewFact(), pair.getMergedFact());
        }

        // 5. 批量删除 + 写入
        if (!toDelete.isEmpty()) {
            factVectorService.deleteDocuments(toDelete);
        }
        if (!toAdd.isEmpty()) {
            factVectorService.addDocuments(toAdd);
        }

        System.out.println("--- [MemoryCompaction] Compaction complete. Merged "
                + conflicts.size() + " pair(s). Saving vector store ---");
        factVectorService.saveStore();
    }

    // ─── 辅助方法 ─────────────────────────────────────────────────────────────

    private String buildFactList(List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            sb.append("[").append(i).append("] ").append(docs.get(i).getText()).append("\n");
        }
        return sb.toString();
    }

    private String buildCompactionPrompt(String factList) {
        return """
                你是一个记忆管理AI，负责整理一个AI角色的长期事实记忆库。

                【任务】：
                阅读下列编号事实列表，找出其中「相互矛盾」或「高度重复（表达同一件事但措辞不同）」的记忆对。
                对于每一对冲突/重复，输出一个合并建议。

                【输出要求】：
                - 只输出一个 JSON 数组，不要有任何额外说明文字
                - 数组中每个元素格式如下（字段名必须完全一致）：
                  {"oldFact": "...", "newFact": "...", "mergedFact": "..."}
                  - oldFact: 被废弃的那条原始事实（完整复制，一字不差）
                  - newFact: 保留/更优先的那条原始事实（完整复制，一字不差）
                  - mergedFact: 你综合两条记忆得出的最权威、最准确的单条事实
                - 如果没有任何冲突或重复，输出空数组 []
                - 绝对不要把不冲突、不重复的记忆也纳入输出

                【事实列表】：
                %s
                """.formatted(factList);
    }

    private String extractJsonArray(String text) {
        if (text == null)
            return "[]";
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start == -1 || end == -1 || start > end)
            return "[]";
        return text.substring(start, end + 1);
    }

    private Document findByText(List<Document> docs, String text) {
        if (text == null)
            return null;
        return docs.stream()
                .filter(d -> text.equals(d.getText()))
                .findFirst()
                .orElse(null);
    }

    private long getLongMeta(Document doc, String key, long defaultVal) {
        Object val = doc.getMetadata().get(key);
        return (val instanceof Number n) ? n.longValue() : defaultVal;
    }
}
