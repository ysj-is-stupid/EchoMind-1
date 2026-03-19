package com.example.aiagent.model;

import lombok.Data;
import java.util.List;

/**
 * 结构化事实提取结果
 */
@Data
public class ExtractionResult {
    /** 客观事实列表（干） */
    private List<FactItem> facts;
}
