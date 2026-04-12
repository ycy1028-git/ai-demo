package com.aip.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.util.UUID;

/**
 * Jackson 配置
 * <p>
 * 配置 Jackson 序列化/反序列化行为：
 * 1. UUID 直接序列化为标准字符串格式（如 "550e8400-e29b-41d4-a716-446655440000"）
 * 2. Java 8 时间类型正确序列化
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        SimpleModule uuidModule = new SimpleModule();
        uuidModule.addSerializer(UUID.class, new JsonSerializer<UUID>() {
            @Override
            public void serialize(UUID value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.toString());
            }
        });

        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(uuidModule)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
