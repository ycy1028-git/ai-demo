package com.aip.common.ai;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * SSE流式响应工具类
 */
public class SseEmitterUtil {

    /**
     * 创建SSEemitter并绑定完成回调
     */
    public static SseEmitter create(Runnable onCompletion) {
        SseEmitter emitter = new SseEmitter(0L); // 0表示无超时限制
        emitter.onCompletion(onCompletion);
        emitter.onTimeout(() -> {
            if (onCompletion != null) {
                onCompletion.run();
            }
        });
        emitter.onError(e -> {
            if (onCompletion != null) {
                onCompletion.run();
            }
        });
        return emitter;
    }

    /**
     * 发送SSE事件
     */
    public static void send(SseEmitter emitter, String eventId, String eventType, String data) {
        try {
            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                    .id(eventId)
                    .name(eventType)
                    .data(data, MediaType.TEXT_EVENT_STREAM);
            emitter.send(builder);
        } catch (IOException e) {
            // 发送失败时不抛出异常，由错误处理统一处理
        }
    }

    /**
     * 发送文本数据
     */
    public static void sendText(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event()
                    .data(data, MediaType.TEXT_PLAIN));
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * 发送JSON数据
     */
    public static void sendJson(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event()
                    .data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * 完成SSE流
     */
    public static void complete(SseEmitter emitter) {
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    /**
     * 完成并发送最终数据
     */
    public static void completeWithData(SseEmitter emitter, String data) {
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().data(data));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }
    }
}
