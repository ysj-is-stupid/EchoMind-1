package com.example.aiagent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * AI 女友的回复内容
     */
    private String reply;

    /**
     * 会话 ID（原样返回，方便前端追踪）
     */
    private String sessionId;
}
