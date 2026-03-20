package com.example.aiagent.service;

import com.example.aiagent.model.PersonaConfig;

/**
 * 人设持久化服务
 */
public interface PersonaStorageService {
    /**
     * 保存人设配置
     */
    void save(PersonaConfig persona);

    /**
     * 加载当前人设配置
     */
    PersonaConfig load();
}
