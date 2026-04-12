package com.aip.flow.engine;

import lombok.Data;

/**
 * 节点执行结果
 * 封装节点执行后的状态和数据，供流程引擎统一处理
 */
@Data
public class NodeResult {

    /** 执行是否成功 */
    private boolean success;

    /** AI回复内容或节点输出 */
    private String output;

    /** 错误码，用于日志记录和问题排查 */
    private String errorCode;

    /** 用户友好的错误提示消息 */
    private String userMessage;

    /** 执行过程中收集到的业务参数 */
    private java.util.Map<String, Object> params;

    /** 流程状态：running-执行中, waiting-等待输入, completed-已完成 */
    private String status;

    /** 是否需要更多用户输入（参数收集场景） */
    private boolean needMoreInput;

    /** 追问用户的提示语 */
    private String prompt;

    /** 默认构造方法，初始化为成功状态 */
    public NodeResult() {
        this.success = true;
        this.status = "running";
    }

    /**
     * 创建成功结果（无输出）
     */
    public static NodeResult success() {
        NodeResult result = new NodeResult();
        result.setSuccess(true);
        return result;
    }

    /**
     * 创建成功结果（带输出内容）
     * @param output 输出内容（AI回复等）
     */
    public static NodeResult success(String output) {
        NodeResult result = success();
        result.setOutput(output);
        return result;
    }

    /**
     * 创建成功结果（带输出和参数）
     * @param output 输出内容
     * @param params 收集到的业务参数
     */
    public static NodeResult success(String output, java.util.Map<String, Object> params) {
        NodeResult result = success(output);
        result.setParams(params);
        return result;
    }

    /**
     * 创建需要用户输入的结果
     * 用于参数收集节点，提示用户补充信息
     * @param prompt 追问提示
     */
    public static NodeResult needInput(String prompt) {
        NodeResult result = new NodeResult();
        result.setSuccess(true);
        result.setNeedMoreInput(true);
        result.setPrompt(prompt);
        result.setStatus("waiting");
        return result;
    }

    /**
     * 创建失败结果
     * @param userMessage 用户友好的错误提示
     * @param errorCode 错误码
     */
    public static NodeResult fail(String userMessage, String errorCode) {
        NodeResult result = new NodeResult();
        result.setSuccess(false);
        result.setUserMessage(userMessage);
        result.setErrorCode(errorCode);
        result.setStatus("running");
        return result;
    }

    /**
     * 创建部分失败结果
     * 用于部分成功但需要用户补充的场景
     * @param userMessage 提示消息
     * @param prompt 追问提示
     */
    public static NodeResult partialFail(String userMessage, String prompt) {
        NodeResult result = new NodeResult();
        result.setSuccess(true);
        result.setUserMessage(userMessage);
        result.setPrompt(prompt);
        result.setNeedMoreInput(true);
        result.setStatus("waiting");
        return result;
    }

    /**
     * 创建跳过结果
     * 用于当前节点不需要执行但流程继续的场景
     * @param reason 跳过原因
     */
    public static NodeResult skip(String reason) {
        NodeResult result = new NodeResult();
        result.setSuccess(true);
        result.setOutput(reason);
        result.setStatus("running");
        return result;
    }

    /**
     * 创建流程结束结果
     */
    public static NodeResult completed() {
        NodeResult result = new NodeResult();
        result.setSuccess(true);
        result.setOutput("流程已结束");
        result.setStatus("completed");
        return result;
    }

    /**
     * 设置参数并返回自身（链式调用）
     */
    public NodeResult setParams(java.util.Map<String, Object> params) {
        this.params = params;
        return this;
    }
}
