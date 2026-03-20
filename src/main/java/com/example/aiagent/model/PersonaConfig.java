package com.example.aiagent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonaConfig {
    private String name;
    private String identity; // 核心身份定位
    private String lifeContext; // 长期生活状态/特质
    private String stableEmotionalStyle; // 稳定情感风格
    private String socialTendencies; // 长期社交倾向
    private String macroStyle; // 宏观行文风格
}
