package com.example.aiagent.model;

import lombok.Data;
import java.util.List;

/**
 * 带上下文的对话场景
 *
 * 不再是单纯的 1问1答，而是带有前几条历史消息作为上下文：
 * {
 * "context": ["我: 听说火葬场工资挺高的", "兄弟: 确实"],
 * "question": "兄弟 我要去唐山了兄弟",
 * "answer": "bro来烧我了？"
 * }
 */
@Data
public class ConversationPair {

    /** 上下文：前面几条历史消息（格式："发送者: 内容"） */
    private List<String> context;

    /** 对方说的话（触发回复的那条） */
    private String question;

    /** 目标人物的回复 */
    private String answer;

    /** 回复的时间 */
    private String time;

    /**
     * 拼接成完整文本（用于向量化 Embedding）
     * 把 context + question 拼在一起，让搜索时能匹配到完整语境
     */
    public String toEmbeddingText() {
        StringBuilder sb = new StringBuilder();
        if (context != null) {
            for (String line : context) {
                sb.append(line).append("\n");
            }
        }
        sb.append(question);
        return sb.toString();
    }

}
