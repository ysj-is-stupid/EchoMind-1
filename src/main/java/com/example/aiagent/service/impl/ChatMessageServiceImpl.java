package com.example.aiagent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aiagent.service.ChatMessageService;
import com.example.aiagent.model.domain.ChatMessage;
import com.example.aiagent.mapper.ChatMessageMapper;
import org.springframework.stereotype.Service;

/**
* @author 10692
* @description 针对表【chat_message】的数据库操作Service实现
* @createDate 2026-03-12 13:17:36
*/
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage>
    implements ChatMessageService {

}




