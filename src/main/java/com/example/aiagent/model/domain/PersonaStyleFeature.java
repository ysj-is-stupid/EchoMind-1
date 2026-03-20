package com.example.aiagent.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * AI人设风格特征表
 * @TableName persona_style_feature
 */
@TableName(value ="persona_style_feature")
@Data
public class PersonaStyleFeature implements Serializable {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID或用户标识，用于隔离不同人的聊天风格
     */
    private String sessionId;

    /**
     * 特征类型: CATCHPHRASE(口头禅), KEYWORD(高频词), TONE(语气助词)
     */
    private String featureType;

    /**
     * 具体的词汇或风格描述
     */
    private String content;

    /**
     * 出现次数，用于衡量该特征的显著程度
     */
    private Integer usageCount;

    /**
     * 是否启用
     */
    private Integer isActive;

    /**
     * 最后一次从聊天记录中提取的时间
     */
    private Date lastExtractedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        PersonaStyleFeature other = (PersonaStyleFeature) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getSessionId() == null ? other.getSessionId() == null : this.getSessionId().equals(other.getSessionId()))
            && (this.getFeatureType() == null ? other.getFeatureType() == null : this.getFeatureType().equals(other.getFeatureType()))
            && (this.getContent() == null ? other.getContent() == null : this.getContent().equals(other.getContent()))
            && (this.getUsageCount() == null ? other.getUsageCount() == null : this.getUsageCount().equals(other.getUsageCount()))
            && (this.getIsActive() == null ? other.getIsActive() == null : this.getIsActive().equals(other.getIsActive()))
            && (this.getLastExtractedAt() == null ? other.getLastExtractedAt() == null : this.getLastExtractedAt().equals(other.getLastExtractedAt()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getSessionId() == null) ? 0 : getSessionId().hashCode());
        result = prime * result + ((getFeatureType() == null) ? 0 : getFeatureType().hashCode());
        result = prime * result + ((getContent() == null) ? 0 : getContent().hashCode());
        result = prime * result + ((getUsageCount() == null) ? 0 : getUsageCount().hashCode());
        result = prime * result + ((getIsActive() == null) ? 0 : getIsActive().hashCode());
        result = prime * result + ((getLastExtractedAt() == null) ? 0 : getLastExtractedAt().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", sessionId=").append(sessionId);
        sb.append(", featureType=").append(featureType);
        sb.append(", content=").append(content);
        sb.append(", usageCount=").append(usageCount);
        sb.append(", isActive=").append(isActive);
        sb.append(", lastExtractedAt=").append(lastExtractedAt);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}