package com.example.aiagent.model;

import lombok.Data;

/**
 * 解析后的单条对话（已合并连续消息）
 */
@Data
public class ParsedMessage {

    /** 发送者名称 */
    private String senderName;

    /** 发送者 uid */
    private String senderUid;

    /** 合并后的消息内容 */
    private String content;

    /** 第一条消息的时间戳（毫秒） */
    private long timestamp;

    /** 格式化的时间字符串 */
    private String time;
}
