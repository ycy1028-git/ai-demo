-- ================================================
-- AI 智答平台 - 初始化数据脚本
-- 说明: 仅插入初始数据，表结构由 JPA 自动创建
-- 字段规范: 使用数据库列名（f_ 前缀 + 下划线命名）
--
-- 存储格式: UUIDv7 无横杠字符串 (CHAR(32))
-- 性能优势:
--   - 固定 32 字符，索引页更紧凑
--   - UUIDv7 时间戳在高位，新数据追加到索引末端，页分裂最少
--   - 比有横杠格式节省 4 字符
--
-- 字段插入规则:
-- - 所有公共字段必须显式插入（JPA 自动管理仅适用于运行时，不适用于纯 SQL）：
--   - f_id: 预生成的 UUIDv7（无横杠小写字符串）
--   - f_create_time: NOW(3)
--   - f_update_time: NOW(3)
--   - f_create_by/f_update_by: 管理员用户 UUIDv7（关联 t_sys_user 表）
--   - f_deleted: false（逻辑删除标记）
--   - f_version: 0（乐观锁版本号）
-- - 使用 ON DUPLICATE KEY UPDATE 保证幂等性
-- ================================================

-- 预定义 UUIDv7 常量（无横杠小写字符串，时间排序友好）
SET @uid_admin      = '018a0e00d8f17a8bc0d1e2f3a4b5c6d7';
SET @uid_deepseek   = '018a0e01d8f17a8bc0d2e2f3a4b5c6d8';
SET @uid_qwen       = '018a0e02d8f17a8bc0d3e2f3a4b5c6d9';
SET @uid_zhipu      = '018a0e03d8f17a8bc0d4e2f3a4b5c6da';
SET @uid_kb_cust    = '018a0e04d8f17a8bc0d5e2f3a4b5c6db';
SET @uid_kb_search  = '018a0e05d8f17a8bc0d6e2f3a4b5c6dc';
SET @uid_kb_hr      = '018a0e06d8f17a8bc0d7e2f3a4b5c6dd';
SET @uid_kb_finance = '018a0e07d8f17a8bc0d8e2f3a4b5c6de';
SET @uid_kb_train   = '018a0e08d8f17a8bc0d9e2f3a4b5c6df';
SET @uid_kb_csrc    = '018a0e09d8f17a8bc0dae2f3a4b5c6e0';
SET @uid_ki_faq     = '018a0e10d8f17a8bc0dbe2f3a4b5c6e1';
SET @uid_ki_terms   = '018a0e11d8f17a8bc0dce2f3a4b5c6e2';
SET @uid_ki_privacy = '018a0e12d8f17a8bc0dde2f3a4b5c6e3';
SET @uid_ki_hrman   = '018a0e13d8f17a8bc0dee2f3a4b5c6e4';
SET @uid_ki_expense = '018a0e14d8f17a8bc0dfe2f3a4b5c6e5';
SET @uid_fl_general = '018a0e15d8f17a8bc0e0e2f3a4b5c6e6';
SET @uid_fl_expense = '018a0e16d8f17a8bc0e1e2f3a4b5c6e7';
SET @uid_fl_leave   = '018a0e17d8f17a8bc0e2e2f3a4b5c6e8';
SET @uid_fl_order   = '018a0e18d8f17a8bc0e3e2f3a4b5c6e9';

-- ================================================
-- 1. 管理员用户（优先插入，作为其他表的关联用户）
-- 密码: admin123 (BCrypt加密)
-- 生成命令: new BCryptPasswordEncoder().encode("admin123")
-- Java字段: UserEntity继承自BusinessEntity
-- UUID格式: UUIDv7 无横杠字符串 (CHAR(32))
-- ================================================
INSERT INTO t_sys_user (f_id, f_username, f_password, f_real_name, f_email, f_phone, f_create_time, f_update_time, f_create_by, f_update_by, f_status, f_deleted, f_version)
VALUES (@uid_admin, 'admin', '$2a$10$rXtsiqP9Fn0KghPMyZChMumAJI8qTgnWYRz8WnWULG8SnZkJx5A02', '系统管理员', 'admin@example.com', '13800138000', NOW(3), NOW(3), NULL, NULL, 1, false, 0)
ON DUPLICATE KEY UPDATE f_update_time = NOW(3);

