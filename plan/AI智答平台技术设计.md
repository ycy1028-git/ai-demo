# AI 智答平台技术设计文档

## 一、系统概述

### 1.1 项目介绍

AI 智答平台是一个基于 Spring AI Alibaba（通义千问/DashScope）构建的智能问答平台，采用**混合模式架构**：
- **动态路由**：LLM 自动理解用户意图，自主选择最优节点组合
- **固定模板**：复杂业务场景（报销、请假等）使用预设固定流程
- 节点 Spring Bean 化管理，可复用、可扩展

### 1.2 核心功能

| 模块 | 功能 |
|------|------|
| **流程引擎** | 流程模板管理、节点编排、动态规划 |
| **知识库** | 文档管理、向量化检索、RAG 问答 |
| **AI 模型** | 多模型配置（DeepSeek/通义千问/智谱GLM） |
| **开放平台** | API 凭证鉴权、流量控制、外部调用 |
| **应用层** | 会话管理、消息记录、调用统计 |

---

## 二、技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.2.0 + JDK 17 |
| AI 框架 | Spring AI Alibaba（通义千问/DashScope） |
| 模型 | qwen-plus / deepseek-chat / glm-4 |
| 数据库 | MySQL 8.0 + JPA |
| 缓存 | Redis（对话上下文存储） |
| 向量检索 | Elasticsearch 8.x |
| 对象存储 | MinIO |
| 流程引擎 | 自建（轻量可控） |

---

## 三、项目结构

### 3.1 后端结构

```
ai-platform-backend/src/main/java/com/aip/
├── ai/                      # AI 模型配置
│   ├── controller/          # AiModelConfigController
│   ├── dto/                 # AiModelConfigDTO/VO
│   ├── entity/              # AiModelConfig
│   └── service/             # IAiModelConfigService, AiModelService
│
├── app/                     # 应用层（会话/消息）
│   ├── constant/            # Status, BusinessCode
│   ├── controller/          # ChatController, ChatStreamController, AppChatController
│   ├── dto/                 # ChatRequestDTO, ChatResponseDTO
│   ├── entity/              # ChatSession, ChatMessage, InvocationStat
│   ├── mapper/              # ChatSessionMapper, ChatMessageMapper
│   ├── service/             # IChatService, InvocationStatService
│   └── vo/                  # ChatSessionVO, ChatMessageVO
│
├── common/                  # 公共组件
│   ├── config/              # Redis/Jackson/Cors/OpenApi 配置
│   ├── exception/           # BusinessException, GlobalExceptionHandler, ErrorCode
│   └── exception/           # 异常处理
│
├── flow/                    # 流程引擎（核心）
│   ├── controller/          # ChatController, FlowTemplateController, NodeController
│   ├── dto/                 # IntentRouteResult, RegisteredNode, NodeExecutionPlan, NodeSchema
│   ├── engine/              # FlowContext, FlowDefinition, NodeResult
│   ├── entity/              # FlowTemplate, FlowTemplateNode
│   ├── executor/            # NodeExecutor, BaseNodeExecutor, LlmCallExecutor, KnowledgeRetrievalExecutor, ConditionExecutor, ParameterExtractorExecutor, RouterExecutor
│   ├── mapper/              # FlowTemplateMapper
│   └── service/             # IFlowEngine, IFlowTemplateService, INodeRegistryService, IIntentRouterService, IDynamicPlannerService, IContextManager
│
├── knowledge/               # 知识库模块
│   ├── config/              # ChunkingProperties, ElasticsearchIndexConfig, MinioConfig
│   ├── controller/          # 知识库/知识条目控制器
│   ├── dto/                 # KnowledgeSearchResultDTO, KnowledgeBaseDTO, KnowledgeItemDTO
│   ├── entity/              # KnowledgeBase, KnowledgeItem, Document
│   ├── mapper/              # KnowledgeBaseMapper, KnowledgeItemMapper, DocumentMapper
│   └── service/             # 文档/知识库/检索服务
│
├── open/                    # 开放平台
│   ├── controller/          # OpenApiController
│   ├── dto/                 # OpenChatRequest, OpenChatResponse, WidgetConfigResponse
│   └── service/             # OpenApiAuthService, RateLimitService, AuthResult
│
└── system/                  # 系统管理
    ├── controller/          # SysUserController, SysRoleController, SysApiCredentialController, AuthController
    ├── dto/                 # LoginDTO, RoleDTO, ApiCredentialDTO
    ├── entity/              # SysUser, SysRole, SysApiCredential, SysOperationLog
    ├── mapper/              # SysUserMapper, SysRoleMapper, SysApiCredentialMapper
    └── service/             # 用户/角色/凭证服务
```

