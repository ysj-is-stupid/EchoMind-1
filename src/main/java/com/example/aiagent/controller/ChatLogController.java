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

        // 3. 同步入库
        for (int i = 0; i < scenes.size(); i++) {
            if (i >= extractionResults.size())
                break;

            com.example.aiagent.model.ExtractionResult res = extractionResults.get(i);

            // 存入事实库
            if (res.getFacts() != null && !res.getFacts().isEmpty()) {
                factStored += factVectorService.store(res.getFacts());
            }
        }

        return Map.of(
                "message", "深度同步入库（事实同步）完成",
                "总场景数", scenes.size(),
                "事实条数", factStored,
                "提示", "同步已完成，AI 将基于新的事实库进行回复");
    }
}
