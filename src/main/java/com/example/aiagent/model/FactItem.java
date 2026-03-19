package com.example.aiagent.model;

import lombok.Data;

@Data
public class FactItem {
    /** 原子化事实内容 (e.g. "可怜的汤姆在辽宁省瓦房店市") */
    private String content;

    /** 原始对话摘录 (作为证据证据) */
    private String sourceQuote;

    /** 类别 (LOCATION, PREFERENCE, EVENT, STATE, PERSPECTIVE) */
    private String category;

    /** 置信度 (0.0-1.0) */
    private double confidence;

    /** 时效性 (STABLE-永久固定, EPHEMERAL-短期状态, PERIODIC-周期性) */
    private String scope;

    /** 记录时间 */
    private String time;
}
