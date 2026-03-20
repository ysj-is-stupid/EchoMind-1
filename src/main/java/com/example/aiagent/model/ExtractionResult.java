package com.example.aiagent.model;

import lombok.Data;
import java.util.List;

/** 单个场景的事实提取结果，包含事实列表和人设特质 */
@Data
public class ExtractionResult {
    /** 客观事实列表 */
    private List<FactItem> facts;

    /** 深层性格特质列表，用于更新人设库 */
    private List<String> personaTraits;

    // TODO: 增加 catchphrases 字段支持口头禅结构化输出
}