-- ================================================
-- 2. AI模型配置
-- Java字段: AiModelConfig extends BusinessEntity
-- UUID格式: UUIDv7 无横杠字符串 (CHAR(32))
-- ================================================
INSERT INTO t_ai_model_config (f_id, f_name, f_provider, f_api_url, f_api_key, f_model_name, f_temperature, f_max_tokens, f_enabled, f_is_default, f_sort_order, f_description, f_create_time, f_update_time, f_create_by, f_update_by, f_status, f_deleted, f_version)
VALUES
    (@uid_deepseek, 'DeepSeek Chat', 'deepseek', 'https://api.deepseek.com/v1', NULL, 'deepseek-chat', 0.7, 2000, true, false, 1, 'DeepSeek 模型，支持中文对话', NOW(3), NOW(3), @uid_admin, @uid_admin, 1, false, 0),
    (@uid_qwen, '通义千问 Plus', 'qwen', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'sk-4982b6d5d8334abaabb8dcca6abaf968', 'qwen-plus', 0.8, 4000, true, true, 2, '阿里云通义千问增强版', NOW(3), NOW(3), @uid_admin, @uid_admin, 1, false, 0),
    (@uid_zhipu, '智谱 GLM-4', 'zhipu', 'https://open.bigmodel.cn/api/paas/v4', NULL, 'glm-4', 0.7, 2000, true, false, 3, '智谱AI GLM-4模型', NOW(3), NOW(3), @uid_admin, @uid_admin, 1, false, 0)
ON DUPLICATE KEY UPDATE f_update_time = NOW(3);

-- ================================================
-- 3. 知识库
-- Java字段: KnowledgeBase extends BusinessEntity
-- ES索引需要单独创建，参考 init-elasticsearch.sh
-- UUID格式: UUIDv7 无横杠字符串 (CHAR(32))
-- ================================================
INSERT INTO t_kb_knowledge_base (f_id, f_name, f_code, f_description, f_es_index, f_oss_path_prefix, f_scene_description, f_priority, f_create_time, f_update_time, f_create_by, f_update_by, f_status, f_deleted, f_version)
VALUES
    (@uid_kb_cust, '客服知识库', 'kb_customer', '系统自动创建的客服知识库', 'kb_kb_customer', 'documents/customer/', '处理客户咨询、问题反馈等服务', 10, NOW(3), NOW(3), @uid_admin, @uid_admin, 1, false, 0),
    (@uid_kb_search, '搜索知识库', 'kb_search', '系统自动创建的搜索知识库', 'kb_kb_search', 'documents/search/', '提供各类信息搜索服务', 8, NOW(3), NOW(3), @uid_admin, @uid_admin, 1, false, 0),
    (@uid_kb_hr, 'HR知识库', 'kb_hr', '系统自动创建的HR知识库', 'kb_kb_hr', 'documents/hr/', '人力资源相关问题解答', 6, NOW(3), NOW(3), @uid_admin, @uid_admin, 1, false, 0),
    (@uid_kb_finance, '财务知识库', 'kb_finance', '系统自动创建的财务知识库', 'kb_kb_finance', 'documents/finance/', '财务报销、预算等问题', 5, NOW(3), NOW(3), @uid_admin, @uid_admin, 1, false, 0),
    (@uid_kb_train, '培训知识库', 'kb_training', '系统自动创建的培训知识库', 'kb_kb_training', 'documents/training/', '培训课程、学习资料', 4, NOW(3), NOW(3), @uid_admin, @uid_admin, 1, false, 0),
    (@uid_kb_csrc, '客资知识库', 'kb_customer_source', '系统自动创建的客资知识库', 'kb_kb_customer_source', 'documents/customer_source/', '客户服务与支持', 7, NOW(3), NOW(3), @uid_admin, @uid_admin, 1, false, 0)
ON DUPLICATE KEY UPDATE f_update_time = NOW(3);

