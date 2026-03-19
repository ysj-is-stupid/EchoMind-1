package com.example.aiagent.model;

import lombok.Data;

/**
 * 聊天请求体
 */
@Data
public class ChatRequest {

    /**
     * 用户发送的消息内容
     */
    private String message;

    /**
     * 会话 ID，用于区分不同用户/会话的聊天记忆
     * 同一个 sessionId 下的对话会共享上下文记忆
     * 如果不传，则使用默认值 "default"
     */
    private String sessionId = "default";
}
