# AI 智答平台技术设计文档（Spring AI Alibaba 版）

## 一、文档概述

### 1.1 文档用途

本文档基于 Spring AI Alibaba（通义千问/DashScope）构建 AI 智能问答平台，采用**混合模式架构**：
- **动态路由**：LLM 自动理解用户意图，自主选择最优节点组合
- **固定模板**：复杂业务场景（报销、请假等）使用预设固定流程
- 节点 Spring Bean 化管理，可复用、可扩展

### 1.2 核心业务目标

- 提供统一 AI 问答入口，用户统一接入
- **固定流程模板**：复杂业务（报销、请假、审批等）使用预设模板
- **动态节点规划**：简单问题由 LLM 自动选择节点组合
- 流程由系统预设节点拖拽编排而成
- AI 自动完成意图识别 → 模板匹配/动态规划 → 参数抽取
- 支持多轮对话、上下文记忆、参数缺失自动反问
- 流程节点由后端开发为 Spring Bean，可复用、可扩展

### 1.3 技术栈

- 后端：Spring Boot 3.2.0 + JDK 17
- AI 框架：Spring AI Alibaba（通义千问/DashScope）
- 模型：qwen-turbo / qwen-plus / qwen-max / deepseek-chat
- 数据库：MySQL 8.0 + JPA
- 缓存：Redis（对话上下文存储）
- 持久层：JPA + MyBatis-Plus 混合
- 工具：Lombok、Hutool、RedisTemplate
- **流程引擎：自建引擎**（不依赖 Flowable，轻量可控）

---

## 二、整体系统架构

### 2.1 系统流程

```
用户发起对话
     ↓
统一对话接口（ChatController）
     ↓
从 Redis 加载上下文（有则继续，无则新对话）
     ↓
意图路由（单次 LLM 调用）
     ↓
┌─────────────────────────────────────────────────────────────┐
│  路由决策                                                    │
│    ├── 匹配固定模板 → 执行预设流程                            │
│    └── 未匹配 → 动态规划或兜底                                │
└─────────────────────────────────────────────────────────────┘
     ↓
流程引擎执行节点
     ↓
  - 收集节点：缺参数反问用户
  - 业务节点：调用服务完成逻辑
  - LLM 节点：调用 AI 模型回答
     ↓
返回结果 / 等待用户补充
```

### 2.2 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                       用户交互层                              │
│    智能助手对话  │  流程模板管理  │  流程编排器                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  ChatController  │  FlowTemplateController  │  FlowEditor   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  IntentRouter  │  FlowEngine  │  ContextManager  │  NodeRegistry │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│   Start  │  Collect  │  LLMCall  │  Knowledge  │  Execute  │  Condition │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
         MySQL (JPA)          │         Redis (上下文)
```

### 2.3 核心流程

```
用户消息 → 加载上下文（Redis）→ 意图路由 → 匹配模板/动态规划
              ↓                              ↓
           无上下文                      有上下文
              ↓                              ↓
         执行预设流程                 继续当前流程
```

### 2.3 核心流程（混合路由模式）

```
用户发起对话
     ↓
┌─────────────────────────────────────────────────────────────┐
│ 1. 加载对话上下文 (Redis)                                    │
└─────────────────────────────────────────────────────────────┘
     ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. 意图路由决策 (单次 LLM 调用)                               │
│    - 意图识别                                                │
│    - 模板匹配（高优先级固定模板优先匹配）                        │
│    - 参数抽取                                                │
└─────────────────────────────────────────────────────────────┘
     ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. 路由决策                                                  │
│    ├── 匹配固定模板 → 执行预设流程（TemplateExecutor）        │
│    └── 未匹配模板 → 动态节点规划（DynamicExecutor）           │
└─────────────────────────────────────────────────────────────┘
     ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. 执行流程节点 (按预设顺序或动态规划)                          │
│    - 参数收集节点：缺失参数则反问                              │
│    - 业务执行节点：调用服务完成逻辑                            │
│    - 条件分支节点：LLM 辅助决策                               │
│    - LLM 节点：调用 AI 模型回答                               │
└────────────���────────────────────────────────────────────────┘
     ↓
返回结果 / 等待用户补充
```

---

## 三、数据库表设计（简化版）

### 3.0 设计变更说明

**移除内容**：
- 删除 `sys_expert` 专家表（专家层级不再需要）
- 流程直接独立存在，不再关联专家

**保留内容**：
- 流程模板表 `sys_flow_template`（重构自专家流程表）
- 节点表 `sys_flow_node`
- 节点编排表 `sys_flow_template_node`

### 3.0 错误处理设计原则

为确保流程稳定性和用户体验，制定以下错误处理原则：

#### 3.0.1 核心原则

| 原则 | 说明 |
|------|------|
| **流程不中断** | 任何节点执行失败，流程必须有兜底处理，不能直接终止 |
| **用户友好提示** | 错误信息必须转换为用户能理解的语言 |
| **上下文保护** | 失败时不清除用户上下文，保留对话状态 |
| **可恢复性** | 允许用户重新输入或跳过失败步骤 |
| **日志记录** | 所有错误必须记录详细日志，便于排查 |

#### 3.0.2 失败场景处理策略

| 失败场景 | 处理策略 | 用户提示示例 |
|---------|---------|-------------|
| OCR解析失败 | 返回"无法识别图片，请重新上传或手动输入" | "抱歉，我无法识别这张图片，您可以重新上传或直接告诉我订单号" |
| LLM调用超时 | 重试3次后返回兜底答案 | "AI服务响应较慢，请稍后再试" |
| 知识库检索失败 | 跳过检索，继续使用LLM回答 | "未找到相关知识，但我会尽力帮助您" |
| 参数抽取失败 | 进入参数收集状态，提示用户补充 | "请提供您的订单号，以便我为您查询" |
| 网络异常 | 保存上下文，返回友好错误 | "网络连接不稳定，请检查网络后重试" |
| 节点执行异常 | 跳过当前节点，继续下一节点或结束流程 | "该步骤执行遇到问题，已为您跳过" |

### 3.1 流程模板表 `sys_flow_template`

> 替代原有的 `sys_expert_flow` 表，移除 `expert_id` 关联，流程直接独立存在

```sql
CREATE TABLE sys_flow_template (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    template_code   VARCHAR(64) NOT NULL UNIQUE COMMENT '模板编码',
    template_name   VARCHAR(128) NOT NULL COMMENT '模板名称',
    description     VARCHAR(512) COMMENT '描述说明',

    -- 匹配规则
    match_pattern   VARCHAR(256) COMMENT '匹配模式关键词（用于快速匹配）',
    match_prompt    TEXT COMMENT '匹配提示词（LLM理解用）',

    -- 模板内容
    flow_data       JSON COMMENT '完整流程定义（包含节点编排）',
    version         INT DEFAULT 1 COMMENT '版本号',

    -- 配置
    priority        INT DEFAULT 0 COMMENT '优先级（高优先级优先匹配）',
    is_fallback     TINYINT(1) DEFAULT 0 COMMENT '是否为兜底模板',
    is_dynamic      TINYINT(1) DEFAULT 1 COMMENT '是否支持动态规划',

    -- 状态
    status          TINYINT DEFAULT 1 COMMENT '状态：0禁用 1启用',
    published_at    DATETIME COMMENT '发布时间',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0 COMMENT '逻辑删除',

    INDEX idx_template_code (template_code),
    INDEX idx_status (status),
    INDEX idx_priority (priority DESC)
) COMMENT '流程模板表';

-- 预置模板示例
INSERT INTO sys_flow_template (template_code, template_name, description, match_pattern, match_prompt, flow_data, priority, is_fallback, status) VALUES
-- 费用报销流程（固定模板）
('reimbursement', '费用报销流程', '处理差旅费、日常报销等', '报销.*|差旅.*|报销单|报销金额',
 '当用户询问报销相关问题时触发，包括：差旅费报销、日常费用报销等场景',
 '[{"type":"collect","code":"amount","name":"报销金额"},{"type":"execute","code":"expense_submit"}]',
 100, 0, 1),

-- 请假申请流程（固定模板）
('leave_request', '请假申请流程', '处理各种请假申请', '请假.*|休假.*|请.*天.*假',
 '当用户询问请假、休假相关问题时触发，包括：年假、事假、病假等',
 '[{"type":"collect","code":"leave_type"},{"type":"collect","code":"days"},{"type":"execute","code":"leave_submit"}]',
 100, 0, 1),

-- 通用问答（兜底）
('general_qa', '通用问答', '无法匹配时的兜底处理', NULL,
 '当用户问题无法匹配任何固定流程时使用此模板',
 '[{"type":"knowledge_retrieval"},{"type":"llm_call"}]',
 0, 1, 1);
```

### 3.2 流程模板设计说明

**设计说明**：
- 流程模板直接独立存在，不再关联专家
- 通过 `match_pattern` 和 `match_prompt` 实现模板匹配
- 高优先级模板优先匹配（如：报销、请假等业务模板优先级 100）
- `is_fallback = 1` 的模板作为兜底，在无匹配时使用
- `is_dynamic = 1` 表示支持动态节点规划

**匹配规则**：
```
用户问题 → 意图分析（LLM）
                ↓
        ┌───────┴───────┐
        ↓               ↓
   匹配固定模板      未匹配 → 动态规划
   （高优先级）       （低优先级）
```

**关联关系**：
```
流程模板（FlowTemplate）
    │
    ├── 费用报销流程（reimbursement）→ 报销相关场景
    ├── 请假申请流程（leave_request）→ 请假相关场景
    ├── 订单查询流程（order_query）→ 订单相关场景
    └── 通用问答（general_qa）→ 兜底
```
### 3.3 流程节点表 `sys_flow_node`

```sql
CREATE TABLE sys_flow_node (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    node_code       VARCHAR(64) NOT NULL UNIQUE COMMENT '节点编码',
    node_name       VARCHAR(128) NOT NULL COMMENT '节点名称',
    node_type       VARCHAR(32) NOT NULL COMMENT '节点类型：collect/execute',
    bean_name       VARCHAR(128) COMMENT 'Spring Bean名称',
    param_name      VARCHAR(64) COMMENT '关联参数名',
    description     VARCHAR(512) COMMENT '节点描述',
    config_schema   JSON COMMENT '配置JSON Schema',
    status          TINYINT DEFAULT 1 COMMENT '状态：0禁用 1启用',
    sort_order      INT DEFAULT 0 COMMENT '排序',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0 COMMENT '逻辑删除'
) COMMENT '流程节点表';
```

### 3.4 节点编排表 `sys_flow_template_node`

> 用于存储模板与节点的关联关系

```sql
CREATE TABLE sys_flow_template_node (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    template_id     BIGINT NOT NULL COMMENT '模板ID',
    node_id         BIGINT NOT NULL COMMENT '节点ID',
    execution_order INT DEFAULT 0 COMMENT '节点执行顺序号',
    node_config     JSON COMMENT '节点运行时配置',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_template_node (template_id, node_id),
    INDEX idx_template_id (template_id)
) COMMENT '节点编排表';
```

### 3.5 对话上下文（Redis 存储）

```
Key: ai:chat:context:{userId}
TTL: 1800秒（30分钟）

结构：
{
    "userId": "用户ID",
    "expertCode": "专家编码",
    "flowCode": "流程编码",
    "currentNodeIndex": 0,
    "params": {},
    "status": "running|completed|waiting",
    "history": []
}
```

---

## 四、后端模块设计（简化版）

### 4.1 模块结构 `com.aip.flow`

> 简化自 `com.aip.expert`，移除专家相关模块

```
com.aip.flow/
├── controller/
│   ├── FlowTemplateController.java     # 流程模板管理
│   ├── FlowNodeController.java        # 节点管理
│   └── ChatController.java            # 对话接口
├── service/
│   ├── IFlowTemplateService.java      # 流程模板服务
│   ├── INodeRegistryService.java      # 节点注册服务（重构自 SkillRegistry）
│   ├── IIntentRouterService.java      # 意图路由服务（核心）
│   ├── IDynamicPlannerService.java    # 动态规划服务
│   └── impl/
│       ├── FlowTemplateServiceImpl.java
│       ├── NodeRegistryService.java    # 节点注册实现
│       ├── IntentRouterService.java    # 意图路由实现
│       └── DynamicPlannerService.java # LLM驱动的动态规划实现
├── entity/
│   ├── FlowTemplate.java              # 流程模板（重构自 ExpertFlow）
│   ├── FlowNode.java                  # 节点定义
│   └── FlowTemplateNode.java          # 节点编排关联
├── dto/
│   ├── FlowTemplateDTO.java           # 流程模板DTO
│   ├── IntentRouteResult.java         # 意图路由结果（核心）
│   ├── NodeDefinitionDTO.java         # 节点定义DTO
│   ├── FlowContextDTO.java             # 流程上下文DTO
│   ├── ChatRequestDTO.java            # 对话请求DTO
│   ├── RegisteredNode.java            # 已注册的节点（重构自 RegisteredSkill）
│   ├── NodeExecutionPlan.java         # 节点执行计划
│   ├── PlannedNode.java               # 规划节点定义
│   └── request/
│       ├── CreateFlowTemplateRequest.java
│       ├── UpdateFlowTemplateRequest.java
│       ├── SaveFlowRequest.java
│       └── ChatMessageRequest.java
├── mapper/
│   ├── FlowTemplateMapper.java        # 流程模板Mapper
│   ├── FlowNodeMapper.java            # 节点Mapper
│   └── FlowTemplateNodeMapper.java    # 节点编排Mapper
├── executor/                          # 节点执行器（保持不变）
│   ├── NodeExecutor.java              # 节点执行器接口
│   ├── BaseNodeExecutor.java          # 基础抽象类
│   ├── StartNodeExecutor.java         # 开始节点
│   ├── EndNodeExecutor.java           # 结束节点
│   ├── CollectNodeExecutor.java       # 收集类节点
│   ├── ConditionNodeExecutor.java      # 条件分支节点
│   ├── ExecuteNodeExecutor.java       # 执行类节点
│   ├── LLMCallNodeExecutor.java       # LLM调用节点
│   ├── EchoNodeExecutor.java          # 回显节点
│   └── KnowledgeRetrievalNodeExecutor.java # 知识检索节点
└── engine/
    ├── FlowEngine.java                # 流程引擎（重构）
    ├── TemplateExecutor.java          # 固定模板执行器
    ├── DynamicExecutor.java           # 动态节点执行器
    ├── FlowContext.java               # 流程上下文
    ├── FlowDefinition.java            # 流程定义
    ├── NodeResult.java                # 节点执行结果
    └── ContextManager.java            # 上下文管理器
