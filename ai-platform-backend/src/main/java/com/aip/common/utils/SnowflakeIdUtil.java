package com.aip.common.utils;

import cn.hutool.core.lang.UUID;

/**
 * 雪花ID生成工具
 */
public class SnowflakeIdUtil {

    private static final SnowflakeIdWorker WORKER = new SnowflakeIdWorker(0, 0);

    /**
     * 生成雪花ID
     */
    public static long nextId() {
        return WORKER.nextId();
    }

    /**
     * 生成带前缀的ID
     */
    public static String nextIdWithPrefix(String prefix) {
        return prefix + nextId();
    }

    /**
     * 生成UUID
     */
    public static String uuid() {
        return UUID.randomUUID().toString(true);
    }

    /**
     * 简单雪花ID实现
     */
    private static class SnowflakeIdWorker {
        private static final long TWEPOCH = 1609459200000L;
        private static final long WORKER_ID_BITS = 5L;
        private static final long DATACENTER_ID_BITS = 5L;
        private static final long SEQUENCE_BITS = 12L;
        private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
        private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
        private final long workerId;
        private final long datacenterId;
        private long sequence = 0L;
        private long lastTimestamp = -1L;

        public SnowflakeIdWorker(long workerId, long datacenterId) {
            if (workerId > MAX_WORKER_ID || workerId < 0) {
                throw new IllegalArgumentException("workerId错误");
            }
            if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
                throw new IllegalArgumentException("datacenterId错误");
            }
            this.workerId = workerId;
            this.datacenterId = datacenterId;
        }

        public synchronized long nextId() {
            long timestamp = timeGen();
            if (timestamp < lastTimestamp) {
                throw new RuntimeException("时钟回拨");
            }
            if (lastTimestamp == timestamp) {
                sequence = (sequence + 1) & ((1 << SEQUENCE_BITS) - 1);
                if (sequence == 0) {
                    timestamp = tilNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }
            lastTimestamp = timestamp;
            return ((timestamp - TWEPOCH) << (WORKER_ID_BITS + DATACENTER_ID_BITS + SEQUENCE_BITS))
                    | (datacenterId << (WORKER_ID_BITS + SEQUENCE_BITS))
                    | (workerId << SEQUENCE_BITS)
                    | sequence;
        }

        private long tilNextMillis(long lastTimestamp) {
            long timestamp = timeGen();
            while (timestamp <= lastTimestamp) {
                timestamp = timeGen();
            }
            return timestamp;
        }

        private long timeGen() {
            return System.currentTimeMillis();
        }
    }
}