### 3.2 前端结构

```
ai-platform-frontend/src/
├── api/                     # API 接口
│   ├── app.js              # 应用层 API
│   ├── credential.js        # 凭证管理 API
│   ├── dashboard.js         # 仪表盘 API
│   ├── flow.js              # 流程引擎 API
│   ├── knowledge.js         # 知识库 API
│   ├── modelConfig.js       # AI 模型配置 API
│   ├── request.js           # 请求封装
│   └── user.js              # 用户 API
│
├── views/                   # 页面视图
│   ├── app/                # 应用管理
│   ├── dashboard/          # 仪表盘
│   ├── flow/               # 流程管理
│   ├── knowledge/          # 知识库管理
│   ├── system/             # 系统管理（用户/角色/凭证）
│   ├── Layout.vue          # 布局
│   └── login.vue           # 登录页
│
└── ...（其他静态资源）
```

---

## 四、核心模块设计

### 4.1 流程引擎（flow 模块）

#### 4.1.1 核心组件

| 组件 | 说明 |
|------|------|
| `FlowTemplate` | 流程模板实体，对应数据库表 `t_flow_template` |
| `FlowContext` | 流程上下文，存储运行时状态 |
| `FlowDefinition` | 流程定义，解析 JSON 格式的流程配置 |
| `NodeResult` | 节点执行结果 |
| `NodeExecutor` | 节点执行器接口 |
| `BaseNodeExecutor` | 节点执行器基类 |

#### 4.1.2 已实现的节点执行器

| 执行器 | 类型 | 功能 |
|--------|------|------|
| `LlmCallExecutor` | ai | 调用 AI 模型回答 |
| `KnowledgeRetrievalExecutor` | ai | 知识库检索 |
| `ConditionExecutor` | logic | 条件分支判断 |
| `ParameterExtractorExecutor` | foundation | 参数抽取 |
| `RouterExecutor` | foundation | 意图路由 |

#### 4.1.3 核心服务

| 服务 | 功能 |
|------|------|
| `FlowEngine` | 流程执行引擎 |
| `IntentRouterService` | 意图路由服务 |
| `FlowTemplateService` | 流程模板服务 |
| `NodeRegistryService` | 节点注册服务 |
| `DynamicPlannerService` | 动态规划服务 |
| `ContextManager` | 上下文管理（Redis 存储） |

### 4.2 知识库模块（knowledge 模块）

| 组件 | 功能 |
|------|------|
| `KnowledgeBase` | 知识库实体 |
| `KnowledgeItem` | 知识条目实体 |
| `Document` | 文档实体（关联知识条目） |
| `EmbeddingService` | 向量化服务 |
| `SearchService` | 搜索服务（RAG 能力） |
| `ElasticsearchService` | ES 检索服务 |
| `DocumentServiceImpl` | 文档处理服务（PDF/Word/Excel） |
| `MinioFileService` | MinIO 文件存储服务 |

### 4.3 AI 模型模块（ai 模块）

| 组件 | 功能 |
|------|------|
| `AiModelConfig` | AI 模型配置实体（对应 `t_ai_model_config` 表） |
| `UnifiedLlmService` | 统一 LLM 调用服务（支持多模型） |
| 支持的提供商 | qwen（通义千问）、deepseek、zhipu（智谱）、openai |

### 4.4 开放平台模块（open 模块）

| 组件 | 功能 |
|------|------|
| `OpenApiController` | 开放 API 控制器 |
| `OpenApiAuthService` | API 凭证鉴权服务 |
| `RateLimitService` | 流量控制服务（基于 Redis） |
| `SysApiCredential` | API 凭证实体 |

---

## 五、数据库表设计

### 5.1 核心表

| 表名 | 说明 |
|------|------|
| `t_flow_template` | 流程模板表 |
| `t_kb_knowledge_base` | 知识库表 |
| `t_kb_knowledge_item` | 知识条目表 |
| `t_ai_model_config` | AI 模型配置表 |
| `t_sys_api_credential` | API 凭证表 |
| `t_chat_session` | 聊天会话表 |
| `t_chat_message` | 聊天消息表 |
| `t_app_invocation_stat` | 调用统计表 |

### 5.2 流程模板表结构

