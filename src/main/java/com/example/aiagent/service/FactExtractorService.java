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

/** 事实提取服务，使用 BeanOutputConverter 将聊天切片结构化解析为事实和人设特质 */
@Service
public class FactExtractorService {

    private final ChatClient chatClient;

    public FactExtractorService(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /** 批量处理场景列表，返回每个场景的提取结果 */
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
        int lineNum = 0;
        for (ParsedMessage msg : scene) {
            sb.append("[").append(lineNum++).append("] ")
                    .append(msg.getSenderName()).append(": ").append(msg.getContent()).append("\n");
        }

        BeanOutputConverter<ExtractionResult> converter = new BeanOutputConverter<>(ExtractionResult.class);
        String formatInstructions = converter.getFormat();

        String promptText = """
                你是一个顶级的心理侧写师。请分析以下聊天片段，对 "%1$s" 进行【长期情景事实】和【深层人设】的剥离提取。

                【严格的概念界定与提取边界】：
                1. 🔴 情景事实 (Facts) -> 长期召回价值的情景记忆
                   - 只提取有长期召回价值的具体事件和客观发生的事（Facts）。
                   - 例如：\"这周在准备期末考\"、\"修了一天的Bug\"。
                   - 严禁提取：瞬时的环境因素（如\"今天天气阴\"）、毫无意义的寒暄、过期的情绪（如\"现在很生气\"）。
                   - 注意：绝对不要试图在这个局部片段中猜测或总结宏观职业或统一身份，只能提取当前片段明确发生的独立事件。

                2. 🔵 背景人设 (PersonaTraits) -> 心理底色与相对关系
                   - 提取：深层的性格底色、核心价值观、行为动机、以及对聊天另一方的【关系定位】（如：极度依赖、防备）。
                   - 例如：\"表面逞强但内心极度缺乏安全感\"、\"极度讨厌迟到的人\"。
                   - 要求：必须是深刻的总结，而不是简单的词语。

                3. ❌ 绝对禁止提取的垃圾信息 (负面清单)
                   - 严禁提取口头禅或语气词。
                   - 严禁提取废话与寒暄。

                4. 📜 对证物溯源 (sourceLineNumbers) 的纯数学规则：
                   - 请不要再试图摘录任何台词文字或名字！
                   - 聊天片段每句话前面都已经被我打上了 [数字] 序号。
                   - 你的任务仅仅是找出支撑该事实结论的上下文对应句，然后把对应的 [数字] 填入 sourceLineNumbers 数组（例如填写 [0, 1] 即可）。

                %2$s

                聊天片段：
                %3$s
                """
                .formatted(targetName, formatInstructions, sb.toString());

        try {
            String responseText = chatClient.prompt().user(promptText).call().content();
            ExtractionResult result = converter.convert(responseText);

            // 用行号溯源，回填每条事实的精确原文引用
            if (result != null && result.getFacts() != null) {
                for (FactItem fact : result.getFacts()) {
                    if (fact.getSourceLineNumbers() != null) {
                        StringBuilder exactQuote = new StringBuilder();
                        for (int idx : fact.getSourceLineNumbers()) {
                            if (idx >= 0 && idx < scene.size()) {
                                ParsedMessage pm = scene.get(idx);
                                exactQuote.append(pm.getSenderName()).append(": ").append(pm.getContent()).append("\n");
                            }
                        }
                        fact.setExactSourceQuote(exactQuote.toString().trim());
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
