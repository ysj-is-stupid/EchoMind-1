package com.example.aiagent.controller;

import com.example.aiagent.model.ConversationPair;
import com.example.aiagent.model.ParsedMessage;
import com.example.aiagent.model.PersonaConfig;
import com.example.aiagent.service.*;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** 聊天记录导入控制器，提供向量化同步和人设骨架提取接口 */
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

    @Resource
    private PersonaEvolutionService personaEvolutionService;

    /** 上传聊天记录，提取事实并存入向量库 */
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
        // 用 Set 对人设特质去重
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

            // 收集人设特质
            if (res.getPersonaTraits() != null) {
                for (String trait : res.getPersonaTraits()) {
                    if (trait != null && !trait.isBlank()) {
                        uniquePersonaTraits.add(trait.trim());
                    }
                }
            }
        }

        // TODO: 将提取的口头禅同步写入 persona_style_feature 表

        return Map.of(
                "message", "深度同步入库（仅事实）完成",
                "总场景数", scenes.size(),
                "事实条数", factStored,
                "提示", "同步已完成，建议随后调用 /extract-skeleton 提取长期人设骨架");
    }

    /** 全局采样聊天记录并提炼人设骨架，可手动传入 identity 覆盖模型猜测 */
    @PostMapping("/extract-skeleton")
    public Map<String, Object> extractSkeleton(
            @RequestParam("file") MultipartFile file,
            @RequestParam("targetName") String targetName,
            @RequestParam(value = "identity", required = false) String identity) throws Exception {
        String jsonStr = new String(file.getBytes(), StandardCharsets.UTF_8);

        // 1. 解析为场景列表
        List<List<ParsedMessage>> scenes = chatLogParser.parseToScenes(jsonStr);
        if (scenes == null || scenes.isEmpty()) {
            return Map.of("error", "未能解析出任何聊天场景");
        }

        // 2. 均匀采样，最多取 50 个场景
        int targetSampleCount = 50;
        int step = Math.max(1, scenes.size() / targetSampleCount);

        StringBuilder sampledLogBuilder = new StringBuilder();
        int sampledCount = 0;
        for (int i = 0; i < scenes.size(); i += step) {
            if (sampledCount >= targetSampleCount)
                break;

            List<ParsedMessage> scene = scenes.get(i);
            sampledLogBuilder.append("--- [切片 ").append(sampledCount + 1).append("] ---\n");
            for (ParsedMessage pm : scene) {
                sampledLogBuilder.append(pm.getSenderName()).append(": ").append(pm.getContent()).append("\n");
            }
            sampledLogBuilder.append("\n");
            sampledCount++;
        }

        // 3. 提炼人设骨架
        PersonaConfig skeleton = personaEvolutionService.generateBaseSkeleton(sampledLogBuilder.toString(), targetName);

        // 4. 持久化存储，如有手动 identity 则覆盖模型猜测
        if (skeleton != null) {
            if (identity != null && !identity.isBlank()) {
                skeleton.setIdentity(identity.trim());
            }
            personaStorageService.save(skeleton);
            return Map.of("message", "提取成功", "skeleton", skeleton);
        }

        return Map.of("error", "提取失败或模型返回为空");
    }
}
