package com.aip.common.util;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UUIDv7 工具类
 * 
 * 数据库存储格式：CHAR(32) 无横杠小写字符串
 * 示例：01920a1e-7d2c-7abc-8f3e-1a2b3c4d5e6f → 01920a1e7d2c7abc8f3e1a2b3c4d5e6f
 * 
 * 核心优势：
 * - 时间戳位于高位，保证趋势递增
 * - 无横杠固定 32 字符，索引更紧凑
 * - 符合 RFC 9562 标准
 * - 数据库索引性能优异
 *
 * @author AI Platform
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9562">RFC 9562 - UUIDs</a>
 */
public final class UuidV7Utils {

    /** UUID 纪元开始时间：2024-01-01 00:00:00 UTC */
    private static final long EPOCH_START = 1704067200000L;
    /** 版本位值 = 7 */
    private static final int VERSION = 7;
    /** 变体掩码（10xx） */
    private static final long VARIANT_MASK = 0xC000L;
    /** 变体值（10xx） */
    private static final long VARIANT_VALUE = 0x8000L;

    private UuidV7Utils() {}

    /**
     * 生成 UUIDv7 并转为无横杠小写字符串（数据库存储格式）
     * @return 32 位无横杠小写字符串
     */
    public static String generateUuidV7String() {
        return toShortString(generateUuidV7());
    }

    /**
     * 生成符合 RFC 9562 的 UUIDv7
     * 
     * 结构：
     * - MSB 高 48 位：Unix 毫秒时间戳（从 2024-01-01 开始）
     * - MSB 次 4 位：版本号 7
     * - MSB 次 12 位 + LSB 高 64 位：随机数
     * - LSB 低 2 位：变体位 10
     * 
     * @return 时间排序的唯一 UUID
     */
    public static UUID generateUuidV7() {
        // 获取从纪元开始的毫秒数
        long elapsedMs = Instant.now().toEpochMilli() - EPOCH_START;
        
        // 生成 62 位随机数（用于 MSB 的低 12 位 + LSB 的高 62 位）
        long random62 = ThreadLocalRandom.current().nextLong() & 0x3FFFFFFFFFFFFFFFL; // 62 位掩码
        
        // 构建 MSB：
        // [48位时间戳][4位版本=7][12位随机数高12位]
        long mostSigBits = (elapsedMs << 16) | ((long) VERSION << 12) | (random62 >>> 50);
        
        // 构建 LSB：
        // [62位随机数低50位][2位变体(10)]
        long leastSigBits = (random62 << 2) | VARIANT_VALUE;
        
        return new UUID(mostSigBits, leastSigBits);
    }

    /**
     * 从 UUIDv7 提取时间戳
     * @param uuid UUIDv7 格式
     * @return 时间戳
     */
    public static Instant extractTimestamp(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        // 提取 MSB 高 48 位作为时间戳
        long elapsedMs = uuid.getMostSignificantBits() >>> 16;
        return Instant.ofEpochMilli(elapsedMs + EPOCH_START);
    }

    /**
     * 判断是否为 UUIDv7 格式
     * @param uuid 待验证的 UUID
     * @return 是否为 UUIDv7
     */
    public static boolean isUuidV7(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        int version = uuid.version();
        return version == VERSION;
    }

    /**
     * 转换为字节数组（大端序）
     * @param uuid UUID
     * @return 16字节数组
     */
    public static byte[] toBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    /**
     * 从字节数组转换
     * @param bytes 16字节数组
     * @return UUID
     */
    public static UUID fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            throw new IllegalArgumentException("字节数组长度必须为16");
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }

    /**
     * 转换为无横杠小写字符串（数据库存储格式）
     * @param uuid UUID
     * @return 32位无横杠小写字符串
     */
    public static String toShortString(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return uuid.toString().replace("-", "").toLowerCase();
    }

    /**
     * 从无横杠字符串还原 UUID 对象
     * @param hexString 32位无横杠字符串（支持大小写）
     * @return UUID
     */
    public static UUID fromShortString(String hexString) {
        if (hexString == null || hexString.length() != 32) {
            throw new IllegalArgumentException("无横杠UUID必须为32位，实际长度：" + (hexString == null ? "null" : hexString.length()));
        }
        return UUID.fromString(
            hexString.substring(0, 8) + "-" +
            hexString.substring(8, 12) + "-" +
            hexString.substring(12, 16) + "-" +
            hexString.substring(16, 20) + "-" +
            hexString.substring(20, 32)
        );
    }

    /**
     * 从无横杠字符串提取时间戳
     * @param hexString 32位无横杠字符串
     * @return 时间戳
     */
    public static Instant extractTimestampFromHex(String hexString) {
        return extractTimestamp(fromShortString(hexString));
    }

    /**
     * 验证是否为有效的32位无横杠字符串
     * @param hexString 待验证字符串
     * @return 是否有效
     */
    public static boolean isValidHexString(String hexString) {
        if (hexString == null || hexString.length() != 32) {
            return false;
        }
        return hexString.matches("^[0-9a-fA-F]{32}$");
    }
}