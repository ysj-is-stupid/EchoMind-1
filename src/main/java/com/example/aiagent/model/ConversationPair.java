package com.example.aiagent.model;

import lombok.Data;
import java.util.List;

/** 带上下文的对话场景（context + question + answer） */
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

    /** 拼接 context + question 用于向量化 Embedding */
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