-- ================================================
-- 4. 知识条目示例
-- Java字段: KnowledgeItem extends BaseEntity
-- 关联知识库ID通过子查询动态获取
-- UUID格式: UUIDv7 无横杠字符串 (CHAR(32))
-- ================================================
INSERT INTO t_kb_knowledge_item (f_id, f_kb_id, f_title, f_content, f_summary, f_tags, f_create_time, f_update_time, f_create_by, f_update_by, f_status, f_vector_status, f_source_type, f_deleted, f_version)
VALUES
    (@uid_ki_faq, @uid_kb_cust, '常见问题FAQ', 'Q1: 如何重置密码？\nA: 在登录页面点击"忘记密码"，输入注册邮箱，系统会发送重置链接。\n\nQ2: 如何联系客服？\nA: 可以通过在线客服、电话400-xxx-xxxx或邮件support@example.com联系我们。', '系统常见问题解答', '["FAQ","帮助"]', NOW(3), NOW(3), @uid_admin, @uid_admin, 1, 2, 'manual', false, 0),
    (@uid_ki_terms, @uid_kb_cust, '服务条款', '本服务条款适用于所有使用本平台的用户...', '平台服务条款', '["条款","协议"]', NOW(3), NOW(3), @uid_admin, @uid_admin, 1, 2, 'manual', false, 0),
    (@uid_ki_privacy, @uid_kb_cust, '隐私政策', '我们重视用户的隐私保护...', '隐私政策说明', '["隐私","政策"]', NOW(3), NOW(3), @uid_admin, @uid_admin, 1, 2, 'manual', false, 0),
    (@uid_ki_hrman, @uid_kb_hr, '员工手册', '欢迎加入我们的团队！本手册包含公司制度、福利政策等内容...', '新员工入职指南', '["入职","手册"]', NOW(3), NOW(3), @uid_admin, @uid_admin, 1, 2, 'manual', false, 0),
    (@uid_ki_expense, @uid_kb_finance, '报销流程', '1. 登录财务系统\n2. 填写报销单\n3. 上传发票\n4. 提交审批\n5. 财务审核', '日常报销流程说明', '["报销","流程"]', NOW(3), NOW(3), @uid_admin, @uid_admin, 1, 2, 'manual', false, 0)
ON DUPLICATE KEY UPDATE f_update_time = NOW(3);

-- ================================================
-- 5. 流程模板
-- Java字段: FlowTemplate extends BaseEntity
-- UUID格式: UUIDv7 无横杠字符串 (CHAR(32))
-- ================================================

-- 通用问答兜底模板
INSERT INTO t_flow_template (f_id, f_template_code, f_template_name, f_description, f_match_pattern, f_match_prompt, f_flow_data, f_priority, f_is_fallback, f_is_dynamic, f_create_time, f_update_time, f_create_by, f_update_by, f_status, f_deleted, f_version)
VALUES
    (@uid_fl_general, 'general_qa', '通用问答', '无法匹配时的兜底处理', NULL, '当用户问题无法匹配任何固定流程时使用此模板', '{"flowId": "general_qa", "flowName": "通用问答", "nodes": [{"id": "node_1", "type": "knowledge_retrieval", "name": "知识检索", "data": {"strategy": "smart"}}, {"id": "node_2", "type": "llm_call", "name": "LLM回答", "data": {"systemPrompt": "你是一个友好的AI助手，请根据用户的问题给出有帮助的回答。"}}]}', 0, 1, 1, NOW(3), NOW(3), @uid_admin, @uid_admin, 1, false, 0)
ON DUPLICATE KEY UPDATE f_update_time = NOW(3);

-- 费用报销流程模板
INSERT INTO t_flow_template (f_id, f_template_code, f_template_name, f_description, f_match_pattern, f_match_prompt, f_flow_data, f_priority, f_is_fallback, f_is_dynamic, f_create_time, f_update_time, f_create_by, f_update_by, f_status, f_deleted, f_version)
VALUES
    (@uid_fl_expense, 'reimbursement', '费用报销流程', '处理差旅费、日常报销等', '报销|差旅|报销单|报销金额|我要报销', '当用户询问报销相关问题时触发，包括：差旅费报销、日常费用报销、出差费用等场景', '{"flowId": "reimbursement", "flowName": "费用报销流程", "nodes": [{"id": "node_1", "type": "collect", "name": "收集报销金额", "data": {"paramName": "amount", "prompt": "请告诉我您的报销金额是多少？", "required": true}}, {"id": "node_2", "type": "collect", "name": "收集报销类别", "data": {"paramName": "category", "prompt": "请问报销类别是？", "options": ["差旅费", "日常费用", "办公用品", "其他"]}}, {"id": "node_3", "type": "condition", "name": "金额判断", "data": {"mode": "preset", "checkField": "amount", "branches": [{"name": "小额审批", "condition": "amount <= 1000", "targetNode": "node_4"}, {"name": "大额审批", "condition": "amount > 1000", "targetNode": "node_5"}]}}, {"id": "node_4", "type": "execute", "name": "快速审批", "data": {"operation": "quick_approve", "message": "报销金额在1000元以内，已自动通过审批"}}, {"id": "node_5", "type": "execute", "name": "提交审批", "data": {"operation": "submit_approval", "requireApproval": true}}, {"id": "node_6", "type": "llm_call", "name": "确认回复", "data": {"systemPrompt": "你是一个报销助手，根据报销信息生成友好的确认回复"}}]}', 100, 0, 0, NOW(3), NOW(3), @uid_admin, @uid_admin, 1, false, 0)
