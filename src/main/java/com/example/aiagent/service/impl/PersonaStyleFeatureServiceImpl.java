package com.example.aiagent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aiagent.model.domain.PersonaStyleFeature;
import com.example.aiagent.service.PersonaStyleFeatureService;
import com.example.aiagent.mapper.PersonaStyleFeatureMapper;
import org.springframework.stereotype.Service;

/** persona_style_feature 表的 Service 实现 */
@Service
public class PersonaStyleFeatureServiceImpl extends ServiceImpl<PersonaStyleFeatureMapper, PersonaStyleFeature>
        implements PersonaStyleFeatureService {

}
