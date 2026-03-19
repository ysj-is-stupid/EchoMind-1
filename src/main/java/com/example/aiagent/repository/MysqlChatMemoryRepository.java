package com.example.aiagent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.aiagent.mapper.ChatMessageMapper;
import com.example.aiagent.model.domain.ChatMessage;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MysqlChatMemoryRepository implements ChatMemoryRepository {

    private final ChatMessageMapper mapper;

    public MysqlChatMemoryRepository(ChatMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public @NonNull List<Message> findByConversationId(@NonNull String conversationId) {
        QueryWrapper<ChatMessage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id", conversationId)
                .orderByAsc("created_at");

        return mapper.selectList(queryWrapper).stream().map(this::toMessage).toList();
    }

    @Override
    public List<String> findConversationIds() {
        return List.of();
    }

    @Override
    @Transactional
    public void saveAll(String conversationId, List<Message> messages) {
        QueryWrapper<ChatMessage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id", conversationId);
        mapper.delete(queryWrapper);
        
        for (Message message : messages) {
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setConversationId(conversationId);
            chatMessage.setMessageType(message.getMessageType().name());
            chatMessage.setContent(message.getText());
            chatMessage.setCreatedAt(LocalDateTime.now());
            mapper.insert(chatMessage);
        }
    }

    @Override
    @Transactional
    public void deleteByConversationId(String conversationId) {
        QueryWrapper<ChatMessage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id", conversationId);
        mapper.delete(queryWrapper);
    }


    private Message toMessage(ChatMessage entity) {
        return switch (entity.getMessageType()) {
            case "USER" -> new UserMessage(entity.getContent());
            case "ASSISTANT" -> new AssistantMessage(entity.getContent());
            case "SYSTEM" -> new SystemMessage(entity.getContent());
            default -> new UserMessage(entity.getContent());
        };
    }

}
