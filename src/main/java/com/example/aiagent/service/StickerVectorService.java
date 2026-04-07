package com.example.aiagent.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * 专属表情包向量检索服务。
 * 负责管理带有情感描述的表情包 Document。
 */
@Service
public class StickerVectorService {

    private final SimpleVectorStore vectorStore;
    private final File vectorFile = new File("vector_persona_stickers.json");

    public StickerVectorService(EmbeddingModel embeddingModel) {
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        if (vectorFile.exists() && vectorFile.length() > 0) {
            System.out.println("--- [StickerVectorService] Loading existing sticker vector store from "
                    + vectorFile.getName() + " ---");
            vectorStore.load(vectorFile);
        }
    }

    /**
     * 根据情绪描述和扮演身份召回表情包。
     * <p>
     * 使用了高严格度的 filterExpression，确保只能召回目标人物历史上真正发过的专属表情包（防串场）。
     *
     * @param emotionText 系统大模型开出的预期情感（如"无奈"、"得意"）
     * @param targetName  当前扮演的目标人物名称
     * @return 匹配度大于阈值的表情包，若无匹配则返回空列表
     */
    public List<Document> recallByEmotion(String emotionText, String targetName) {
        SearchRequest request = SearchRequest.builder()
                .query(emotionText)
                .topK(1)
                // 相似度必须很高，避免把不相干的图硬拉过来凑数（宁缺毋滥的静态兜底）
                // 测试阶段从 0.70 降低到 0.50，以便更容易观察到命中效果
                .similarityThreshold(0.50)
                // 硬性安全隔离：强校验类型和专属者
                .filterExpression("target_name == '" + targetName + "' && type == 'sticker'")
                .build();

        return vectorStore.similaritySearch(request);
    }

    /**
     * 将包含富含语义描述的表情包内容入库（由 ChatLogParser 最终阶段调用）。
     */
    public void addDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        vectorStore.add(documents);
        vectorStore.save(vectorFile);
        System.out.println("--- [StickerVectorService] Added " + documents.size() + " stickers and saved to "
                + vectorFile.getName() + " ---");
    }
}
