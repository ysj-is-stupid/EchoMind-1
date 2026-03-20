package com.example.aiagent.service;

import com.example.aiagent.model.PersonaConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

/** 从历史聊天记录中提炼人设骨架 */
@Service
public class PersonaEvolutionService {
    private final ChatClient chatClient;

    public PersonaEvolutionService(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    public PersonaConfig generateBaseSkeleton(String sampledChatLog, String targetName) {
        BeanOutputConverter<PersonaConfig> converter = new BeanOutputConverter<>(PersonaConfig.class);
        String format = converter.getFormat();

        String prompt = """
                你是一个顶尖的心理侧写师。
                以下是目标人物 "%1$s" 的历史聊天记录切片。我已经为你做了一次均匀的时间跨度和话题域抽样，你看到的几十段对话横跨了极长的一段时间。

                【核心任务】：审视这些记录，穿透表象的流水账（比如今天修了什么Bug），为他提炼出一份最为深层、稳固且立体的【原生人设骨架】。

                【必须提取的维度】：
                1. lifeContext：长期稳定的生活约束或语境（如：经费紧张、常年熬夜、靠云服务器续命等。**警告：严禁提取短期发生的阶段性项目**）。
                2. stableEmotionalStyle：稳定的情绪底色（如：抗压能力强但容易焦虑。**警告：严禁提取诸如\"今天很生气\"的短期临时情绪**）。
                3. socialTendencies：他的人际社交倾向（必须是你在多个不同切片中印证出的特征，如：嘴硬心软、好为人师）。
                4. macroStyle：结构化的宏观行文风格。大段形容词是无效的，你必须严谨地按以下4个环节进行分点描述：
                   - [节奏与结构]：断句习惯（是残暴的短句连击，还是偶尔长篇大论？）。
                   - [幽默与讽刺]：防御性语言机制（如在面对报错挫折或被提问时，是否会忍不住自嘲或阴阳怪气自己）。
                   - [互动态度]：基于1v1聊天的语境，分析他的主导性与压迫感（如：总是脾气暴躁喜欢反驳、还是往往处于弱势并寻求肯定？）。
                   - [直率度]：废话浓缩度（是否极度厌恶开场白铺垫，直奔主题？）。
                   （警告：极其微观的具体口头禅已由外部负责，此处绝对禁止列举具体的口癖单词，只需总结你的文风大逻辑即可！）。

                警告：你必须严格遵循以上字段的隔离规则，不要混淆！
                %2$s

                ===========
                均匀采样的跨期聊天记录切片：
                %3$s
                """.formatted(targetName, format, sampledChatLog);

        try {
            String response = chatClient.prompt().user(prompt).call().content();
            PersonaConfig config = converter.convert(response);
            if (config != null) {
                config.setName(targetName);
            }
            return config;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
