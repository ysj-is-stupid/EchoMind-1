package com.example.aiagent.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import java.util.List;

@Data
public class FactItem {
    /** 事实内容 */
    @JsonPropertyDescription("提取出具有长期召回价值的具体事件和客观事实。")
    private String content;

    /** 支撑该事实的聊天行号列表 */
    @JsonPropertyDescription("支撑该事实的对应聊天片段中的「行号」数组（填入数字序号，例如 [0, 1]）。这是为了在底层查出对应人的发言。")
    private List<Integer> sourceLineNumbers;

    /** 由后端根据行号回填的精确原文引用，不参与模型输入 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String exactSourceQuote;

    /** 置信度 (0.0-1.0) */
    @JsonPropertyDescription("对该事实判断的置信度，0.0到1.0之间。")
    private double confidence;
}
