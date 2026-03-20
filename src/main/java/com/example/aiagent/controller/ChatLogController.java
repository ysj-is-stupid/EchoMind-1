package com.example.aiagent.controller;

import com.example.aiagent.model.ConversationPair;
import com.example.aiagent.model.ParsedMessage;
import com.example.aiagent.service.*;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 聊天记录导入控制器
 *
 * 仅保留：
 * /vectorize/sync → 把对话对存入向量库，启用 RAG 语境回忆
 */
@RestController
@RequestMapping("/chatlog")
public class ChatLogController {

    @Resource
    private ChatLogParser chatLogParser;

    @Resource
    private FactExtractorService factExtractor;

    @Resource
    private FactVectorService factVectorService;

    @Resource
    private PersonaStorageService personaStorageService;

    // 接口: 深度同步入库 (Deep Sync v3.0 - AI 驱动的事实提取与入库)
    @PostMapping("/vectorize/sync")
    public Map<String, Object> vectorizeSync(
            @RequestParam("file") MultipartFile file,
            @RequestParam("targetName") String targetName) throws Exception {
        String jsonStr = new String(file.getBytes(), StandardCharsets.UTF_8);

        // 1. 切片
        List<List<ParsedMessage>> scenes = chatLogParser.parseToScenes(jsonStr);

        // 2. 事实提取
        List<com.example.aiagent.model.ExtractionResult> extractionResults = factExtractor
                .extractResultsFromScenes(scenes, targetName);

        int factStored = 0;
        // 使用 Set 自动对大模型提取出的特征短句进行去重
        Set<String> uniquePersonaTraits = new LinkedHashSet<>();

        // 3. 同步入库
        for (int i = 0; i < scenes.size(); i++) {
            if (i >= extractionResults.size())
                break;

            com.example.aiagent.model.ExtractionResult res = extractionResults.get(i);
            if (res == null)
                continue;

            // 存入事实库
            if (res.getFacts() != null && !res.getFacts().isEmpty()) {
                factStored += factVectorService.store(res.getFacts());
            }

            // 收集人设特质并自动去重
            if (res.getPersonaTraits() != null) {
                for (String trait : res.getPersonaTraits()) {
                    if (trait != null && !trait.isBlank()) {
                        uniquePersonaTraits.add(trait.trim());
                    }
                }
            }
        }

        // TODO: [PERSISTENCE] 统计并将提取出的口头禅同步入库到 persona_style_feature 表 (使用 MyBatis-Plus
        // 的 Upsert 逻辑)

        // 3. 将去重后的人设特质拼接成一篇极其丰满的“人物设定文档”
        StringBuilder backgroundBuilder = new StringBuilder();
        for (String trait : uniquePersonaTraits) {
            backgroundBuilder.append("- ").append(trait).append("\n");
        }

        // 4. 构建最终人设并保存
        com.example.aiagent.model.PersonaConfig finalPersona = new com.example.aiagent.model.PersonaConfig();
        finalPersona.setName(targetName);
        finalPersona.setBackground(backgroundBuilder.toString().trim());

        personaStorageService.save(finalPersona);

        return Map.of(
                "message", "深度同步入库（事实与人设同步）完成",
                "总场景数", scenes.size(),
                "事实条数", factStored,
                "人设特征数", uniquePersonaTraits.size(),
                "提示", "同步已完成，AI 人设已根据聊天记录完成进化");
    }
}
