package com.example.aiagent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aiagent.service.ChatMessageService;
import com.example.aiagent.model.domain.ChatMessage;
import com.example.aiagent.mapper.ChatMessageMapper;
import org.springframework.stereotype.Service;

/** chat_message 表的 Service 实现 */
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage>
        implements ChatMessageService {

}
