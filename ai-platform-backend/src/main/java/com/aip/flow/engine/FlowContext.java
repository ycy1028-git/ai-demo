package com.aip.flow.engine;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程上下文
 * 存储流程执行过程中的状态和数据，在整个流程生命周期内保持传递
 * <p>
 * 包含：会话信息、用户信息、当前消息、已收集参数、对话历史、流程状态等
 */
@Data
public class FlowContext implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int MAX_HISTORY_SIZE = 20;

    /** 会话ID，唯一标识一次对话会话 */
    private String sessionId;

    /** 用户ID，用于多用户场景下的上下文隔离 */
    private String userId;

    /** 当前模板编码，表示正在执行的流程模板 */
    private String templateCode;

    /** 用户当前发送的消息内容 */
    private String currentMessage;

    /** 当前节点索引，记录流程执行到第几个节点 */
    private int currentNodeIndex;

    /** 已收集的参数Map，键为参数名，值为参数值 */
    private Map<String, Object> params;

    /** 对话历史列表，存储用户和AI的对话记录 */
    private List<String> history;

    /** 流程状态：running-执行中, waiting-等待用户输入, completed-已完成, failed-失败 */
    private String status;

    /** 扩展元数据，用于存储额外的流程相关信息 */
    private Map<String, Object> metadata;

    /** 默认构造方法，初始化所有属性为默认值 */
    public FlowContext() {
        this.sessionId = generateSessionId();
        this.params = new HashMap<>();
        this.history = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.status = "running";
        this.currentNodeIndex = 0;
    }

    /**
     * 根据用户ID创建流程上下文
     * @param userId 用户ID
     */
    public FlowContext(String userId) {
        this();
        this.userId = userId;
    }

    /**
     * 根据用户ID和当前消息创建流程上下文
     * @param userId 用户ID
     * @param currentMessage 当前用户消息
     */
    public FlowContext(String userId, String currentMessage) {
        this(userId);
        this.currentMessage = currentMessage;
    }

    /**
     * 添加对话历史记录
     * @param message 对话消息内容
     */
    public void addHistory(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (this.history == null) {
            this.history = new ArrayList<>();
        }
        this.history.add(message.trim());
        trimHistory();
    }

    /**
     * 添加用户消息到历史
     */
    public void addUserMessage(String message) {
        addHistory("用户: " + message);
    }

    /**
     * 添加助手消息到历史
     */
    public void addAssistantMessage(String message) {
        addHistory("AI: " + message);
    }

    /**
     * 添加业务参数
     * @param key 参数名
     * @param value 参数值
     */
    public void addParam(String key, Object value) {
        if (this.params == null) {
            this.params = new HashMap<>();
        }
        this.params.put(key, value);
    }

    /**
     * 设置元数据
     * @param key 元数据键
     * @param value 元数据值
     */
    public void setMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /**
     * 获取元数据
     * @param key 元数据键
     * @return 元数据值，不存在则返回null
     */
    public Object getMetadata(String key) {
        return this.metadata != null ? this.metadata.get(key) : null;
    }

    /**
     * 生成会话ID
     * 格式：sess_时间戳_随机数
     */
    private String generateSessionId() {
        return "sess_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
    }

    private void trimHistory() {
        if (this.history == null || this.history.size() <= MAX_HISTORY_SIZE) {
            return;
        }
        int removeCount = this.history.size() - MAX_HISTORY_SIZE;
        this.history.subList(0, removeCount).clear();
    }
}
