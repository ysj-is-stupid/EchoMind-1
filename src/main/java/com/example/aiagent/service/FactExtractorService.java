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

        // 【核心灵魂：三维分离 Prompt】
        String promptText = """
                你是一个顶级的心理侧写师和人格建模专家。请分析以下聊天片段，对目标人物 "%1$s" 进行【客观事实】和【背景人设】的深度剥离提取。

                【严格的概念界定与提取边界】：
                1. 🔴 事实 (Facts) -> 物理层与客观层
                   - 提取：职业、地点、固定资产、发生的具体事件、客观状态。
                   - 例子："我今天入职了字节"、"我买了一只猫"。

                2. 🔵 背景人设 (PersonaTraits) -> 心理层、性格层与关系层
                   - 提取：深层的性格底色、价值观、行为动机、面对挫折的应对模式、以及对聊天另一方的【关系定位】（如：依赖、防备、倾囊相授）。
                   - 例子："表面逞强但内心渴望被认可"、"极度讨厌迟到的人"、"将对方视为情绪的唯一宣泄口"。
                   - 要求：必须是深刻的总结，而不是简单的词语。

                3. ❌ 绝对禁止提取的垃圾信息 (负面清单)
                   - 严禁提取口头禅或语气词（如："喜欢说'哈哈'"、"经常用'喵'结尾"）。
                   - 严禁将客观事实混入人设（如不能把"是个程序员"放入人设，这是事实）。
                   - 严禁提取瞬时的情绪（如"现在很生气"不是人设，"容易暴怒"才是人设）。

                // TODO: [EXTRACTION] 优化 Prompt，将口头禅(Catchphrases)从负面清单移至专门的提取维度

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
