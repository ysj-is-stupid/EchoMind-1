package com.example.aiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aiagent.model.domain.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
* @author 10692
* @description 针对表【chat_message】的数据库操作Mapper
* @createDate 2026-03-12 13:17:36
* @Entity generator.domain.ChatMessage
*/
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

}




