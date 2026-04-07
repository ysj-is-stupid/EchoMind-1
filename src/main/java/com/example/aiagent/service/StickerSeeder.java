package com.example.aiagent.service;

import org.springframework.ai.document.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 临时测试数据种子器：
 * 用于在启动应用时，如果检测到没有表情包向量库，就自动给你塞入两条测试数据。
 * 这依赖于底层的 EmbeddingModel（去调取阿里的向量接口算出真实的多维浮点数组存下来）。
 */
@Component
public class StickerSeeder implements ApplicationRunner {

    private final StickerVectorService stickerVectorService;

    public StickerSeeder(StickerVectorService stickerVectorService) {
        this.stickerVectorService = stickerVectorService;
    }

    @Override
    public void run(ApplicationArguments args) {
        File file = new File("vector_persona_stickers.json");
        // 如果文件不存在或者大小为0，说明你还没灌入过测试数据
        if (!file.exists() || file.length() == 0) {
            System.out.println(">>> [StickerSeeder] 检测到未包含表情包本地库，正在调用 Embedding 接口生成测试测试数据...");

            // 测试表情一：无语叹气
            Document doc1 = new Document(
                    "极其无语、绝望、无奈、被气笑。常用于面对极其离谱的事情或者别人反复搞坏了代码时尴尬地叹气打圆场。",
                    Map.of(
                            "type", "sticker",
                            "target_name", "可怜的汤姆", // 与默认兜底人设一致，保证肯定能查出来
                            "sticker_id", "Cry_Laugh_99" // 最终预期被替换在字符串里的标签
                    ));

            // 测试表情二：得意骄傲
            Document doc2 = new Document(
                    "极其得意、骄傲、沾沾自喜。常用于自己做了一件很厉害的事情、吹牛或者无情嘲讽别人的时候。",
                    Map.of(
                            "type", "sticker",
                            "target_name", "可怜的汤姆",
                            "sticker_id", "Proud_Doge_01"));

            // 写入向量接口自动算分并落盘
            stickerVectorService.addDocuments(List.of(doc1, doc2));
            System.out.println(">>> [StickerSeeder] 表情测试数据写入完成！现在你可以去调接口测试嘲讽大模型了！");
        }
    }
}
