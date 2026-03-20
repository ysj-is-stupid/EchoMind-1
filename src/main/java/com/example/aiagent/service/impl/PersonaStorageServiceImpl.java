package com.example.aiagent.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.example.aiagent.model.PersonaConfig;
import com.example.aiagent.service.PersonaStorageService;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 人设持久化服务实现类 - 基于本地文件 (persona.json)
 */
@Service
public class PersonaStorageServiceImpl implements PersonaStorageService {

    private static final String PERSONA_FILE_PATH = "persona.json";

    @Override
    public void save(PersonaConfig persona) {
        if (persona == null)
            return;
        String json = JSONUtil.toJsonPrettyStr(persona);
        FileUtil.writeString(json, new File(PERSONA_FILE_PATH), StandardCharsets.UTF_8);
    }

    @Override
    public PersonaConfig load() {
        File file = new File(PERSONA_FILE_PATH);
        if (!file.exists()) {
            return null;
        }
        String json = FileUtil.readString(file, StandardCharsets.UTF_8);
        return JSONUtil.toBean(json, PersonaConfig.class);
    }
}