```

### 4.2 核心数据结构

#### 4.2.1 RegisteredNode（已注册的节点）

> 重构自 RegisteredSkill，移除"专家"相关概念

```java
/**
 * 已注册的节点
 * 封装节点的元数据，供 LLM 理解和路由使用
 */
@Data
@Builder
public class RegisteredNode {

    /** 节点编码（唯一标识，对应 NodeExecutor.getNodeType()） */
    private String code;

    /** 节点名称 */
    private String name;

    /** 能力描述（LLM 可理解） */
    private String description;

    /** 分类（foundation/ai/execute/logic/advanced） */
    private String category;

    /** 触发词列表 */
    private List<String> triggers;

    /** 适用场景 */
    private List<String> scenarios;

    /** 输入参数 Schema */
    private NodeSchema inputSchema;

    /** 输出参数 Schema */
    private NodeSchema outputSchema;

    /** 配置参数 Schema */
    private NodeSchema configSchema;

    /** 关联的执行器 */
    private NodeExecutor executor;
}
```

#### 4.2.2 IntentRouteResult（意图路由结果）

> 重构自 UnifiedRouteResult，移除专家概念，简���路由决策

```java
/**
 * 意图路由结果
 * 一次 LLM 调用返回路由决策结果
 */
@Data
public class IntentRouteResult {

    /** 路由类型 */
    private RouteType routeType;

    /** 匹配到的模板编码（如果是固定模板） */
    private String templateCode;

    /** 匹配到的模板名称 */
    private String templateName;

    /** 识别的用户意图 */
    private String intent;

    /** 抽取到的业务参数 */
    private Map<String, Object> params;

    /** 置信度（用于判断是否需要追问） */
    private Double confidence;

    /** 建议的回复（当需要追问时） */
    private String prompt;

    /** 是否需要更多输入 */
    private boolean needMoreInput;

    /** 建议下一步操作 */
    private String nextAction;

    /** 路由类型枚举 */
    public enum RouteType {
        FIXED_TEMPLATE,   // 固定模板
        DYNAMIC_PLAN,      // 动态规划
        DIRECT_ANSWER,     // 直接回答（简单问答）
        FALLBACK          // 兜底
    }
}
```

#### 4.2.3 NodeSchema（参数 Schema）

```java
/**
 * 参数 Schema 定义
 * 用于描述节点的输入、输出、配置参数
 */
@Data
@Builder
public class NodeSchema {

    private String name;        // Schema 名称
    private String type;        // 参数类型（object/string/number/boolean）
    private String description; // 参数描述
    private boolean required;   // 是否必需
    private Map<String, Property> properties; // 对象类型的属性

    @Data
    @Builder
    public static class Property {
        private String type;
        private String description;
        private boolean required;
        private Object defaultValue;
        private List<String> enumValues; // 枚举值
    }
}
```

### 4.3 核心接口设计

#### 4.2.1 节点执行器接口

```java
/**
 * 节点执行器接口
 * 所有节点执行器实现此接口，并通过 @Component 注入 Spring 容器
 *
 * 【错误处理约定】
 * - 节点执行器内部必须捕获所有异常
 * - 失败时返回 NodeResult.fail() 而非抛出异常
 * - 错误信息应转换为用户友好的提示
 */
public interface NodeExecutor {

    /**
     * 获取节点类型编码
     */
    String getNodeType();

    /**
     * 获取节点名称
     */
    String getNodeName();

    /**
     * 获取节点分类
     */
    String getCategory();

    /**
     * 获取节点图标
     */
    String getIcon();

    /**
     * 获取输入 Schema
     */
    NodeSchema getInputSchema();

    /**
     * 获取输出 Schema
     */
    NodeSchema getOutputSchema();

    /**
     * 获取配置 Schema
     */
    NodeSchema getConfigSchema();

    /**
     * 执行节点
     * @param context 流程上下文
     * @param config 节点配置
     * @return 节点执行结果（失败时返回 NodeResult.fail()）
     */
    NodeResult execute(FlowContext context, Map<String, Object> config);
}
```

#### 4.2.2 节点执行结果

```java
/**
 * 节点执行结果
 * 统一的结果封装，包含成功/失败状态和用户友好的消息
 */
@Data
public class NodeResult {

    /** 是否成功 */
    private boolean success;

    /** 是否需要更多输入（参数收集场景） */
    private boolean needMoreInput;

    /** 输出内容（AI回复等） */
    private String output;

    /** 收集到的参数 */
    private Map<String, Object> params;

    /** 提示用户输入的消息 */
    private String prompt;

    /** 错误信息（内部使用，不直接展示给用户） */
    private String error;

    /** 错误码 */
    private String errorCode;

    /** 元数据（调试信息等） */
    private Map<String, Object> metadata;

    // ==================== 工厂方法 ====================

    /**
     * 成功结果
     */
    public static NodeResult success(String output) {
        NodeResult result = new NodeResult();
        result.setSuccess(true);
        result.setOutput(output);
        return result;
    }

    /**
     * 成功结果（带参数）
     */
    public static NodeResult success(String output, Map<String, Object> params) {
        NodeResult result = new NodeResult();
        result.setSuccess(true);
        result.setOutput(output);
        result.setParams(params);
        return result;
    }

    /**
     * 需要更多输入
     */
    public static NodeResult needInput(String prompt) {
        NodeResult result = new NodeResult();
        result.setSuccess(true);
        result.setNeedMoreInput(true);
        result.setPrompt(prompt);
        return result;
    }

    /**
     * 失败结果（用户友好）
     * @param userMessage 用户友好的错误提示
     * @param errorCode 错误码
     */
    public static NodeResult fail(String userMessage, String errorCode) {
        NodeResult result = new NodeResult();
        result.setSuccess(false);
        result.setOutput(userMessage);  // 失败消息也作为输出返回给前端
        result.setErrorCode(errorCode);
        return result;
    }

    /**
     * 失败结果（带参数，可继续流程）
     * 用于部分成功的场景，如参数缺失但可以提示用户补充
     */
    public static NodeResult partialFail(String userMessage, String prompt) {
        NodeResult result = new NodeResult();
        result.setSuccess(false);
        result.setOutput(userMessage);
        result.setNeedMoreInput(true);
        result.setPrompt(prompt);
        return result;
    }

    /**
     * 跳过当前节点
     * 用于可恢复的错误场景，如第三方服务不可用
     */
    public static NodeResult skip(String reason) {
        NodeResult result = new NodeResult();
        result.setSuccess(true);  // 流程继续执行
        result.setOutput("[已跳过]" + reason);
        return result;
    }

    /**
     * 流程结束
     */
    public static NodeResult completed() {
        NodeResult result = new NodeResult();
        result.setSuccess(true);
        result.setOutput("流程已结束");
        return result;
    }
}
```

#### 4.2.3 节点注册服务

```java
/**
 * 节点注册服务 - 从 Spring 容器获取所有节点执行器
 *
 * 【容错设计】
 * - 所有节点执行器必须实现 NodeExecutor 接口
 * - 节点执行失败由 FlowEngine 统一处理，捕获异常避免流程中断
 */
/**
 * Skills 注册服务
 * 从 Spring 容器获取所有节点执行器，并注册为可用 Skills
 *
 * 【设计说明】
 * - 所有实现 NodeExecutor 接口的 Bean 自动注册为 Skill
 * - 每个 Skill 提供元数据（描述、触发词等），供 LLM 路由使用
 * - 提供 Skills 列表查询能力
 */
@Service
@Slf4j
public class SkillRegistryService {

    /**
     * Skills 缓存：skillCode -> RegisteredSkill
     */
    private Map<String, RegisteredSkill> skillCache;

    @Autowired
    private List<NodeExecutor> nodeExecutors;

    @PostConstruct
    public void init() {
        skillCache = nodeExecutors.stream()
            .map(this::convertToSkill)
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(RegisteredSkill::getCode, s -> s));

