package com.aip.app.constant;

/**
 * 数据状态常量
 */
public class Status {

    /** 禁用 */
    public static final int DISABLED = 0;

    /** 启用 */
    public static final int ENABLED = 1;

    /** 删除 */
    public static final int DELETED = -1;

    /** 待处理 */
    public static final int PENDING = 0;

    /** 处理中 */
    public static final int PROCESSING = 1;

    /** 已完成 */
    public static final int COMPLETED = 2;

    /** 处理失败 */
    public static final int FAILED = -1;

    /**
     * 获取状态描述
     */
    public static String getDesc(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case DISABLED -> "禁用";
            case ENABLED -> "启用";
            case DELETED -> "已删除";
            default -> String.valueOf(status);
        };
    }

    private Status() {
        // 私有构造函数，防止实例化
    }
}
