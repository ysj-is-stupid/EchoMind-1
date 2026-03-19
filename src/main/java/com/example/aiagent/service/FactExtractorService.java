package com.example.aiagent.service;

import com.example.aiagent.model.ExtractionResult;
import com.example.aiagent.model.FactItem;
import com.example.aiagent.model.ParsedMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 事实提取服务 3.1 - 使用 Spring AI BeanOutputConverter 增强鲁棒性
 */
@Service
public class FactExtractorService {

    private final ChatClient chatClient;

    public FactExtractorService(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /**
     * 事实提取 3.0：从会话切片中提取客观事实
     */
    public List<ExtractionResult> extractResultsFromScenes(List<List<ParsedMessage>> scenes, String targetName) {
        List<ExtractionResult> results = new ArrayList<>();
        if (scenes == null || scenes.isEmpty())
            return results;

        for (List<ParsedMessage> scene : scenes) {
            ExtractionResult res = extractFromSingleScene(scene, targetName);
            if (res != null) {
                results.add(res);
            }
        }
        return results;
    }

    private ExtractionResult extractFromSingleScene(List<ParsedMessage> scene, String targetName) {
        StringBuilder sb = new StringBuilder();
        for (ParsedMessage msg : scene) {
            sb.append(msg.getSenderName()).append(": ").append(msg.getContent()).append("\n");
        }

        // 1. 初始化 Spring AI 的 Bean 转换器
        BeanOutputConverter<ExtractionResult> converter = new BeanOutputConverter<>(ExtractionResult.class);

        // 2. 获取转换器自动生成的 JSON 格式指令 (Format Instructions)
        String formatInstructions = converter.getFormat();

        // 3. 重写 Prompt，专注于事实提取
        String promptText = """
                你是一个高级的人格建模分析师。请对以下聊天记录片段进行【客观事实】提取。

                目标对象："%1$s"

                规则：
                1. 剥离所有情绪、修饰和语气。
                2. 只保留关于"%1$s"本人的物理状态、工作、事件、固定资产、明确意图。
                3. 严禁提取他人的信息。
                4. 必须保留 evidence (原文摘录)。

                %2$s

                聊天片段：
                %3$s
                """.formatted(targetName, formatInstructions, sb.toString());

        try {
            // 4. 调用大模型
            String responseText = chatClient.prompt().user(promptText).call().content();

            // 5. 让转换器接管解析，彻底告别 substring 和手动 JSON 映射
            ExtractionResult result = converter.convert(responseText);

            // 补充时间戳信息
            if (result != null && result.getFacts() != null && !scene.isEmpty()) {
                String sceneTime = scene.get(scene.size() - 1).getTime();
                for (FactItem item : result.getFacts()) {
                    if (item.getTime() == null) {
                        item.setTime(sceneTime);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            System.err.println("提取基础事实数据失败: " + e.getMessage());
            return null;
        }
    }
}