ON DUPLICATE KEY UPDATE f_update_time = NOW(3);

-- 请假申请流程模板
INSERT INTO t_flow_template (f_id, f_template_code, f_template_name, f_description, f_match_pattern, f_match_prompt, f_flow_data, f_priority, f_is_fallback, f_is_dynamic, f_create_time, f_update_time, f_create_by, f_update_by, f_status, f_deleted, f_version)
VALUES
    (@uid_fl_leave, 'leave_request', '请假申请流程', '处理各种请假申请', '请假|休假|请.*天.*假|我要请假|申请休假', '当用户询问请假、休假相关问题时触发，包括：年假，事假、病假、婚假、产假等', '{"flowId": "leave_request", "flowName": "请假申请流程", "nodes": [{"id": "node_1", "type": "collect", "name": "请假类型", "data": {"paramName": "leaveType", "prompt": "请问您要请什么类型的假期？", "options": ["年假", "事假", "病假", "婚假", "产假"]}}, {"id": "node_2", "type": "collect", "name": "请假时间", "data": {"paramName": "dateRange", "prompt": "请问您要请从什么时候开始，请多长时间？"}}, {"id": "node_3", "type": "collect", "name": "请假原因", "data": {"paramName": "reason", "prompt": "请问请假的原因是什么？（可选）", "required": false}}, {"id": "node_4", "type": "execute", "name": "余额检查", "data": {"operation": "check_leave_balance"}}, {"id": "node_5", "type": "condition", "name": "余额判断", "data": {"mode": "preset", "checkField": "balance", "branches": [{"name": "余额充足", "condition": "balance >= requestedDays", "targetNode": "node_6"}, {"name": "余额不足", "condition": "balance < requestedDays", "targetNode": "node_7"}]}}, {"id": "node_6", "type": "execute", "name": "提交申请", "data": {"operation": "submit_leave", "workflowType": "leave"}}, {"id": "node_7", "type": "llm_call", "name": "余额不足提示", "data": {"systemPrompt": "你是一个请假助手，当用户余额不足时，友好地告知并提供建议"}}, {"id": "node_8", "type": "llm_call", "name": "确认回复", "data": {"systemPrompt": "你是一个请假助手，根据请假信息生成友好的确认回复"}}]}', 100, 0, 0, NOW(3), NOW(3), @uid_admin, @uid_admin, 1, false, 0)
ON DUPLICATE KEY UPDATE f_update_time = NOW(3);

-- 订单查询流程模板
INSERT INTO t_flow_template (f_id, f_template_code, f_template_name, f_description, f_match_pattern, f_match_prompt, f_flow_data, f_priority, f_is_fallback, f_is_dynamic, f_create_time, f_update_time, f_create_by, f_update_by, f_status, f_deleted, f_version)
VALUES
    (@uid_fl_order, 'order_query', '订单查询流程', '查询订单状态、物流信息等', '订单|查单|快递|物流|发货|我的订单', '当用户询问订单、物流相关问题时触发，包括：订单状态、物流进度、签收信息等', '{"flowId": "order_query", "flowName": "订单查询流程", "nodes": [{"id": "node_1", "type": "collect", "name": "订单号", "data": {"paramName": "orderId", "prompt": "请告诉我您的订单号", "required": true}}, {"id": "node_2", "type": "execute", "name": "查询订单", "data": {"operation": "query_order"}}, {"id": "node_3", "type": "condition", "name": "物流判断", "data": {"mode": "preset", "checkField": "hasLogistics", "branches": [{"name": "有物流", "condition": "hasLogistics == true", "targetNode": "node_4"}, {"name": "无物流", "condition": "hasLogistics == false", "targetNode": "node_5"}]}}, {"id": "node_4", "type": "execute", "name": "查询物流", "data": {"operation": "query_logistics"}}, {"id": "node_5", "type": "llm_call", "name": "无物流回复", "data": {"systemPrompt": "当订单暂无物流信息时，友好地告知用户"}}, {"id": "node_6", "type": "llm_call", "name": "订单信息回复", "data": {"systemPrompt": "你是一个订单助手，根据查询到的订单和物流信息，生成详细的回复"}}]}', 100, 0, 0, NOW(3), NOW(3), @uid_admin, @uid_admin, 1, false, 0)
ON DUPLICATE KEY UPDATE f_update_time = NOW(3);

-- ================================================
-- 完成
-- ================================================
SELECT '初始化数据完成!' AS message;