        log.info("Skills 注册完成，共注册 {} 个技能", skillCache.size());
    }

    /**
     * 将 NodeExecutor 转换为 RegisteredSkill
     */
    private RegisteredSkill convertToSkill(NodeExecutor executor) {
        try {
            return RegisteredSkill.builder()
                .code(executor.getNodeType())
                .name(executor.getNodeName())
                .description(executor.getDescription())
                .category(executor.getCategory())
                .triggers(executor.getTriggers())
                .inputSchema(executor.getInputSchema())
                .outputSchema(executor.getOutputSchema())
                .configSchema(executor.getConfigSchema())
                .executor(executor)
                .build();
        } catch (Exception e) {
            log.warn("注册 Skill 失败: executor={}, error={}",
                executor.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * 获取所有 Skills
     */
    public List<RegisteredSkill> getAllSkills() {
        return new ArrayList<>(skillCache.values());
    }

    /**
     * 获取指定分类的 Skills
     */
    public List<RegisteredSkill> getSkillsByCategory(String category) {
        return skillCache.values().stream()
            .filter(s -> s.getCategory().equals(category))
            .collect(Collectors.toList());
    }

    /**
     * 获取指定 Skill 的执行器
     */
    public NodeExecutor getExecutor(String skillCode) {
        RegisteredSkill skill = skillCache.get(skillCode);
        return skill != null ? skill.getExecutor() : null;
    }

    /**
     * 安全获取执行器（不抛异常）
     */
    public Optional<NodeExecutor> getExecutorSafe(String skillCode) {
        return Optional.ofNullable(getExecutor(skillCode));
    }

    /**
     * 生成 Skills 描述供 LLM 使用
     */
    public String generateSkillsDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("【可用技能列表】\n\n");

        Map<String, List<RegisteredSkill>> grouped = skillCache.values().stream()
            .collect(Collectors.groupingBy(RegisteredSkill::getCategory));

        for (Map.Entry<String, List<RegisteredSkill>> entry : grouped.entrySet()) {
            sb.append("## ").append(entry.getKey()).append("\n");
            for (RegisteredSkill skill : entry.getValue()) {
                sb.append(String.format("- [%s] %s: %s\n",
                    skill.getCode(),
                    skill.getName(),
                    skill.getDescription()));

                if (skill.getTriggers() != null && !skill.getTriggers().isEmpty()) {
                    sb.append("  触发词: ").append(String.join(", ", skill.getTriggers())).append("\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
```

### 4.3 预定义节点类型（Skills 分类）

| 节点类型 | 编码 | 分类 | 功能 | 触发词示例 |
|---------|------|------|------|-----------|
| 开始 | `start` | foundation | 流程入口 | - |
| 结束 | `end` | foundation | 流程结束 | - |
| 参数收集 | `collect` | foundation | 收集用户参数 | "请告诉我"、"需要提供" |
| LLM调用 | `llm_call` | ai | 调用AI模型 | "帮我分析"、"回答" |
| 知识检索 | `knowledge_retrieval` | ai | 检索知识库 | "查一下"、"有规定吗" |
| 条件分支 | `condition` | logic | 条件判断分支 | "如果"、"根据" |
| 变量处理 | `variable` | logic | 变量赋值/转换 | "设置为"、"赋值" |
| OCR解析 | `ocr` | advanced | 图片文字识别 | "识别图片"、"上传图片" |
| 工具调用 | `tool_call` | advanced | 调用外部工具 | - |

### 4.4 节点执行器示例（OCR解析）

```java
/**
 * OCR解析节点执行器
 * 演示如何处理失败场景
 *
 * 【错误处理模式】
 * 1. 捕获所有可能的异常
 * 2. 根据异常类型返回不同的友好提示
 * 3. 返回 NodeResult.fail() 而非抛出异常
 * 4. 记录详细日志便于排查
 */
@Slf4j
@Component
public class OcrNodeExecutor implements NodeExecutor {

    @Autowired
    private OcrService ocrService;

    @Override
    public String getNodeType() {
        return "ocr";
    }

    @Override
    public String getNodeName() {
        return "OCR解析";
    }

    @Override
    public String getCategory() {
        return "advanced";
    }

    @Override
    public String getIcon() {
        return "Scanner";
    }

    @Override
    public NodeResult execute(FlowContext context, Map<String, Object> config) {
        String paramName = (String) config.getOrDefault("paramName", "ocr_text");
        boolean required = (Boolean) config.getOrDefault("required", true);

        try {
            // 1. 从上下文中获取图片数据
            Object imageData = context.getParams().get("image_data");
            if (imageData == null) {
                // 无图片，返回需要输入提示
                return NodeResult.needInput("请上传需要识别的图片");
            }

            // 2. 调用OCR服务
            String imageUrl = imageData.toString();
            String result = ocrService.recognize(imageUrl);

            // 3. 检查识别结果
            if (result == null || result.isBlank()) {
                log.warn("OCR识别结果为空: imageUrl={}", imageUrl);
                if (required) {
                    return NodeResult.partialFail(
                        "抱歉，无法识别图片中的文字",
                        "您可以重新上传清晰的图片，或者直接告诉我需要查询的内容"
                    );
                } else {
                    // 非必需参数，跳过
                    return NodeResult.skip("OCR识别结果为空").setParams(Map.of(paramName, ""));
                }
            }

            // 4. 返回识别结果
            log.info("OCR识别成功: length={}", result.length());
            return NodeResult.success("已识别文字内容")
                    .setParams(Map.of(paramName, result));

        } catch (OcrServiceException e) {
            log.error("OCR服务异常: {}", e.getMessage(), e);
            return NodeResult.fail(
                "图片识别服务暂时不可用，请稍后再���或手动输入",
                "OCR_SERVICE_ERROR"
            );

        } catch (Exception e) {
            log.error("OCR解析执行异常", e);
            return NodeResult.fail(
                "无法识别这张图片，请重新上传或直接告诉我订单号等信息",
                "OCR_PARSE_ERROR"
            );
        }
    }
}
```

### 4.4 流程定义 JSON 结构

```json
{
  "flowId": "flow_001",
  "flowName": "客服专家流程",
  "nodes": [
    {
      "id": "node_1",
      "type": "start",
      "position": {"x": 100, "y": 200},
      "data": {}
    },
    {
      "id": "node_2",
      "type": "collect",
      "position": {"x": 300, "y": 200},
      "data": {
        "paramName": "orderId",
        "prompt": "请提供您的订单号",
        "required": true
      }
    },
    {
      "id": "node_3",
      "type": "llm_call",
      "position": {"x": 500, "y": 200},
      "data": {
        "modelId": 1,
        "temperature": 0.7,
        "systemPrompt": "你是专业客服..."
      }
    },
    {
      "id": "node_4",
      "type": "end",
      "position": {"x": 700, "y": 200},
      "data": {}
    }
  ],
  "edges": [
    {"id": "e1", "source": "node_1", "target": "node_2"},
    {"id": "e2", "source": "node_2", "target": "node_3"},
    {"id": "e3", "source": "node_3", "target": "node_4"}
  ]
}
```

---

## 五、知识检索判断时机设计

### 5.0.1 三种判断时机对比

| 判断时机 | 说明 | 优点 | 缺点 | 适用场景 |
|---------|------|------|------|---------|
| 专家选择时 | 在路由到专家时判断是否需要知识库 | 早期决策，流程清晰 | 粒度太粗，不够灵活 | 专家与知识库强绑定 |
| 流程选择时 | 在路由到流程时判断 | 流程级配置，比专家级更灵活 | 仍需预设 | 同专家下不同流程有不同知识库需求 |
| 节点执行时 | 在知识检索节点执行时动态判断 | 最灵活，可按问题动态决定 | 实现复杂度稍高 | 需要灵活判断的场景 |

### 5.0.2 推荐方案：混合策略

**核心设计思路**：
1. **流程级别**：流程设计时决定"有没有"知识检索节点
2. **节点级别**：知识检索节点执行时决定"这次是否需要"检索

### 5.0.3 知识检索节点配置

```java
/**
 * 知识检索节点执行器
 * 支持两种检索策略：自动检索 + 按需检索
 */
@Slf4j
@Component
public class KnowledgeRetrievalNodeExecutor implements NodeExecutor {

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private LlmService llmService;

    @Override
    public String getNodeType() {
        return "knowledge_retrieval";
    }

    @Override
    public NodeResult execute(FlowContext context, Map<String, Object> config) {
        // 1. 获取检索策略配置
        String retrievalStrategy = (String) config.getOrDefault("retrievalStrategy", "auto");
        String knowledgeId = (String) config.getOrDefault("knowledgeId", "");
        Integer topK = (Integer) config.getOrDefault("topK", 5);
        Double threshold = (Double) config.getOrDefault("similarityThreshold", 0.7);

        // 2. 根据策略决定是否检索
        if ("auto".equals(retrievalStrategy)) {
            // 策略1：自动检索 - 总是执行知识库检索
            return doRetrieval(context, config, knowledgeId, topK, threshold);

        } else if ("on_demand".equals(retrievalStrategy)) {
            // 策略2：按需检索 - 先判断是否需要检索
            boolean needRetrieval = shouldRetrieveKnowledge(context);
            if (needRetrieval) {
                log.info("按需检索：判断需要检索，执行知识库检索");
                return doRetrieval(context, config, knowledgeId, topK, threshold);
            } else {
                log.info("按需检索：判断不需要检索，跳过");
                return NodeResult.skip("问题不需要知识库检索");
            }
        }

        return NodeResult.skip("未配置的检索策略");
    }

    /**
     * 判断是否需要检索（方案三的核心）
     * 使用 LLM 判断当前问题是否涉及知识库内容
     */
    private boolean shouldRetrieveKnowledge(FlowContext context) {
        String userMessage = (String) context.getParams().get("_current_message");

        // 构建判断提示词
        String prompt = String.format("""
            用户问题：%s

            判断这个问题是否需要从知识库检索答案？
            - 如果问题涉及具体政策、规定、流程、手册、条款等知识库内容，返回：是
            - 如果问题是通用对话、闲聊、简单计算、纯知识性问题（LLM能直接回答的），返回：否

            只回答"是"或"否"，不要其他内容。
            """,
            userMessage
        );

        try {
            String result = llmService.chat(prompt);
            boolean need = result.trim().contains("是");
            log.info("RAG判断结果: needRetrieval={}, question={}", need, userMessage);
            return need;
        } catch (Exception e) {
            log.warn("RAG判断失败，默认检索: error={}", e.getMessage());
            return true;  // 判断失败时默认检索
        }
    }

    /**
     * 执行知识库检索
     */
    private NodeResult doRetrieval(FlowContext context, Map<String, Object> config,
                                   String knowledgeId, Integer topK, Double threshold) {
        String userMessage = (String) context.getParams().get("_current_message");

        try {
            // 1. 执行知识库检索
            List<KnowledgeResult> results = knowledgeService.search(
                knowledgeId, userMessage, topK, threshold
            );

            // 2. 处理检索结果
            if (results.isEmpty()) {
                log.info("知识库检索结果为空");
                // 根据配置决定是否继续
                Boolean fallbackWhenEmpty = (Boolean) config.getOrDefault("fallbackWhenEmpty", true);
                if (fallbackWhenEmpty) {
                    // 无结果时跳过，继续 LLM 回答
                    return NodeResult.skip("知识库未找到相关内容")
                            .setParams(Map.of("_knowledge_results", "[]"));
                } else {
                    return NodeResult.fail("未找到相关知识库内容", "KNOWLEDGE_NOT_FOUND");
                }
            }

            // 3. 组装检索结果
            StringBuilder knowledgeContext = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                KnowledgeResult r = results.get(i);
                knowledgeContext.append(String.format("[%d] %s%n来源: %s%n%n",
                    i + 1, r.getContent(), r.getSource()));
            }

            // 4. 保存到上下文
            Map<String, Object> params = new HashMap<>();
            params.put("_knowledge_results", knowledgeContext.toString());
            params.put("_knowledge_count", results.size());

            log.info("知识库检索成功: count={}, question={}", results.size(), userMessage);
            return NodeResult.success("已检索到 " + results.size() + " 条相关内容", params);

        } catch (KnowledgeServiceException e) {
            log.error("知识库检索异常: {}", e.getMessage(), e);
            // 失败时跳过，继续流程
            return NodeResult.skip("知识库检索失败，将直接回答");
        } catch (Exception e) {
            log.error("知识库检索执行异常", e);
            return NodeResult.skip("知识库检索遇到问题");
        }
    }
}
```

### 5.0.4 节点配置 Schema

```json
{
  "nodeCode": "knowledge_retrieval",
  "nodeName": "知识检索",
  "category": "ai",
  "icon": "Search",
  "configSchema": {
    "properties": {
      "knowledgeId": {
        "type": "string",
        "title": "知识库ID",
        "description": "关联的知识库ID"
      },
      "retrievalStrategy": {
        "type": "string",
        "title": "检索策略",
        "enum": ["auto", "on_demand"],
        "default": "auto"
      },
      "topK": {
        "type": "number",
        "title": "检索数量",
        "default": 5
      },
      "similarityThreshold": {
        "type": "number",
        "title": "相似度阈值",
        "minimum": 0,
        "maximum": 1,
        "default": 0.7
      },
      "fallbackWhenEmpty": {
        "type": "boolean",
        "title": "无结果时继续",
        "description": "知识库无结果时是否继续流程",
        "default": true
      }
    }
  }
}
```

### 5.0.5 检索策略说明

| 策略 | 配置值 | 使用场景 | 示例 |
|------|--------|---------|------|
| 自动检索 | `auto` | 客服、文档问答等固定场景 | 总是先检索再回答 |
| 按需检索 | `on_demand` | HR助手等需要灵活判断的场景 | LLM判断是否需要 |

**按需检索判断示例**：
- "年假多少天？" → 判断：是 → 检索HR政策
- "帮我算个税" → 判断：否 → 跳过，直接回答
- "公司地址在哪？" → 判断：是 → 检索公司信息
- "你好" → 判断：否 → 跳过，直接打招呼

### 5.0.6 流程编排示例

```
┌─────────────────────────────────────────────────────────────┐
│ 场景1：智能客服流程（固定检索）                                │
├─────────────────────────────────────────────────────────────┤
│ 开始 → 参数收集 → 【知识检索(auto)】 → LLM回答 → 结束          │
│         ↓                                                   │
│   配置: retrievalStrategy = "auto"                          │
│   效果：总是先检索知识库，再 LLM 综合回答                       │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ 场景2：HR助手流程（按需检索）                                  │
├─────────────────────────────────────────────────────────────┤
│ 开始 → 参数收集 → 【知识检索(on_demand)】 → LLM回答 → 结束      │
│         ↓                                                   │
│   配置: retrievalStrategy = "on_demand"                       │
│   效果：LLM 判断是否需要检索知识库                             │
│         - "年假多少天？" → 检索HR政策                        │
│         - "你好" → 跳过，直接回答                            │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ 场景3：通用问答（无知识库）                                    │
├─────────────────────────────────────────────────────────────┤
│ 开始 → 参数收集 → LLM回答 → 结束                              │
│         ↓                                                   │
│   无知识检索节点，直接 LLM 回答                                │
└─────────────────────────────────────────────────────────────┘
```

---

## 五、AI 路由层设计

### 5.0 设计理念

**核心原则**：
1. **合并路由调用**：将「专家路由 + 流程路由 + 参数抽取」合并为一次 LLM 调用，降低延迟和成本
2. **预设流程骨架**：业务流程由人工预设，规定业务边界和标准处理步骤
3. **LLM 动态决策**：在预设骨架内，LLM 负责意图识别、参数补充、分支选择
4. **Skills 统一抽象**：所有节点执行器抽象为 Skills，支持动态注册和能力描述

**架构演进**：

```
旧方案（多次调用）：
用户消息 → 专家路由LLM → 流程路由LLM → 参数抽取LLM → 执行
           ↓               ↓              ↓
          3次LLM调用     3次LLM调用     3次LLM调用

新方案（合并调用）：
用户消息 → 【统一路由LLM】 → 执行
              ↓
         1次LLM调用
```

### 5.1 Skills 统一抽象

#### 5.1.1 Skills 定义

每个节点执行器都是一个 Skill，包含以下元数据：

```
┌─────────────────────────────────────────────────────────────┐
│  RegisteredSkill（已注册的技能）                              │
├─────────────────────────────────────────────────────────────┤
│  code: String              // 技能编码，唯一标识              │
│  name: String              // 技能名称                       │
│  description: String        // 能力描述（LLM 可理解）          │
│  category: String           // 分类（collect/execute/ai/logic）│
│  triggers: List<String>     // 触发词列表（提高路由准确率）      │
│  inputSchema: JSONSchema    // 输入参数定义                    │
│  outputSchema: JSONSchema   // 输出参数定义                    │
│  configSchema: JSONSchema   // 配置参数定义                    │
│  executor: NodeExecutor     // 实际执行器（Spring Bean）       │
└─────────────────────────────────────────────────────────────┘
```

#### 5.1.2 Skills 注册服务

```java
/**
 * Skills 注册服务
 * 负责从 Spring 容器收集所有 Skills，并提供查询能力
 *
 * 【设计说明】
 * - 所有实现 NodeExecutor 接口的 Bean 自动注册为 Skill
 * - 每个 Skill 提供能力描述，供 LLM 理解和使用
 */
@Service
@Slf4j
public class SkillRegistryService {

    /**
     * Skills 缓存：skillCode -> RegisteredSkill
     */
    private Map<String, RegisteredSkill> skillCache;

    @Autowired
    private List<NodeExecutor> nodeExecutors;

    @PostConstruct
    public void init() {
        // 从 Spring 容器获取所有 NodeExecutor，自动注册为 Skill
        skillCache = nodeExecutors.stream()
            .map(this::convertToSkill)
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(RegisteredSkill::getCode, s -> s));

        log.info("Skills 注册完成，共注册 {} 个技能", skillCache.size());
    }

    /**
     * 将 NodeExecutor 转换为 RegisteredSkill
     * 从执行器的元数据方法中提取能力描述
     */
    private RegisteredSkill convertToSkill(NodeExecutor executor) {
        try {
            return RegisteredSkill.builder()
                .code(executor.getNodeType())
                .name(executor.getNodeName())
                .description(executor.getDescription())
                .category(executor.getCategory())
                .triggers(executor.getTriggers())
                .inputSchema(executor.getInputSchema())
                .outputSchema(executor.getOutputSchema())
                .configSchema(executor.getConfigSchema())
                .executor(executor)
                .build();
        } catch (Exception e) {
            log.warn("注册 Skill 失败: executor={}, error={}",
                executor.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * 获取所有已注册的 Skills
     */
    public List<RegisteredSkill> getAllSkills() {
        return new ArrayList<>(skillCache.values());
    }

    /**
     * 获取指定分类的 Skills
     */
    public List<RegisteredSkill> getSkillsByCategory(String category) {
        return skillCache.values().stream()
            .filter(s -> s.getCategory().equals(category))
            .collect(Collectors.toList());
    }

    /**
     * 获取指定 Skill 的执行器
     */
    public NodeExecutor getExecutor(String skillCode) {
        RegisteredSkill skill = skillCache.get(skillCode);
        return skill != null ? skill.getExecutor() : null;
    }

    /**
     * 生成 Skills 描述供 LLM 使用
     * 返回结构化的 Skills 列表，用于路由决策
     */
    public String generateSkillsDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("【可用技能列表】\n\n");

        // 按分类分组
        Map<String, List<RegisteredSkill>> grouped = skillCache.values().stream()
            .collect(Collectors.groupingBy(RegisteredSkill::getCategory));

        for (Map.Entry<String, List<RegisteredSkill>> entry : grouped.entrySet()) {
            sb.append("## ").append(entry.getKey()).append("\n");
            for (RegisteredSkill skill : entry.getValue()) {
                sb.append(String.format("- [%s] %s: %s\n",
                    skill.getCode(),
                    skill.getName(),
                    skill.getDescription()));

                // 添加触发词
                if (skill.getTriggers() != null && !skill.getTriggers().isEmpty()) {
                    sb.append("  触发词: ").append(String.join(", ", skill.getTriggers())).append("\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
```

#### 5.1.3 扩展 NodeExecutor 接口

为支持 Skills 抽象，需要扩展 `NodeExecutor` 接口：

```java
/**
 * 节点执行器接口（扩展版）
 * 所有节点执行器实现此接口，自动注册为可被 LLM 调用的 Skill
 */
public interface NodeExecutor {

    // ==================== 基础信息 ====================

    /** 获取节点类型编码（作为 Skill 的唯一标识） */
    String getNodeType();

    /** 获取节点名称 */
    String getNodeName();

    /** 获取节点分类 */
    String getCategory();

    // ==================== Skills 元数据（新增强制方法） ====================

    /** 获取技能描述（LLM 可理解的能力说明） */
    default String getDescription() {
        return getNodeName();
    }

    /** 获取触发词列表（提高路由准确率） */
    default List<String> getTriggers() {
        return Collections.emptyList();
    }

    /** 获取输入参数 Schema */
    NodeSchema getInputSchema();

    /** 获取输出参数 Schema */
    NodeSchema getOutputSchema();

    /** 获取配置参数 Schema */
    NodeSchema getConfigSchema();

    // ==================== 执行方法 ====================

    /**
     * 执行节点
     * @param context 流程上下文
     * @param config 节点配置
     * @return 节点执行结果
     */
    NodeResult execute(FlowContext context, Map<String, Object> config);
}
```

#### 5.1.4 Skills 示例（扩展后的节点执行器）

```java
/**
 * 订单查询节点执行器（示例）
 * 演示如何定义 Skill 的元数据
 */
@Slf4j
@Component
public class OrderQueryNodeExecutor implements NodeExecutor {

    @Override
    public String getNodeType() {
        return "order_query";
    }

    @Override
    public String getNodeName() {
        return "订单查询";
    }

    @Override
    public String getCategory() {
        return "execute";
    }

    // ==================== Skills 元数据实现 ====================

    @Override
    public String getDescription() {
        return "查询用户的订单信息，包括订单状态、物流进度、收货地址等";
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList(
            "查订单", "我的订单", "订单到哪了", "物流信息",
            "发货了吗", "什么时候到", "快递单号"
        );
    }

    @Override
    public NodeSchema getInputSchema() {
        return NodeSchema.builder()
            .name("orderQueryInput")
            .type("object")
            .properties(Map.of(
                "orderId", NodeSchema.Property.builder()
                    .type("string")
                    .description("订单号")
                    .required(true)
                    .build()
            ))
            .build();
    }

    @Override
    public NodeSchema getOutputSchema() {
        return NodeSchema.builder()
            .name("orderQueryOutput")
            .type("object")
            .properties(Map.of(
                "orderStatus", NodeSchema.Property.builder()
                    .type("string")
                    .description("订单状态")
                    .build(),
                "logisticsInfo", NodeSchema.Property.builder()
                    .type("object")
                    .description("物流信息")
                    .build()
            ))
            .build();
    }

    // ==================== 执行逻辑 ====================

    @Override
    public NodeResult execute(FlowContext context, Map<String, Object> config) {
        String orderId = (String) context.getParams().get("orderId");

        // 缺少参数时返回收集提示
        if (orderId == null || orderId.isBlank()) {
            return NodeResult.needInput("请提供您的订单号，以便我为您查询");
        }

        try {
            // 调用订单服务查询
            OrderInfo orderInfo = orderService.queryById(orderId);
            return NodeResult.success("已为您查询到订单信息")
                .setParams(Map.of("orderInfo", orderInfo));
        } catch (Exception e) {
            log.error("订单查询失败: orderId={}", orderId, e);
            return NodeResult.fail("查询订单失败，请稍后再试", "ORDER_QUERY_ERROR");
        }
    }
}
```

#### 5.1.5 预定义 Skills 分类

| 分类 | 说明 | 示例 |
|------|------|------|
| `foundation` | 基础节点 | start、end、collect |
| `ai` | AI 能力节点 | llm_call、knowledge_retrieval |
| `execute` | 业务执行节点 | order_query、refund_apply |
| `logic` | 逻辑控制节点 | condition、variable |
| `advanced` | 高级能力节点 | ocr、tool_call |

### 5.2 统一路由服务

#### 5.2.1 设计概述

**合并调用**：将原来的 3 次 LLM 调用（专家路由 + 流程路由 + 参数抽取）合并为 1 次。

**统一路由响应**：

```java
/**
 * 统一路由结果
 * 一次 LLM 调用返回所有需要的信息
 */
@Data
public class UnifiedRouteResult {

    /** 匹配的专家编码 */
    private String expertCode;

    /** 匹配的流程编码 */
    private String flowCode;

    /** 识别的用户意图 */
    private String intent;

    /** 抽取到的业务参数 */
    private Map<String, Object> params;

    /** 置信度（用于判断是否需要追问） */
    private Double confidence;

    /** 建议的回复（当需要追问时） */
    private String prompt;
}
```

#### 5.2.2 统一路由服务实现

```java
/**
 * 统一路由服务
 * 一次 LLM 调用完成：专家路由 + 流程路由 + 意图识别 + 参数抽取
 *
 * 【核心设计】
 * - 将所有需要的信息一次性发送给 LLM
 * - LLM 返回结构化的路由结果
 * - 支持多轮对话上下文
 */
@Service
@Slf4j
public class UnifiedRouterService {

    @Autowired
    private ExpertMapper expertMapper;

    @Autowired
    private ExpertFlowMapper flowMapper;

    @Autowired
    private SkillRegistryService skillRegistry;

    @Autowired
    private QwenApiService qwenApiService;

    /**
     * 执行统一路由
     *
     * @param userMessage 用户消息
     * @param context 流程上下文（可为空，用于多轮对话）
     * @return 统一路由结果
     */
    public UnifiedRouteResult route(String userMessage, FlowContext context) {
        // 1. 加载路由所需数据
        List<Expert> experts = expertMapper.findAllEnabled();
        if (CollectionUtils.isEmpty(experts)) {
            throw new BusinessException("系统未配置任何专家");
        }

        // 2. 构建统一路由提示词
        String prompt = buildUnifiedPrompt(userMessage, experts, context);

        // 3. 调用 LLM 获取路由结果
        try {
            List<Message> messages = List.of(new Message("user", prompt));
            String response = qwenApiService.chat(getApiKey(), "qwen-plus", messages);

            // 4. 解析 LLM 响应
            return parseRouteResult(response, experts);

        } catch (Exception e) {
            log.error("统一路由失败，使用兜底方案: error={}", e.getMessage());
            return buildFallbackResult(experts);
        }
    }

    /**
     * 构建统一路由提示词
     * 将专家列表、流程列表、Skills 列表全部传入，一次决策
     */
    private String buildUnifiedPrompt(String userMessage, List<Expert> experts,
                                      FlowContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 统一路由任务\n\n");
        sb.append("请分析用户问题，一次性完成以下决策：\n");
        sb.append("1. 选择最合适的专家\n");
        sb.append("2. 选择该专家下最合适的流程\n");
        sb.append("3. 识别用户意图\n");
        sb.append("4. 抽取业务参数\n\n");

        // 添加上下文信息（多轮对话时）
        if (context != null && context.getExpertCode() != null) {
            sb.append("【当前对话上下文】\n");
            sb.append("- 当前专家：").append(context.getExpertCode()).append("\n");
            sb.append("- 当前流程：").append(context.getFlowCode()).append("\n");
            sb.append("- 已收集参数：").append(context.getParams()).append("\n");
            sb.append("- 当前状态：").append(context.getStatus()).append("\n\n");
        }

        // 添加专家列表
        sb.append("【可用的专家】\n");
        for (Expert expert : experts) {
            String marker = Boolean.TRUE.equals(expert.getIsDefault()) ? " [默认/兜底]" : "";
            sb.append(String.format("- %s: %s%s\n",
                expert.getExpertCode(),
                expert.getRouterPrompt(),
                marker));
        }
        sb.append("\n");

        // 添加每个专家的流程列表
        sb.append("【专家对应的流程】\n");
        for (Expert expert : experts) {
            List<ExpertFlow> flows = flowMapper.findByExpertCode(expert.getExpertCode());
            if (CollectionUtils.isEmpty(flows)) continue;

            sb.append(String.format("## %s 专家的流程：\n", expert.getExpertCode()));
            for (ExpertFlow flow : flows) {
                sb.append(String.format("- %s: %s\n",
                    flow.getFlowCode(),
                    flow.getRouterPrompt()));
            }
        }
        sb.append("\n");

        // 添加 Skills 描述（供 LLM 理解可用能力）
        sb.append(skillRegistry.generateSkillsDescription());

        // 添加输出格式说明
        sb.append("【输出格式要求】\n");
        sb.append("请以 JSON 格式返回，示例：\n");
        sb.append("""
            {
              "expertCode": "customer_service",
              "flowCode": "order_query_flow",
              "intent": "查询订单状态",
              "params": {
                "orderId": "123456",
                "userId": "user_001"
              },
              "confidence": 0.95,
              "prompt": null
            }

            注意事项：
            - 如果缺少必需参数，设置 confidence < 0.7，并在 prompt 中说明需要什么
            - 如果无法确定路由结果，使用默认专家
            - params 中只返回能确定的参数，不要编造
            """);

        // 添加用户消息
        sb.append("【用户问题】\n");
        sb.append(userMessage);

        return sb.toString();
    }

    /**
     * 解析 LLM 响应
     */
    private UnifiedRouteResult parseRouteResult(String response, List<Expert> experts) {
        try {
            // 尝试解析为 JSON
            UnifiedRouteResult result = JSON.parseObject(response, UnifiedRouteResult.class);

            // 验证路由结果的合法性
            if (result.getExpertCode() != null &&
                !isValidExpertCode(result.getExpertCode(), experts)) {
                log.warn("路由结果专家编码无效，使用默认专家: {}", result.getExpertCode());
                result.setExpertCode(getDefaultExpertCode(experts));
            }

            // 如果置信度低，使用默认专家
            if (result.getConfidence() == null || result.getConfidence() < 0.5) {
                result.setExpertCode(getDefaultExpertCode(experts));
                result.setConfidence(0.5);
            }

            log.info("统一路由成功: expertCode={}, flowCode={}, intent={}, confidence={}",
                result.getExpertCode(), result.getFlowCode(),
                result.getIntent(), result.getConfidence());

            return result;

        } catch (Exception e) {
            log.error("解析路由结果失败: {}", e.getMessage());
            return buildFallbackResult(experts);
        }
    }

    /**
     * 构建兜底结果
     */
    private UnifiedRouteResult buildFallbackResult(List<Expert> experts) {
        String defaultCode = getDefaultExpertCode(experts);

        // 获取默认专家的第一个流程
        List<ExpertFlow> flows = flowMapper.findByExpertCode(defaultCode);
        String flowCode = flows.isEmpty() ? null : flows.get(0).getFlowCode();

        UnifiedRouteResult result = new UnifiedRouteResult();
        result.setExpertCode(defaultCode);
        result.setFlowCode(flowCode);
        result.setIntent("general_chat");
        result.setConfidence(0.3);
        result.setPrompt("抱歉，我无法理解您的问题，请重新描述一下？");

        return result;
    }

    /**
     * 获取默认专家编码
     */
    private String getDefaultExpertCode(List<Expert> experts) {
        return experts.stream()
            .filter(e -> Boolean.TRUE.equals(e.getIsDefault()))
            .findFirst()
            .map(Expert::getExpertCode)
            .orElse(experts.get(0).getExpertCode());
    }

    private boolean isValidExpertCode(String code, List<Expert> experts) {
        return experts.stream()
            .anyMatch(e -> e.getExpertCode().equalsIgnoreCase(code.trim()));
    }
}
```

#### 5.2.3 统一路由流程图

```
                    用户问题
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  1. 加载数据                                                  │
│     - 获取所有启用的专家列表                                    │
│     - 获取每个专家下的流程列表                                   │
│     - 生成 Skills 描述                                        │
│     - 获取对话上下文（多轮对话时）                               │
└─────────────────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  2. 构建统一路由提示词                                         │
│     - 专家列表 + 路由提示                                       │
│     - 流程列表 + 流程提示                                       │
│     - Skills 描述（能力说明 + 触发词）                           │
│     - 输出格式要求                                            │
└─────────────────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  3. 单次 LLM 调用（替代原来的 3 次调用）                        │
│     ┌─────────────────────────────────────────────┐          │
│     │  LLM 一次决策：                             │          │
│     │  - 选择专家                                  │          │
│     │  - 选择流程                                  │          │
│     │  - 识别意图                                  │          │
│     │  - 抽取参数                                  │          │
│     └─────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  4. 解析并验证结果                                             │
│     - 验证专家/流程编码合法性                                   │
│     - 检查置信度                                               │
│     - 异常时使用兜底方案                                        │
└─────────────────────────────────────────────────────────────┘
                       │
                       ▼
                 UnifiedRouteResult
                       │
                       ▼
         ┌─────────────┴─────────────┐
         │                           │
   confidence >= 0.7          confidence < 0.7
         │                           │
         ▼                           ▼
   继续执行流程              返回追问提示，等待用户补充
```

### 5.3 混合模式执行策略

#### 5.3.1 预设骨架 + LLM 动态决策

**预设内容（人工编排）**：
- 流程模板（规定业务边界）
- 节点组合（规定标准处理步骤）
- 节点顺序（规定执行顺序）

**动态决策（LLM 辅助）**：
- 选择使用哪个流程
- 补充缺失的参数
- 遇到分支时做选择
- 处理异常情况

#### 5.3.2 执行流程示例

```
【客服专家 - 投诉处理流程】（预设骨架）

┌─────────────────────────────────────────────────────────────┐
│  开始                                                         │
│     ↓                                                         │
│  ┌───────────────────┐                                       │
│  │ 参数收集：订单号    │ ← LLM 辅助抽取，如缺失则反问           │
│  └───────────────────┘                                       │
│     ↓                                                         │
│  ┌───────────────────┐                                       │
│  │ 订单查询节点       │ ← 自动执行（Skills 执行器）             │
│  └───────────────────┘                                       │
│     ↓                                                         │
│  ┌───────────────────┐                                       │
│  │ 问题收集节点       │ ← LLM 辅助理解投诉内容                  │
│  └───────────────────┘                                       │
│     ↓                                                         │
│  ┌───────────────────┐                                       │
│  │ 投诉分类判断       │ ← LLM 根据预设条件分支做选择            │
│  │ (LLM 决策)        │                                       │
│  └───────────────────┘                                       │
│     ↓                    ↓                    ↓
│  [质量投诉]          [服务投诉]        [物流投诉]
│     ↓                    ↓                    ↓
│  ┌─────────┐         ┌─────────┐         ┌─────────┐
│  │质量处理 │         │服务处理 │         │物流处理 │
│  │ 节点    │         │ 节点    │         │ 节点    │
│  └─────────┘         └─────────┘         └─────────┘
│     ↓                    ↓                    ↓
│     └────────────────────┼────────────────────┘
│                          ↓
│                    ┌───────────┐
│                    │ 结束节点   │
│                    └───────────┘
└─────────────────────────────────────────────────────────────┘
```

#### 5.3.3 条件分支节点（支持 LLM 决策）

```java
/**
 * 条件分支节点
 * 支持预设条件和 LLM 动态决策两种模式
 */
@Slf4j
@Component
public class ConditionNodeExecutor implements NodeExecutor {

    @Autowired
    private QwenApiService qwenApiService;

    @Override
    public String getNodeType() {
        return "condition";
    }

    @Override
    public String getNodeName() {
        return "条件分支";
    }

    @Override
    public String getCategory() {
        return "logic";
    }

    @Override
    public String getDescription() {
        return "根据条件判断选择不同的执行分支";
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("如果", "根据", "判断", "分类", "区分");
    }

    @Override
    public NodeResult execute(FlowContext context, Map<String, Object> config) {
        // 获取分支配置
        List<Map<String, Object>> branches =
            (List<Map<String, Object>>) config.getOrDefault("branches", Collections.emptyList());

        String conditionMode = (String) config.getOrDefault("mode", "preset");

        if ("llm".equals(conditionMode)) {
            // 模式1：LLM 动态决策
            return executeLlmDecision(context, config, branches);
        } else {
            // 模式2：预设条件判断
            return executePresetDecision(context, config, branches);
        }
    }

    /**
     * LLM 动态决策分支
     * 将分支选项交给 LLM 选择
     */
    private NodeResult executeLlmDecision(FlowContext context, Map<String, Object> config,
                                          List<Map<String, Object>> branches) {
        String userMessage = (String) context.getParams().get("_current_message");

        // 构建分支选项描述
        StringBuilder options = new StringBuilder();
        for (int i = 0; i < branches.size(); i++) {
            Map<String, Object> branch = branches.get(i);
            options.append(String.format("[%d] %s: %s\n",
                i + 1,
                branch.get("name"),
                branch.get("description")));
        }

        // 构建 LLM 提示词
        String prompt = String.format("""
            用户问题：%s

            请根据问题内容，选择最合适的处理分支：

            %s

            请只返回一个数字（1-%d），表示选择的分支编号。
            """,
            userMessage, options.toString(), branches.size());

        try {
            List<Message> messages = List.of(new Message("user", prompt));
            String response = qwenApiService.chat(getApiKey(), "qwen-turbo", messages);

            // 解析分支编号
            int branchIndex = parseBranchIndex(response, branches.size());

            Map<String, Object> selectedBranch = branches.get(branchIndex);
            String nextNodeId = (String) selectedBranch.get("targetNode");

            log.info("LLM 决策分支: branch={}, nextNode={}",
                selectedBranch.get("name"), nextNodeId);

            return NodeResult.success("已选择分支: " + selectedBranch.get("name"))
                .setParams(Map.of(
                    "_selected_branch", selectedBranch.get("name"),
                    "_next_node", nextNodeId
                ));

        } catch (Exception e) {
            log.error("LLM 分支决策失败: {}", e.getMessage());
            // 失败时使用默认分支
            return useDefaultBranch(branches);
        }
    }

    /**
     * 预设条件判断分支
     */
    private NodeResult executePresetDecision(FlowContext context, Map<String, Object> config,
                                             List<Map<String, Object>> branches) {
        // 遍历预设条件，找到第一个匹配的分支
        for (Map<String, Object> branch : branches) {
            String condition = (String) branch.get("condition");
            String field = (String) config.get("checkField");
            Object expectedValue = branch.get("value");

            Object actualValue = context.getParams().get(field);
            if (actualValue != null && actualValue.equals(expectedValue)) {
                return NodeResult.success("条件匹配: " + branch.get("name"))
                    .setParams(Map.of("_next_node", branch.get("targetNode")));
            }
        }

        // 无匹配分支，使用默认分支
        return useDefaultBranch(branches);
    }

    private int parseBranchIndex(String response, int maxIndex) {
        try {
            // 提取数字
            String num = response.replaceAll("[^0-9]", "").trim();
            int index = Integer.parseInt(num) - 1;
            return Math.max(0, Math.min(index, maxIndex - 1));
        } catch (Exception e) {
            return 0; // 解析失败使用第一个分支
        }
    }

    private NodeResult useDefaultBranch(List<Map<String, Object>> branches) {
        if (branches.isEmpty()) {
            return NodeResult.fail("无可用分支", "NO_BRANCH");
        }

        Map<String, Object> defaultBranch = branches.get(0);
        return NodeResult.success("使用默认分支: " + defaultBranch.get("name"))
            .setParams(Map.of("_next_node", defaultBranch.get("targetNode")));
    }
}
```

### 5.4 默认专家设计

**设计理念**：
- 当所有专家都无法匹配用户问题时，使用默认专家作为兜底
- 默认专家走全局知识库检索，覆盖所有知识库内容
- 支持后台配置任意专家为默认专家（`is_default = 1`）

**数据库字段**：

```sql
ALTER TABLE ai_expert ADD COLUMN is_default TINYINT(1) DEFAULT 0 COMMENT '是否默认专家，0-否，1-是';

-- 同一时刻只能有一个默认专家，需要唯一约束
CREATE UNIQUE INDEX idx_expert_is_default ON ai_expert(is_default) WHERE is_default = 1;
```

**路由优先级**：
1. 优先尝试匹配具体专家
2. 匹配失败时使用默认专家
3. 无默认专家时使用第一个专家兜底

---

## 六、流程引擎设计

### 6.1 流程上下文

```java
/**
 * 流程上下文
 * 存储流程执行过程中的状态和数据
 */
@Data
public class FlowContext {

    private String sessionId;           // 会话ID
    private String userId;               // 用户ID
    private String expertCode;           // 专家编码
    private String flowCode;             // 流程编码
    private int currentNodeIndex;        // 当前节点索引
    private Map<String, Object> params; // 已收集的参数
    private List<String> history;       // 对话历史
    private String status;               // running/waiting/completed
    private Map<String, Object> metadata; // 元数据

    // 上下文过期时间（秒）
    private static final int EXPIRE_SECONDS = 1800;
}
```

### 6.2 上下文管理器

```java
/**
 * 上下文管理器
 * 负责 Redis 中对话上下文的读写和过期管理
 */
@Service
public class ContextManager {

    private static final String CONTEXT_KEY_PREFIX = "ai:chat:context:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 保存上下文到 Redis
     */
    public void saveContext(FlowContext context) {
        String key = CONTEXT_KEY_PREFIX + context.getUserId();
        redisTemplate.opsForValue().set(key, context, 1800, TimeUnit.SECONDS);
    }

    /**
     * 从 Redis 加载上下文
     */
    public FlowContext getContext(String userId) {
        String key = CONTEXT_KEY_PREFIX + userId;
        return (FlowContext) redisTemplate.opsForValue().get(key);
    }

    /**
     * 清除上下文
     */
    public void clearContext(String userId) {
        String key = CONTEXT_KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }

    /**
     * 刷新过期时间
     */
    public void refreshExpire(String userId) {
        String key = CONTEXT_KEY_PREFIX + userId;
        redisTemplate.expire(key, 1800, TimeUnit.SECONDS);
    }
}
```

### 6.3 流程引擎

```java
/**
 * 流程引擎
 * 负责解析流程定义、调度节点执行
 *
 * 【容错设计】
 * - 所有节点执行异常由引擎统一捕获和处理
 * - 单个节点失败不会导致整个流程终止
 * - 失败后可根据配置决定：跳过、重试或结束流程
 */
@Slf4j
@Service
public class FlowEngine {

    @Autowired
    private ContextManager contextManager;

    @Autowired
    private INodeRegistryService nodeRegistryService;

    @Autowired
    private ExpertFlowMapper flowMapper;

    /** 最大重试次数 */
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 执行流程
     */
    public NodeResult executeFlow(String userId, String userMessage) {
        FlowContext context = null;
        try {
            // 1. 加载上下文
            context = contextManager.getContext(userId);

            // 2. 无上下文则初始化
            if (context == null) {
                context = initContext(userId, userMessage);
            } else {
                context.getParams().put("_current_message", userMessage);
                context.getHistory().add(userMessage);
            }

            // 3. 加载流程定义
            ExpertFlow flow = flowMapper.findByExpertAndFlow(
                context.getExpertCode(),
                context.getFlowCode()
            );
            FlowDefinition definition = parseFlowDefinition(flow.getFlowData());

            // 4. 执行当前节点（带容错）
            return executeCurrentNode(context, definition, 0);

        } catch (Exception e) {
            log.error("流程执行异常: userId={}", userId, e);
            return handleFlowError(context, e, userMessage);
        }
    }

    /**
     * 执行当前节点（带重试和容错）
     */
    private NodeResult executeCurrentNode(FlowContext context, FlowDefinition definition, int retryCount) {
        List<FlowNode> nodes = definition.getNodes();

        // 检查是否所有节点已执行完毕
        if (context.getCurrentNodeIndex() >= nodes.size()) {
            context.setStatus("completed");
            contextManager.saveContext(context);
            return NodeResult.completed();
        }

        FlowNode node = nodes.get(context.getCurrentNodeIndex());

        // 尝试获取执行器
        Optional<NodeExecutor> executorOpt = nodeRegistryService.getExecutorSafe(node.getType());
        if (executorOpt.isEmpty()) {
            log.warn("节点类型不存在，跳过: nodeType={}", node.getType());
            context.setCurrentNodeIndex(context.getCurrentNodeIndex() + 1);
            contextManager.saveContext(context);
            return NodeResult.skip("节点[" + node.getType() + "]不存在，已跳过")
                    .setMetadata(Map.of("skippedNode", node.getType()));
        }

        try {
            // 执行节点
            NodeExecutor executor = executorOpt.get();
            NodeResult result = executor.execute(context, node.getData());

            // 处理执行结果
            return processNodeResult(context, definition, result);

        } catch (Exception e) {
            log.error("节点执行异常: nodeType={}, retryCount={}", node.getType(), retryCount, e);

            // 重试逻辑
            if (retryCount < MAX_RETRY_COUNT) {
                log.info("节点执行失败，进行第{}次重试: nodeType={}", retryCount + 1, node.getType());
                return executeCurrentNode(context, definition, retryCount + 1);
            }

            // 重试次数用完，处理失败
            return handleNodeError(context, definition, node, e);
        }
    }

    /**
     * 处理节点执行结果
     */
    private NodeResult processNodeResult(FlowContext context, FlowDefinition definition, NodeResult result) {
        if (result.isNeedMoreInput()) {
            // 需要更多输入，更新上下文并返回
            context.setStatus("waiting");
            contextManager.saveContext(context);
            return result;
        }

        if (result.isSuccess()) {
            // 成功，保存参数并进入下一节点
            if (result.getParams() != null) {
                context.getParams().putAll(result.getParams());
            }
            context.setCurrentNodeIndex(context.getCurrentNodeIndex() + 1);
            contextManager.saveContext(context);

            // 递归执行下一节点
            return executeCurrentNode(context, definition, 0);
        }

        // 节点执行失败（用户友好消息）
        return result;
    }

    /**
     * 处理节点执行错误
     */
    private NodeResult handleNodeError(FlowContext context, FlowDefinition definition,
                                       FlowNode node, Exception e) {
        String nodeType = node.getType();
        log.warn("节点执行失败，已达到最大重试次数: nodeType={}, error={}", nodeType, e.getMessage());

        // 根据节点类型决定处理策略
        String userMessage = switch (nodeType) {
            case "ocr" -> "抱歉，无法识别图片内容，请重新上传或手动输入相关信息";
            case "llm_call" -> "AI服务响应较慢，请稍后再试";
            case "knowledge_retrieval" -> "知识库检索服务暂时不可用，我将继续为您解答";
            case "tool_call" -> "调用服务遇到问题，已为您跳过此步骤";
            default -> "处理您的请求时遇到问题，已为您跳过";
        };

        // 跳过当前节点，继续流程
        context.setCurrentNodeIndex(context.getCurrentNodeIndex() + 1);
        contextManager.saveContext(context);

        NodeResult skipResult = NodeResult.skip("节点执行失败: " + nodeType)
                .setOutput(userMessage)
                .setMetadata(Map.of(
                    "originalNode", nodeType,
                    "error", e.getMessage(),
                    "retryExhausted", true
                ));

        // 继续执行下一节点
        return executeCurrentNode(context, definition, 0);
    }

    /**
     * 处理流程级别错误
     */
    private NodeResult handleFlowError(FlowContext context, Exception e, String userMessage) {
        // 记录错误日志
        log.error("流程执行异常: {}", e.getMessage(), e);

        // 保留上下文，让用户可以继续对话
        if (context != null) {
            context.setStatus("error");
            context.getParams().put("_last_error", e.getMessage());
            contextManager.saveContext(context);
        }

        // 返回友好错误消息
        String userMessage;
        if (e instanceof BusinessException) {
            userMessage = e.getMessage();
        } else if (e instanceof java.net.SocketTimeoutException ||
                   e instanceof java.net.ConnectException) {
            userMessage = "网络连接不稳定，请检查网络后重试";
        } else if (e instanceof com.alibaba.fastjson2.JSONException) {
            userMessage = "系统数据解析异常，请稍后再试";
        } else {
            userMessage = "系统处理时遇到问题，请稍后再试或重新描述您的问题";
        }

        return NodeResult.fail(userMessage, "FLOW_ERROR")
                .setMetadata(Map.of("originalError", e.getClass().getSimpleName()));
    }

    /**
     * 初始化流程上下文
     */
    private FlowContext initContext(String userId, String userMessage) {
        FlowContext context = new FlowContext();
        context.setSessionId(UUIDUtil.uuid());
        context.setUserId(userId);
        context.setStatus("running");
        context.setParams(new HashMap<>());
        context.setHistory(new ArrayList<>());
        context.getHistory().add(userMessage);
        context.setCurrentNodeIndex(0);
        contextManager.saveContext(context);
        return context;
    }
}
```

---

## 六点五、动态节点规划

### 6.5.1 设计背景

在原有设计中，当专家未设置流程或流程节点为空时，系统会直接结束流程并返回"流程已结束"的响应，用户体验较差。为解决这一问题，引入了**LLM 驱动的动态节点规划**机制。

### 6.5.2 核心设计

```
用户消息 → 意图分析 → LLM 节点匹配 → 动态执行计划生成 → 执行节点
                ↓
        可用节点注册表
        (SkillRegistry)
```

#### 触发条件

| 触发场景 | 说明 |
|---------|------|
| 流程定义为空 | 数据库中未找到对应流程 |
| 流程节点为空 | flowData 解析后 nodes 列表为空 |
| 意图置信度低 | 路由阶段意图识别置信度低于 0.7 |
| 显式标记 | 上下文 metadata 中标记 need_dynamic_planning=true |

### 6.5.3 规划策略

#### 预规划策略（Eager）

一次性规划完整路径，减少 LLM 调用次数，执行效率高。

```
1. LLM 分析用户意图
2. 根据可用节点生成完整执行序列
3. 按顺序执行所有规划的节点
4. 返回最终结果
```

#### 按需规划策略（Lazy）

每个节点执行后动态判断下一步，更灵活，适用于复杂对话场景。

```
1. LLM 分析当前状态
2. 决定下一步节点
3. 执行该节点
4. 重复步骤 1-3 直到完成
```

### 6.5.4 核心接口

```java
/**
 * 节点规划服务接口
 */
public interface INodePlannerService {

    /**
     * 根据用户意图和上下文，生成节点执行计划
     */
    NodeExecutionPlan plan(String userMessage, FlowContext context);

    /**
     * 检查是否需要动态规划
     */
    boolean shouldPlan(FlowContext context);

    /**
     * 确定规划策略
     */
    String determineStrategy(FlowContext context);
}
```

### 6.5.5 节点执行计划

```java
/**
 * 节点执行计划
 */
@Data
public class NodeExecutionPlan {

    private String planId;              // 计划ID
    private String strategy;           // 策略：eager/lazy
    private String reason;             // 规划原因
    private double confidence;          // 置信度
    private List<PlannedNode> nodes;   // 节点序列

    @Data
    public static class PlannedNode {
        private String nodeType;       // 节点类型
        private String nodeName;       // 节点名称
        private Map<String, Object> config;  // 节点配置
        private int order;              // 执行顺序
        private String reason;          // 为什么需要这个节点
        private boolean required;       // 是否必需
    }
}
```

### 6.5.6 LLM 规划提示词

```
# 智能节点规划任务

你是一个专业的 AI 流程规划专家。根据用户的问题和当前上下文，
从可用节点中选择最合适的节点组合，生成最优的执行计划。

## 可用节点列表
- [llm_call] LLM调用: 调用AI大模型生成回答
- [knowledge_retrieval] 知识检索: 从知识库检索相关信息
- [collect] 参数收集: 收集用户输入参数
- [execute] 业务执行: 执行数据库查询等业务操作

## 输出格式
{
  "planId": "plan_001",
  "strategy": "eager",
  "reason": "规划理由说明",
  "confidence": 0.85,
  "nodes": [
    {
      "nodeType": "knowledge_retrieval",
      "order": 1,
      "reason": "需要检索相关知识",
      "required": true
    },
    {
      "nodeType": "llm_call",
      "order": 2,
      "reason": "基于检索结果生成回答",
      "required": true
    }
  ]
}
```

### 6.5.7 流程引擎集成

```java
/**
 * 流程引擎中的动态规划集成
 */

// 1. 检查是否需要动态规划
if (needsDynamicPlanning(definition, context)) {
    log.info("流程为空，触发动态规划");
    return executeDynamicPlan(context);
}

// 2. 判断是否需要动态规划
private boolean needsDynamicPlanning(FlowDefinition definition, FlowContext context) {
    if (nodePlannerService == null) {
        return false;  // 动态规划服务未启用
    }
    if (definition.getNodes() == null || definition.getNodes().isEmpty()) {
        return true;  // 节点为空
    }
    return false;
}

// 3. 执行动态规划流程
private NodeResult executeDynamicPlan(FlowContext context) {
    NodeExecutionPlan plan = nodePlannerService.plan(context.getCurrentMessage(), context);
    if (plan == null || plan.isEmpty()) {
        return executeDefaultLLMCall(context);  // 兜底方案
    }
    return executeEagerPlan(context, plan);  // 执行预规划
}
```

### 6.5.8 兜底机制

当动态规划失败时，系统提供多层兜底：

| 兜底层级 | 触发条件 | 处理方式 |
|---------|---------|---------|
| LLM 规划失败 | 解析失败或返回空 | 使用 FALLBACK_PLAN（单节点 llm_call） |
| 节点执行失败 | 必需节点异常 | 返回友好错误提示 |
| 无执行器 | nodeType 不存在 | 跳过该节点，继续后续节点 |

### 6.5.9 Backward Compatible

- **完全向后兼容**：有节点的流程保持原有逻辑不变
- **可选功能**：`INodePlannerService` 使用 `@Autowired(required = false)`，未配置时不启用
- **只影响空流程**：空流程或无节点时启用动态规划，不影响现有功能

### 6.5.10 模块结构

```
com.aip.expert/
├── dto/
│   ├── NodeExecutionPlan.java       # 节点执行计划
│   └── PlannedNode.java             # 规划节点定义
├── service/
│   ├── INodePlannerService.java     # 节点规划服务接口
│   └── impl/
│       └── NodePlannerService.java   # LLM驱动的动态规划实现
└── service/impl/
    └── FlowEngineService.java        # 已集成动态规划逻辑
```

### 6.5.11 配置项

| 配置项 | 说明 | 默认值 |
|-------|------|-------|
| `aip.dynamic-planning.enabled` | 是否启用动态规划 | `true` |
| `aip.dynamic-planning.timeout-ms` | 规划超时时间 | `10000` |
| `aip.dynamic-planning.confidence-threshold` | 意图置信度阈值 | `0.7` |

---

## 七、对话接口设计

### 7.1 统一对话接口

```java
/**
 * 专家对话控制器
 * 统一的 AI 智能问答入口
 *
 * 【错误处理设计】
 * - 所有异常统一捕获，返回用户友好的错误消息
 * - SSE 连接断开不影响流程状态
 * - 错误时保留上下文，用户可重试
 */
@Slf4j
@RestController
@RequestMapping("/api/expert/chat")
public class ExpertChatController {

    @Autowired
    private FlowEngine flowEngine;

    @Autowired
    private ContextManager contextManager;

    /** SSE 超时时间：5分钟 */
    private static final long SSE_TIMEOUT = 300L * 1000;

    /**
     * 流式对话接口
     */
    @GetMapping("/stream")
    public SseEmitter chatStream(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId
    ) {
        if (message == null || message.isBlank()) {
            throw new BusinessException("问题不能为空");
        }

        String userId = getCurrentUserId();
        log.info("收到对话请求: userId={}, sessionId={}, message={}", userId, sessionId, message);

        // 1. 创建 SSE emitter
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitter.onCompletion(() -> log.debug("SSE连接完成: userId={}", userId));
        emitter.onTimeout(() -> log.debug("SSE连接超时: userId={}", userId));
        emitter.onError(e -> log.debug("SSE连接错误: userId={}, error={}", userId, e.getMessage()));

        // 2. 异步处理（使用线程池避免阻塞）
        asyncExecutor.execute(() -> {
            try {
                // 2.1 执行流程
                NodeResult result = flowEngine.executeFlow(userId, message);

                // 2.2 发送结果（分块发送，模拟流式效果）
                sendResultInChunks(emitter, result);

                // 2.3 检查是否需要继续输入
                if ("waiting".equals(result.getStatus()) || result.isNeedMoreInput()) {
                    sendWaitingPrompt(emitter, result.getPrompt());
                }

                // 2.4 标记流程完成
                if ("completed".equals(result.getStatus()) || !result.isNeedMoreInput()) {
                    sendCompletion(emitter);
                }

                emitter.complete();

            } catch (Exception e) {
                handleStreamError(emitter, userId, e);
            }
        });

        return emitter;
    }

    /**
     * 分块发送结果（模拟流式效果）
     */
    private void sendResultInChunks(SseEmitter emitter, NodeResult result) {
        String output = result.getOutput();
        if (output == null || output.isEmpty()) {
            return;
        }

        // 分块发送，每块最多100字符
        int chunkSize = 100;
        for (int i = 0; i < output.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, output.length());
            String chunk = output.substring(i, end);

            try {
                String data = String.format("{\"type\":\"content\",\"content\":\"%s\"}", escapeJson(chunk));
                emitter.send(SseEmitter.event().name("message").data(data));
                Thread.sleep(20);  // 模拟打字效果
            } catch (Exception e) {
                log.debug("SSE发送中断: userId={}", getCurrentUserId());
                return;
            }
        }
    }

    /**
     * 发送等待提示
     */
    private void sendWaitingPrompt(SseEmitter emitter, String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            return;
        }
        try {
            String data = String.format("{\"type\":\"waiting\",\"prompt\":\"%s\"}", escapeJson(prompt));
            emitter.send(SseEmitter.event().name("waiting").data(data));
        } catch (Exception e) {
            log.debug("SSE发送等待提示失败", e);
        }
    }

    /**
     * 发送完成标记
     */
    private void sendCompletion(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data("{\"type\":\"done\"}"));
        } catch (Exception e) {
            log.debug("SSE发送完成标记失败", e);
        }
    }

    /**
     * 处理流式错误
     */
    private void handleStreamError(SseEmitter emitter, String userId, Exception e) {
        log.error("对话处理异常: userId={}", userId, e);

        String errorMessage = getUserFriendlyError(e);

        try {
            // 发送友好错误消息
            String data = String.format("{\"type\":\"error\",\"message\":\"%s\"}", escapeJson(errorMessage));
            emitter.send(SseEmitter.event().name("error").data(data));
            emitter.complete();
        } catch (Exception ex) {
            log.debug("SSE发送错误失败: userId={}", userId);
        }
    }

    /**
     * 获取用户友好的错误消息
     */
    private String getUserFriendlyError(Exception e) {
        if (e instanceof BusinessException) {
            return e.getMessage();
        }
        if (e instanceof java.net.SocketTimeoutException) {
            return "请求超时，请稍后再试";
        }
        if (e instanceof java.net.ConnectException) {
            return "网络连接不稳定，请检查网络后重试";
        }
        if (e instanceof OutOfMemoryError) {
            return "服务负载过高，请稍后再试";
        }
        return "系统处理时遇到问题，请稍后再试或重新描述您的问题";
    }

    /**
     * JSON字符串转义
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 获取当前上下文状态
     */
    @GetMapping("/context")
    public Result<FlowContext> getContext() {
        String userId = getCurrentUserId();
        FlowContext context = contextManager.getContext(userId);
        return Result.ok(context);
    }

    /**
     * 清除对话上下文
     */
    @DeleteMapping("/context")
    public Result<Void> clearContext() {
        String userId = getCurrentUserId();
        contextManager.clearContext(userId);
        return Result.ok();
    }
}
```

### 7.2 后台管理接口

```java
/**
 * 专家管理控制器
 */
@RestController
@RequestMapping("/api/expert")
public class ExpertController {

    @Autowired
    private IExpertService expertService;

    @GetMapping("/list")
    public Result<List<Expert>> list() {
        return Result.ok(expertService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Expert> getById(@PathVariable Long id) {
        return Result.ok(expertService.getById(id));
    }

    @PostMapping
    public Result<Expert> create(@RequestBody @Valid CreateExpertRequest request) {
        return Result.ok(expertService.create(request));
    }

    @PutMapping
    public Result<Expert> update(@RequestBody @Valid UpdateExpertRequest request) {
        return Result.ok(expertService.update(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        expertService.delete(id);
        return Result.ok();
    }
}

/**
 * 流程管理控制器
 */
@RestController
@RequestMapping("/api/expert/flow")
public class ExpertFlowController {

    @Autowired
    private IExpertFlowService flowService;

    @GetMapping("/nodes")
    public Result<List<NodeDefinitionDTO>> getAvailableNodes() {
        return Result.ok(nodeRegistryService.getAllNodeDefinitions());
    }

    @GetMapping("/{expertId}")
    public Result<ExpertFlow> getFlow(@PathVariable Long expertId) {
        return Result.ok(flowService.getFlowByExpertId(expertId));
    }

    @PostMapping("/save")
    public Result<ExpertFlow> saveFlow(@RequestBody @Valid SaveFlowRequest request) {
        return Result.ok(flowService.saveFlow(request));
    }

    @PostMapping("/{expertId}/publish")
    public Result<Void> publishFlow(@PathVariable Long expertId) {
        flowService.publishFlow(expertId);
        return Result.ok();
    }

    @PostMapping("/validate")
    public Result<Map<String, Object>> validateFlow(@RequestBody String flowData) {
        return Result.ok(flowService.validateFlow(flowData));
    }
}
```

---

## 八、前端模块设计（简化版）

### 8.1 前端文件结构 `src/views/flow/`

> 简化自 `src/views/expert/`，移除专家相关页面

```
src/views/flow/
├── template/
│   ├── index.vue          # 流程模板列表页
│   └── form.vue           # 模板表单
├── editor/
│   ├── index.vue         # 流程编排主页面
│   └── components/
│       ├── FlowCanvas.vue         # 画布组件
│       ├── NodePanel.vue          # 节点面板
│       ├── NodeRenderer.vue       # 节点渲染
│       ├── ConfigPanel.vue        # 配置面板
│       └── nodes/
│           ├── StartNode.vue
│           ├── EndNode.vue
│           ├── CollectNode.vue
│           └── LLMCallNode.vue
└── chat/
    └── index.vue          # AI对话测试页
```

### 8.2 API 接口定义 `src/api/flow.js`

> 简化自 `src/api/expert.js`，移除专家相关接口

```javascript
import request from './request'

// ==================== 节点定义 ====================
export const getNodeDefinitions = () => request.get('/flow/nodes')

// ==================== 流程模板管理 ====================
export const getFlowTemplateList = (params) => request.get('/flow/template', { params })
export const getFlowTemplateById = (id) => request.get(`/flow/template/${id}`)
export const createFlowTemplate = (data) => request.post('/flow/template', data)
export const updateFlowTemplate = (data) => request.put('/flow/template', data)
export const deleteFlowTemplate = (id) => request.delete(`/flow/template/${id}`)
export const updateFlowTemplateStatus = (id, status) => request.put(`/flow/template/${id}/status`, { status })

// ==================== 流程编排 ====================
export const getFlowById = (id) => request.get(`/flow/detail/${id}`)
export const saveFlow = (data) => request.post('/flow/save', data)
export const publishFlow = (id) => request.post(`/flow/${id}/publish`)
export const validateFlow = (data) => request.post('/flow/validate', data)

// ==================== 对话接口 ====================
export const chatStream = (message, sessionId) => {
  return request.get('/flow/chat/stream', {
    params: { message, sessionId }
  }, { responseType: 'stream' })
}
export const getChatContext = () => request.get('/flow/chat/context')
export const clearChatContext = () => request.delete('/flow/chat/context')
```

### 8.3 流程编辑器组件

```vue
<!-- FlowEditor.vue - 流程编排主页面 -->
<template>
  <div class="flow-editor">
    <div class="editor-header">
      <el-button @click="goBack">返回</el-button>
      <span class="flow-name">{{ templateName }} - 流程编排</span>
      <el-button type="primary" @click="saveFlow">保存</el-button>
      <el-button type="success" @click="publishFlow">发布</el-button>
    </div>

    <div class="editor-body">
      <!-- 节点面板 -->
      <NodePanel class="node-panel" @drag-start="onDragStart" />

      <!-- 画布 -->
      <FlowCanvas
        class="flow-canvas"
        :nodes="nodes"
        :edges="edges"
        @drop="onDrop"
        @node-click="onNodeClick"
        @edge-click="onEdgeClick"
      />

      <!-- 配置面板 -->
      <ConfigPanel
        class="config-panel"
        v-if="selectedNode"
        :node="selectedNode"
        :node-definition="getNodeDefinition(selectedNode.type)"
        @update="onUpdateNode"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getFlowById, saveFlow, publishFlow, getNodeDefinitions } from '@/api/flow'
import NodePanel from './components/NodePanel.vue'
import FlowCanvas from './components/FlowCanvas.vue'
import ConfigPanel from './components/ConfigPanel.vue'

const route = useRoute()
const templateId = route.params.templateId
const templateName = ref('')
const nodes = ref([])
const edges = ref([])
const selectedNode = ref(null)
const nodeDefinitions = ref([])

onMounted(async () => {
  // 加载节点定义
  const defRes = await getNodeDefinitions()
  nodeDefinitions.value = defRes.data || []

  // 加载流程数据
  if (templateId) {
    const flowRes = await getFlowById(templateId)
    if (flowRes.data) {
      templateName.value = flowRes.data.templateName || ''
      const flowData = JSON.parse(flowRes.data.flowData || '{}')
      nodes.value = flowData.nodes || []
      edges.value = flowData.edges || []
    }
  }
})

const onDragStart = (event, nodeDef) => {
  event.dataTransfer.setData('nodeType', nodeDef.type)
  event.dataTransfer.setData('nodeDef', JSON.stringify(nodeDef))
}

const onDrop = (event, position) => {
  const nodeType = event.dataTransfer.getData('nodeType')
  const nodeDef = JSON.parse(event.dataTransfer.getData('nodeDef'))

  const newNode = {
    id: `node_${Date.now()}`,
    type: nodeType,
    position: position,
    data: {}
  }
  nodes.value.push(newNode)
}

const onNodeClick = (node) => {
  selectedNode.value = node
}

const getNodeDefinition = (type) => {
  return nodeDefinitions.value.find(d => d.type === type)
}
</script>
```

---

## 九、Spring AI Alibaba 集成要点

### 9.1 依赖引入

```xml
<!-- 在 pom.xml 中添加 -->
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
    <version>2024.0.1.0</version>
</dependency>
```

### 9.2 配置项

```yaml
spring:
  ai:
    alibaba:
      dashscope:
        api-key: ${DASHSCOPE_API_KEY}  # 从环境变量或配置中心获取
        model: qwen-plus
        temperature: 0.1
```

### 9.3 ChatClient 使用示例

```java
@Autowired
private ChatClient chatClient;

public String chat(String systemPrompt, String userMessage) {
    return chatClient.prompt()
        .system(systemPrompt)
        .user(userMessage)
        .call()
        .content();
}

public void streamChat(String userMessage, Consumer<String> onChunk) {
    chatClient.prompt()
        .user(userMessage)
        .stream()
        .content()
        .subscribe(onChunk);
}
```

---

## 十、AI 模型配置（多模型支持）

### 9.1 设计背景

为支持多 AI 模型提供商（通义千问、DeepSeek、智谱GLM、OpenAI 等），平台采用**数据库配置 + 动态切换**的方案，无需修改代码即可切换不同模型。

### 9.2 现有模型配置表 `t_ai_model_config`

平台已有完整的 AI 模型配置体系，存储在 `t_ai_model_config` 表中：

```sql
CREATE TABLE t_ai_model_config (
    id              VARCHAR(36) PRIMARY KEY COMMENT '主键ID(UUID)',
    f_name          VARCHAR(100) NOT NULL COMMENT '模型名称（显示用）',
    f_provider      VARCHAR(50) NOT NULL COMMENT '提供商：qwen/deepseek/zhipu/openai',
    f_api_url       VARCHAR(500) COMMENT 'API地址（可选，使用默认地址）',
    f_api_key       VARCHAR(255) COMMENT 'API密钥（加密存储）',
    f_model_name    VARCHAR(100) NOT NULL COMMENT '模型标识（如 deepseek-chat、qwen-plus）',
    f_temperature   DECIMAL(3,2) DEFAULT 0.70 COMMENT '温度参数（0-1）',
    f_max_tokens    INT DEFAULT 2000 COMMENT '最大生成Token数',
    f_enabled       TINYINT(1) DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    f_is_default    TINYINT(1) DEFAULT 0 COMMENT '是否为默认模型',
    f_sort_order    INT DEFAULT 0 COMMENT '排序权重',
    f_description   VARCHAR(500) COMMENT '模型描述',
    f_create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
    f_update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0 COMMENT '逻辑删除'
) COMMENT 'AI大模型配置表';
```

### 9.3 支持的模型提供商

| 提供商 | Provider 值 | 默认 API 地址 | 模型示例 |
|-------|------------|--------------|---------|
| 通义千问 | `qwen` / `dashscope` | `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` | qwen-plus, qwen-turbo, qwen-max |
| DeepSeek | `deepseek` | `https://api.deepseek.com/v1/chat/completions` | deepseek-chat, deepseek-coder |
| 智谱GLM | `zhipu` | `https://open.bigmodel.cn/api/paas/v4/chat/completions` | glm-4, glm-4-flash |
| OpenAI | `openai` | `https://api.openai.com/v1/chat/completions` | gpt-4, gpt-3.5-turbo |

### 9.4 统一 LLM 调用服务 `UnifiedLlmService`

`UnifiedLlmService` 是统一的 LLM 调用封装，支持多模型动态切换：

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class UnifiedLlmService {

    private final IAiModelConfigService aiModelConfigService;

    /**
     * 流式调用 LLM
     * @param config  模型配置（从数据库读取）
     * @param messages 对话消息列表
     * @param onChunk 每个chunk的回调
     */
    public void streamChat(AiModelConfig config, List<Message> messages,
                           Consumer<String> onChunk, Runnable onComplete, Consumer<String> onError);

    /**
     * 非流式调用 LLM
     */
    public String chat(AiModelConfig config, List<Message> messages);
}
```

### 9.5 LLM 节点配置

在流程编排的 LLM 节点中，可选择使用哪个模型：

```json
{
  "nodeCode": "llm_call",
  "config": {
    "modelId": "uuid-xxx-xxx",    // 模型ID（留空使用默认模型）
    "systemPrompt": "你是一个专业的客服助手",
    "temperature": 0.7
  }
}
```

### 9.6 模型选择流程

```
┌─────────────────────────────────────────────────────────────┐
│                    模型选择流程                              │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. 检查节点配置中是否指定 modelId                             │
│    ├── 有 → 从数据库读取指定模型配置                           │
│    └── 无 → 使用默认模型                                     │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. 从 t_ai_model_config 表读取模型配置                        │
│    - API 地址、API Key（解密）、模型标识、温度参数等            │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. 调用 UnifiedLlmService 执行请求                            │
│    - 自动适配不同提供商的 API 格式                            │
└─────────────────────────────────────────────────────────────┘
```

### 9.7 前端操作步骤

1. **配置模型**：进入 **系统管理 → 大模型配置**
   - 点击"新建"添加新模型
   - 选择提供商（DeepSeek / 通义千问 / 智谱 / OpenAI）
   - 填写 API Key、模型标识、温度参数等
   - 保存后点击"设为默认"设置默认模型

2. **创建流程模板**：进入 **流程管理 → 流程模板**
   - 点击"新建模板"，填写模板名称、编码、描述
   - 设置匹配模式（关键词或正则）
   - 保存后进入"编排"页面

3. **编排流程**：进入 **流程管理 → 流程模板 → 编排**
   - 从左侧节点面板拖拽节点到画布
   - 点击节点配置参数（如选择 AI 模型）
   - 连接节点形成流程
   - 保存并发布

4. **切换模型**：在模型配置页面修改默认模型，所有使用默认模型的流程自动生效

### 9.8 与现有系统的集成

| 组件 | 说明 |
|-----|------|
| `AiModelConfig` | 实体类，对应 `t_ai_model_config` 表 |
| `IAiModelConfigService` | 模型配置服务接口 |
| `UnifiedLlmService` | 统一 LLM 调用服务 |
| `LLMCallNodeExecutor` | 流程节点执行器，调用 `UnifiedLlmService` |

### 9.9 优势

| 特性 | 说明 |
|-----|------|
| **多模型支持** | 支持通义千问、DeepSeek、智谱、OpenAI 等多种模型 |
| **动态切换** | 在管理页面修改配置，无需重启服务 |
| **统一封装** | `UnifiedLlmService` 屏蔽不同提供商的 API 差异 |
| **安全存储** | API Key 加密存储，防止泄露 |
| **灵活配置** | 每个节点可独立选择模型，或使用全局默认 |

---

## 十、开放平台 API 凭证

### 10.1 概述

开放平台为外部系统提供 AI 能力调用接口，支持：
- **API 调用**：外部系统通过 API Key/Secret 鉴权，调用 AI 对话能力
- **智答助手嵌入**：通过嵌入代码将 AI 助手小部件集成到外部网站

**设计原则**：
- 外部调用统一使用 AI 中台配置的**默认模型**，不开放模型选择
- 凭证可关联**允许调用的专家列表**
- 支持**流量控制**（QPS、每日/每月额度）

### 10.2 表结构

```sql
CREATE TABLE t_sys_api_credential (
    id                      VARCHAR(36) PRIMARY KEY COMMENT '主键ID(UUID)',
    f_name                  VARCHAR(100) NOT NULL COMMENT '凭证名称',
    f_app_id                VARCHAR(50) NOT NULL UNIQUE COMMENT '应用ID',
    f_api_key               VARCHAR(64) NOT NULL UNIQUE COMMENT 'API Key（AK）',
    f_api_secret            VARCHAR(128) NOT NULL COMMENT 'API Secret（SK，加密存储）',
    f_api_secret_last_four  VARCHAR(4) COMMENT '密钥后四位',
    f_allowed_experts       JSON COMMENT '允许调用的专家编码列表',
    f_rate_limit_qps        INT DEFAULT 10 COMMENT '每秒最大请求数',
    f_daily_quota           BIGINT DEFAULT -1 COMMENT '每日额度，-1=不限',
    f_monthly_quota         BIGINT DEFAULT -1 COMMENT '每月额度，-1=不限',
    f_daily_reset_date      DATE COMMENT '每日计数重置日期',
    f_monthly_reset_date    VARCHAR(7) COMMENT '每月计数重置日期',
    f_total_calls           BIGINT DEFAULT 0 COMMENT '历史总调用次数',
    f_today_calls           INT DEFAULT 0 COMMENT '今日调用次数',
    f_monthly_calls         BIGINT DEFAULT 0 COMMENT '本月调用次数',
    f_last_called_at        DATETIME COMMENT '最后调用时间',
    f_status                TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    f_expire_time           DATETIME COMMENT '过期时间，NULL=永不过期',
    deleted                 TINYINT DEFAULT 0 COMMENT '逻辑删除',
    f_create_time           DATETIME DEFAULT CURRENT_TIMESTAMP,
    f_update_time           DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    f_creator               VARCHAR(36) COMMENT '创建人ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台API凭证表';
```

### 10.3 鉴权流程

```
┌─────────────────────────────────────────────────────────────┐
│                 Open API 鉴权流程                            │
└─────────────────────────────────────────────────────────────┘

1. 解析凭证
   Authorization: Bearer <api_key>:<api_secret>

2. 验证凭证有效性
   ├── API Key 不存在 → 401 Unauthorized
   ├── Secret 不匹配 → 401 Unauthorized
   ├── 状态=禁用 → 401 Unauthorized
   └── 已过期 → 403 Forbidden

3. 权限校验（专家）
   └── f_allowed_experts 是否包含请求的专家编码
       ├── 包含 → 通过
       └── 不包含 → 403 Forbidden

4. 流量控制
   ├── QPS 超限 → 429 Too Many Requests
   ├── 今日额度超限 → 429 Too Many Requests
   └── 每月额度超限 → 429 Too Many Requests

5. 执行请求（使用 AI 中台默认模型）

6. 更新统计
   ├── f_total_calls++
   ├── f_today_calls++
   └── f_monthly_calls++
```

### 10.4 API 接口

#### 管理端 API（内部管理员）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/admin/credentials` | 创建凭证 |
| `GET` | `/admin/credentials` | 分页查询 |
| `GET` | `/admin/credentials/{id}` | 详情 |
| `PUT` | `/admin/credentials/{id}` | 更新 |
| `DELETE` | `/admin/credentials/{id}` | 删除 |
| `POST` | `/admin/credentials/{id}/reset-secret` | 重置密钥 |
| `PUT` | `/admin/credentials/{id}/status` | 启用/禁用 |
| `GET` | `/admin/credentials/{id}/stats` | 统计 |

#### 开放 API（外部系统调用）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/open/v1/chat/completions` | AI 对话（使用默认模型） |
| `POST` | `/open/v1/chat/completions/stream` | AI 对话（SSE流式） |
| `POST` | `/open/v1/knowledge/search` | 知识库检索 |
| `GET` | `/open/v1/experts` | 获取可用的专家列表 |
| `GET` | `/open/v1/widget/config` | 获取嵌入配置 |

### 10.5 核心接口定义

#### 1. AI 对话 `/open/v1/chat/completions`

```
Headers:
  Authorization: Bearer <api_key>:<api_secret>

Body:
{
  "expertId": "customer_service",    // 专家编码（可选）
  "messages": [
    {"role": "user", "content": "你好"}
  ],
  "stream": false                      // 是否流式
}

Response:
{
  "code": 200,
  "data": {
    "id": "msg_xxx",
    "content": "你好，有什么可以帮你的？",
    "usage": {
      "promptTokens": 10,
      "completionTokens": 20,
      "totalTokens": 30
    }
  }
}
```

#### 2. 获取专家列表 `/open/v1/experts`

```
Headers:
  Authorization: Bearer <api_key>:<api_secret>

Response:
{
  "code": 200,
  "data": {
    "experts": [
      {"id": "exp_001", "code": "customer_service", "name": "客服助手", "description": "..."},
      {"id": "exp_002", "code": "tech_support", "name": "技术支持", "description": "..."}
    ]
  }
}
```

#### 3. 嵌入配置 `/open/v1/widget/config`

```
GET /open/v1/widget/config?app_id=xxx

Response:
{
  "code": 200,
  "data": {
    "widgetToken": "wt_xxx",
    "serverUrl": "wss://ai.xxx.com/open/v1/widget/connect",
    "experts": [...],
    "theme": {
      "primaryColor": "#1890ff",
      "position": "right",
      "zIndex": 9999
    }
  }
}
```

### 10.6 流量控制

| 限制类型 | 说明 | 配置 |
|----------|------|------|
| `rate_limit_qps` | 每秒最大请求数 | 默认 10 |
| `daily_quota` | 每日调用额度 | -1=不限 |
| `monthly_quota` | 每月调用额度 | -1=不限 |

**超限响应**：
```json
{
  "code": 429,
  "message": "今日额度已用尽，请明日再试"
}
```

### 10.7 与现有系统的集成

| 组件 | 说明 |
|------|------|
| `SysApiCredential` | 实体类，对应 `t_sys_api_credential` 表 |
| `ISysApiCredentialService` | 凭证管理服务接口 |
| `OpenApiAuthService` | 开放 API 鉴权服务 |
| `RateLimitService` | 流量控制服务（基于 Redis） |
| `OpenApiController` | 开放 API 控制器 |

---

## 十一、部署说明

### 11.1 环境要求

- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- Elasticsearch 8.x（可选，用于知识库检索）

### 11.2 部署步骤

1. **初始化数据库**
   - 执行数据库初始化脚本 `v3.0__expert_system.sql`
   - 包含 4 张核心表和初始化数据

2. **配置 AI 接口**
   - 配置 DashScope API Key
   - 或使用现有 QwenApiService（项目已有实现）

3. **启动应用**
   - 无需重启即可使用新功能
   - 节点通过 `@Component` 自动注册

4. **前端部署**
   - 新增路由配置
   - 构建并部署静态资源

---

## 十二、实施计划

### 阶段一：数据库设计与初始化（1天）
- [ ] 创建专家表、流程表、节点表、编排表
- [ ] 编写初始化 SQL 脚本
- [ ] 初始化默认专家和流程模板

### 阶段二：后端核心框架（3天）
- [ ] 创建 `com.aip.expert` 模块
- [ ] 实现实体类和 Mapper
- [ ] 实现基础 CRUD 接口
- [ ] 实现节点执行器框架
- [ ] 实现流程引擎

### 阶段三：AI 路由层（2天）
- [ ] 实现专家路由服务
- [ ] 实现流程路由服务
- [ ] 实现参数抽取服务
- [ ] 实现对话上下文管理

### 阶段四：前台管理页面（2天）
- [ ] 专家管理页面
- [ ] 流程编排编辑器
- [ ] 节点配置面板
- [ ] 路由配置

### 阶段五：集成测试（2天）
- [ ] 单元测试
- [ ] 集成测试
- [ ] 性能测试
- [ ] 文档编写

---

## 十三、附录

### 13.1 术语表

| 术语 | 说明 |
|------|------|
| 流程模板 FlowTemplate | 独立的业务流程模板，如报销模板、请假模板 |
| 节点 Node | 流程中的单个处理步骤 |
| 意图路由 IntentRoute | 根据用户意图选择处理方式（固定模板/动态规划） |
| 固定模板 FixedTemplate | 预设的复杂业务流程，需按步骤执行 |
| 动态规划 DynamicPlan | LLM 自动选择节点组合，适用于简单场景 |
| 路由类型 RouteType | FIXED_TEMPLATE / DYNAMIC_PLAN / DIRECT_ANSWER / FALLBACK |

### 13.2 错误码

| 错误码 | 说明 | 用户提示 |
|--------|------|---------|
| TEMPLATE_NOT_FOUND | 模板不存在 | 请稍后再试 |
| TEMPLATE_NOT_PUBLISHED | 模板未发布 | 服务正在准备中，请稍后再试 |
| FLOW_ERROR | 流程执行异常 | 系统处理时遇到问题，请稍后再试 |
| NODE_EXECUTE_ERROR | 节点执行错误 | 处理您的请求时遇到问题，已为您跳过 |
| NODE_NOT_FOUND | 节点类型不存在 | 系统配置异常，已跳过该步骤 |
| PARAM_MISSING | 缺少必需参数 | 请补充相关信息 |
| CONTEXT_EXPIRED | 上下文已过期 | 会话已过期，请重新开始 |
| OCR_SERVICE_ERROR | OCR服务异常 | 图片识别服务暂时不可用，请稍后再试 |
| OCR_PARSE_ERROR | OCR解析失败 | 无法识别这张图片，请重新上传 |
| LLM_TIMEOUT | LLM调用超时 | AI服务响应较慢，请稍后再试 |
| KNOWLEDGE_ERROR | 知识库检索异常 | 知识库检索失败，将继续为您解答 |
| TOOL_CALL_ERROR | 工具调用失败 | 调用服务遇到问题，已为您跳过 |
| REDIS_ERROR | Redis连接异常 | 会话服务暂时不可用，请稍后再试 |
| DYNAMIC_PLAN_ERROR | 动态规划失败 | 系统暂时无法处理您的问题，请稍后再试 |

### 12.3 参考文档

- Spring AI Alibaba 官方文档：https://springaialibaba.bootcom.cn/
- 阿里云百炼平台：https://bailian.console.aliyun.com/
- 通义千问 API：https://help.aliyun.com/zh/dashscope/
