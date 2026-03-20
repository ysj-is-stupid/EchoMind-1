package com.example.aiagent.mapper;

import com.example.aiagent.model.domain.PersonaStyleFeature;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * @author 10692
 * @description 针对表【persona_style_feature(AI人设风格特征表)】的数据库操作Mapper
 * @createDate 2026-03-19 19:12:29
 * @Entity com.example.aiagent.model.domain.PersonaStyleFeature
 */
@Mapper
public interface PersonaStyleFeatureMapper extends BaseMapper<PersonaStyleFeature> {

}
