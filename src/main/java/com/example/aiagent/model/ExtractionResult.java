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

    /**
     * 核心背景设定与性格特质列表（进人设库）
     * 例如：["性格极度缺乏安全感", "对工作抱有完美主义", "将对方视为唯一可以倾诉软弱的人"]
     */
    private List<String> personaTraits;

    // TODO: [MODEL] 增加 catchphrases 字段以支持口头禅提取结果的结构化输出 (e.g., List<String>
    // catchphrases)
}
