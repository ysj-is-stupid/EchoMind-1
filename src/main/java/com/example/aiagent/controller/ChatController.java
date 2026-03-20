package com.example.aiagent.controller;

import com.example.aiagent.model.ChatRequest;
import com.example.aiagent.model.ChatResponse;
import com.example.aiagent.service.ChatService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/** 聊天接口，接收前端请求并调用 ChatService 返回回复 */
@RestController
public class ChatController {

    @Resource
    private ChatService chatService;

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String reply = chatService.chat(request.getMessage(), request.getSessionId());
        return ChatResponse.builder()
                .reply(reply)
                .sessionId(request.getSessionId())
                .build();
    }
}
