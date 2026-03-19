package com.example.aiagent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Spring AI 框架调用AI
 */
// @Component // 已注释：不再在启动时自动调用 AI，改用 ChatController 提供 API
public class SpringAiAiInvoke implements CommandLineRunner {

    @Resource
    private ChatModel dashscopeChatModel;

    @Override
    public void run(String... args) throws Exception {
        AssistantMessage ass = dashscopeChatModel.call(new Prompt("hello world"))
                .getResult()
                .getOutput();
        System.out.println(ass.getText());

    }
}