```sql
CREATE TABLE t_flow_template (
    f_id              VARCHAR(32) PRIMARY KEY,
    f_template_code   VARCHAR(64) NOT NULL UNIQUE,
    f_template_name   VARCHAR(128) NOT NULL,
    f_description     VARCHAR(512),
    f_match_pattern   VARCHAR(256),       -- 匹配模式关键词
    f_match_prompt    TEXT,              -- 匹配提示词
    f_flow_data       JSON,               -- 完整流程定义
    f_priority        INT DEFAULT 0,      -- 优先级
    f_is_fallback     TINYINT(1) DEFAULT 0, -- 是否兜底模板
    f_is_dynamic      TINYINT(1) DEFAULT 1, -- 是否支持动态规划
    f_status          TINYINT DEFAULT 1,
    f_deleted         TINYINT DEFAULT 0,
    -- 审计字段
    f_create_time     DATETIME,
    f_update_time     DATETIME,
    f_create_by       VARCHAR(32),
    f_update_by       VARCHAR(32),
    f_version         INT DEFAULT 0
);
```

---

## 六、API 接口

### 6.1 流程管理 API

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/flow/template` | 分页查询模板 |
| `POST` | `/api/flow/template` | 创建模板 |
| `PUT` | `/api/flow/template` | 更新模板 |
| `DELETE` | `/api/flow/template/{id}` | 删除模板 |
| `POST` | `/api/flow/template/{id}/publish` | 发布模板 |
| `GET` | `/api/flow/nodes` | 获取可用节点 |

### 6.2 对话 API

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/flow/chat/stream` | SSE 流式对话 |
| `GET` | `/api/flow/chat/context` | 获取对话上下文 |
| `DELETE` | `/api/flow/chat/context` | 清除对话上下文 |

### 6.3 知识库 API

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/knowledge/base` | 知识库列表 |
| `POST` | `/api/knowledge/base` | 创建知识库 |
| `GET` | `/api/knowledge/item` | 知识条目列表 |
| `POST` | `/api/knowledge/item` | 创建知识条目 |
| `GET` | `/api/knowledge/search` | 知识检索 |

### 6.4 开放平台 API

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/open/v1/chat/completions` | AI 对话 |
| `POST` | `/api/open/v1/chat/completions/stream` | AI 对话（SSE） |
| `GET` | `/api/open/v1/experts` | 获取可用专家 |
| `GET` | `/api/open/v1/widget/config` | 获取嵌入配置 |

---

## 七、路由类型

技术设计文档中定义了 4 种路由类型：

| 类型 | 说明 |
|------|------|
| `FIXED_TEMPLATE` | 固定模板（预设流程） |
| `DYNAMIC_PLAN` | 动态规划（LLM 选择节点组合） |
| `DIRECT_ANSWER` | 直接回答（简单问答） |
| `FALLBACK` | 兜底（无匹配时） |

---

## 八、错误码

| 错误码 | 说明 | 用户提示 |
|--------|------|---------|
| TEMPLATE_NOT_FOUND | 模板不存在 | 请稍后再试 |
| TEMPLATE_NOT_PUBLISHED | 模板未发布 | 服务正在准备中，请稍后再试 |
| FLOW_ERROR | 流程执行异常 | 系统处理时遇到问题，请稍后再试 |
| NODE_EXECUTE_ERROR | 节点执行错误 | 处理您的请求时遇到问题，已为您跳过 |
| PARAM_MISSING | 缺少必需参数 | 请补充相关信息 |
| LLM_TIMEOUT | LLM 调用超时 | AI 服务响应较慢，请稍后再试 |
| KNOWLEDGE_ERROR | 知识库检索异常 | 知识库检索失败，将继续为您解答 |
| REDIS_ERROR | Redis 连接异常 | 会话服务暂时不可用，请稍后再试 |

---

## 九、部署说明

### 9.1 环境要求

- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- Elasticsearch 8.x（用于知识库检索）
- MinIO（用于文件存储）

### 9.2 初始化

执行初始化脚本：`scripts/docker/init/init-data.sql`

---

## 十、附录

### 10.1 术语表

| 术语 | 说明 |
|------|------|
| FlowTemplate | 流程模板 |
| FlowContext | 流程上下文 |
| NodeExecutor | 节点执行器 |
| IntentRoute | 意图路由 |
| RAG | 检索增强生成 |

### 10.2 参考文档

- Spring AI Alibaba：https://springaialibaba.bootcom.cn/
- 阿里云百炼平台：https://bailian.console.aliyun.com/