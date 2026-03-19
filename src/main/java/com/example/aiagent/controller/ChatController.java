package com.example.aiagent.controller;

import com.example.aiagent.model.ChatRequest;
import com.example.aiagent.model.ChatResponse;
import com.example.aiagent.service.ChatService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 聊天控制器 - 提供 AI 女友聊天的 REST API
 *
 * ========== 这个类做了什么？ ==========
 *
 * 它是一个标准的 Spring MVC 控制器，接收前端发来的 HTTP 请求，
 * 调用 ChatService 和 AI 对话，然后把结果返回给前端。
 *
 * 请求流程：
 * 前端 → POST /api/chat → ChatController → ChatService → AI 模型 → 返回回复
 *
 * =========================================
 */
@RestController // 标记为 REST 控制器，返回值自动转 JSON
public class ChatController {

    @Resource // 自动注入 ChatService（Spring 依赖注入）
    private ChatService chatService;

    /**
     * 聊天接口 - 发消息给 AI 女友
     *
     * 请求示例（用 Postman 或浏览器插件测试）：
     * POST http://localhost:8123/api/chat
     * Content-Type: application/json
     * Body:
     * {
     * "message": "亲爱的，你在干嘛呀",
     * "sessionId": "user001"
     * }
     *
     * 响应示例：
     * {
     * "reply": "在想你呀～你今天工作忙不忙呢？😊",
     * "sessionId": "user001"
     * }
     *
     * @param request 包含 message（消息）和 sessionId（会话ID）
     * @return AI 女友的回复
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        // 调用 ChatService 获取 AI 的回复
        String reply = chatService.chat(request.getMessage(), request.getSessionId());

        // 封装成 ChatResponse 返回
        return ChatResponse.builder()
                .reply(reply)
                .sessionId(request.getSessionId())
                .build();
    }
}
