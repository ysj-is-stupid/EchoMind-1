# EchoMind

基于 Spring AI 的角色扮演对话系统。通过导入目标人物的真实聊天记录，提取其事实记忆和性格骨架，让模型在对话中持续扮演该人物。

---

## 功能概览

| 模块 | 说明 |
|------|------|
| 多轮对话 | 对话历史持久化到 MySQL，支持多 Session 隔离 |
| **双流 RAG 引擎** | **① 事实库 (事前)**: 原汁原味实体提取 + 并发多路召回去重； **② 表情库 (事后)**: Regex 内心戏拦截 + RAG 情绪算分 + 动态图片标识替换 |
| 意图与安全防线 | LLM 前置路由清洗冗长噪音，同时识别过滤越权与注入攻击 |
| 人设骨架 | 从大量聊天记录中提炼稳定的性格、语境、风格骨架，当前持久化为 `persona.json`（待迁移） |
| 工具调用 | 内置天气工具；由于 Spring AI 拦截缺陷，探索出了纯靠 Prompt + RegExp 的后置调用解法 |

---

## 架构

```text
用户输入
  │
  ▼
IntentClassifierService       # 意图前置清洗与隔离
  │
  ├─ 命中注入 → 构造拒答指令
  └─ 需要检索 → 原汁原味特征词提取，裂变 1~3 个搜索短语 (Extractive Query Expansion)
                                │
                                ▼
                         [多线程并发召回层] parallelStream
                                │
                                ▼
                         FactVectorService             # 取出多路历史记忆拼装备用并严格去重
                                │
                                ▼
                        SystemPromptTemplate           # 组合人设矩阵 + <dynamic_memory>
                                │
                                ▼
                           ChatClient                  # 召唤主模型开展深度角色扮演推理
                          (MessageChatMemoryAdvisor)   
                                │
                                ▼
                    Regex Sticker Interceptor          # 核心特色：后置正则表达式拦截内心戏 <sticker: xxx>
                                │
                                ├── 分数达标(>0.5) ---> 替换为专属图片ID [emj_xxx]   # (StickerVectorService RAG)
                                └── 未过阈值(太主观) -> 触发静默安全销毁
                                │
                                ▼
                           返回最终回复前端
```

**聊天记录处理流程：**
```
上传 JSON 文件
  │
  ├─ /chatlog/vectorize/sync
  │    ChatLogParser → 切片 → FactExtractorService → 事实+人设特质
  │    └─ FactVectorService.store()  # 写入 vector_facts.json
  │
  └─ /chatlog/extract-skeleton
       均匀采样 50 片 → PersonaEvolutionService → PersonaConfig
       └─ PersonaStorageService.save()  # 写入 persona.json（待迁移至 MySQL persona_config 表）
```

---

## 技术栈

- **Spring Boot 3 + Spring AI**
- **大模型**：通义千问（`qwen-turbo`，通过 DashScope 接入）
- **向量存储**：Spring AI `SimpleVectorStore`（本地文件 `vector_facts.json`）
- **多轮记忆**：MySQL（`chat_message` 表），通过 `MessageWindowChatMemory` 管理最近 20 条
- **MCP**：stdio 方式接入 Node.js Steam MCP 服务
- **ORM**：MyBatis-Plus
- **API 文档**：Knife4j（Swagger UI：`/api/swagger-ui.html`）

---

## 数据库表

| 表名 | 用途 |
|------|------|
| `chat_message` | 多轮对话历史 |
| `persona_style_feature` | 人物口头禅/高频短语（当前无写入逻辑，见 TODO） |

---

## 接口

所有接口均挂在 `/api` 路径下。

### 对话

```
POST /api/chat
Body: { "message": "你好", "sessionId": "user001" }

Response: { "reply": "...", "sessionId": "user001" }
```

### 聊天记录导入

