package com.aip.common.utils;

import cn.hutool.core.lang.UUID;

/**
 * UUID工具类
 */
public class UUIDUtil {

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String uuidWithoutDash() {
        return UUID.randomUUID().toString(true);
    }

    public static String simpleUUID() {
        return UUID.fastUUID().toString(true);
    }
}