```
POST /api/chatlog/vectorize/sync
  Params: file（QQ 导出 JSON）, targetName（目标人物名）
  作用：提取事实和人设特质，存入向量库

POST /api/chatlog/extract-skeleton
  Params: file, targetName, identity（可选，手动指定身份）
  作用：提炼人设骨架并保存为 persona.json
```

---

## 快速启动

**前置条件：**
1. JDK 17+
2. MySQL 数据库 `ai_girlfriend`，建好 `chat_message` 和 `persona_style_feature` 表
3. Node.js（用于 Steam MCP 服务）

**配置 `application.yaml`：**
```yaml
spring:
  ai:
    dashscope:
      api-key: "你的 DashScope API Key"
  datasource:
    url: jdbc:mysql://localhost:3306/ai_girlfriend?...
    username: root
    password: 你的密码
```
MCP 配置中的 Steam js 文件路径改为你本地的绝对路径。

**启动：**
```bash
mvn spring-boot:run
```

---

## 聊天记录 JSON 格式

`vectorize/sync` 接口接受 QQ 导出的结构化 JSON，格式如下：
```json
{
  "messages": [
    {
      "timestamp": 1700000000000,
      "time": "2023-11-15 10:00:00",
      "recalled": false,
      "system": false,
      "sender": { "uid": "123456", "name": "张三" },
      "content": { "text": "你好啊" }
    }
  ]
}
```

---

## TODO

### 个性化表达（口头禅 & 私有表情包）自动化提取链路 (未实现)
当前 `persona_style_feature` 表和 `vector_persona_stickers.json` 还缺乏一套从海量原始聊天记录中**自动洗数据入库**的脚本链路，属于硬编码或缺位状态。

- [ ] **提取层抽象**：在 `ExtractionResult` 模型中新增 `catchphrases: List<String>` 和 `stickers: List<StickerInfo>` 字段。
- [ ] **Prompt 维度扩充**：修改 `FactExtractorService` 的提取指令，让大模型在清洗 QQ 历史切片时：
  - 统计该片段的高频且独有的口头禅/口癖/Emoji。
  - 重点识别出原生格式里的 `[图片]` 或特定的通用 `[表情]`。**分析发送该表情前后的历史语境**，将其提炼转换为高度凝聚的客观“情绪描述短语”。
- [ ] **落盘写入控制 (`ChatLogController / Sync`)**：
  - 将提取出的 `catchphrases` 执行频率累加与 Upsert，写入 MySQL 的 `persona_style_feature` 表。
  - 将提炼出的 `stickers` 组装为带 `target_name` 和 `sticker_id` 元数据的 Document，直接调用 `StickerVectorService.addDocuments()`，灌入本地高维表现图库供主回答流调用。

### 事实记忆缺陷
- [x] `FactVectorService` metadata 中回填系统时间戳，`formatAsPrompt` 时显示"X 个月前"，防止时间错乱
- [x] 实现定时记忆压缩任务（Memory Compaction）：用模型扫描并合并相互矛盾的旧记忆

### 存储迁移
- [ ] `persona.json` 迁移至 MySQL `persona_config` 表（`target_name` 为主键），支持多人物共存、改写历史追踪，改动范围：`PersonaStorageService` 实现类 + 新增 Mapper
- [ ] `vector_facts.json`（`SimpleVectorStore`）迁移至专用向量数据库；当前数据量小可维持现状，数据量增大后建议迁移至 PostgreSQL + pgvector 或 Qdrant；仅需替换 Spring AI 的 `VectorStore` Bean

### 工程问题
- [ ] `application.yaml` 中 Steam MCP js 文件路径改为相对路径或配置注入，避免硬编码
- [ ] API Key 和数据库密码移出配置文件（环境变量或 Vault）
- [ ] `IntentClassifierService` 目前使用主模型打分，建议替换为更便宜的小模型（如 `qwen-turbo`、`glm-4-flash`）
- [ ] `findConversationIds()` 方法返回空列表，需实现
