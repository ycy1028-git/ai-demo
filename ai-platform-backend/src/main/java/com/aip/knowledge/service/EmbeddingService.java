package com.aip.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 向量化服务接口
 * 实现文本到向量embedding的转换
 */
@Slf4j
@Service
public class EmbeddingService {

    /** 向量维度 */
    @Value("${ai.embedding.dimension:768}")
    private int vectorDimension;

    /** Embedding服务地址 */
    @Value("${ai.embedding.api-url:http://localhost:8001}")
    private String embeddingApiUrl;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    private final Random random = new Random();

    /**
     * 文本向量化
     *
     * @param text 待向量化的文本
     * @return 向量数组
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("文本不能为空");
        }

        // 尝试调用远程embedding服务
        try {
            float[] vector = callEmbeddingApi(text);
            log.debug("远程向量化成功, 长度: {} 字符, 维度: {}", text.length(), vector.length);
            return vector;
        } catch (Exception e) {
            log.warn("远程向量化失败，使用模拟实现: {}", e.getMessage());
            return generateSimulatedVector(text);
        }
    }

    /**
     * 调用远程Embedding API
     *
     * @param text 文本
     * @return 向量数组
     */
    private float[] callEmbeddingApi(String text) {
        if (restTemplate == null) {
            restTemplate = new RestTemplate();
        }

        // 构建请求
        Map<String, Object> request = new HashMap<>();
        request.put("text", text);

        // 调用API
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(
                embeddingApiUrl + "/embed",
                request,
                Map.class
        );

        if (response == null || !response.containsKey("embedding")) {
            throw new RuntimeException("Embedding API返回格式错误");
        }

        @SuppressWarnings("unchecked")
        List<Number> embeddingList = (List<Number>) response.get("embedding");

        float[] vector = new float[embeddingList.size()];
        for (int i = 0; i < embeddingList.size(); i++) {
            vector[i] = embeddingList.get(i).floatValue();
        }

        return vector;
    }

    /**
     * 生成模拟向量（基于文本内容生成确定性随机向量）
     * 用于没有真实embedding服务时的降级方案
     *
     * @param text 文本
     * @return 向量数组
     */
    private float[] generateSimulatedVector(String text) {
        float[] vector = new float[vectorDimension];

        // 基于文本内容生成伪随机但确定性的向量
        int seed = text.hashCode();
        Random seededRandom = new Random(seed);

        for (int i = 0; i < vectorDimension; i++) {
            // 生成 [-1, 1] 范围内的值
            vector[i] = (float) (seededRandom.nextGaussian() * 0.5);
        }

        // 归一化处理
        normalize(vector);

        log.debug("生成模拟向量, 维度: {}", vectorDimension);
        return vector;
    }

    /**
     * 批量向量化
     *
     * @param texts 文本列表
     * @return 向量数组列表
     */
    public float[][] batchEmbed(String[] texts) {
        if (texts == null || texts.length == 0) {
            return new float[0][];
        }

        float[][] vectors = new float[texts.length][];
        for (int i = 0; i < texts.length; i++) {
            vectors[i] = embed(texts[i]);
        }
        return vectors;
    }

    /**
     * 计算余弦相似度
     *
     * @param vecA 向量A
     * @param vecB 向量B
     * @return 相似度分数 [-1, 1]
     */
    public float cosineSimilarity(float[] vecA, float[] vecB) {
        if (vecA.length != vecB.length) {
            throw new IllegalArgumentException("向量维度不一致");
        }

        float dotProduct = 0;
        float normA = 0;
        float normB = 0;

        for (int i = 0; i < vecA.length; i++) {
            dotProduct += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }

        float denominator = (float) Math.sqrt(normA) * (float) Math.sqrt(normB);
        if (denominator == 0) {
            return 0;
        }

        return dotProduct / denominator;
    }

    /**
     * 向量归一化（L2归一化）
     */
    private void normalize(float[] vector) {
        float norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }

    /**
     * 获取向量维度
     */
    public int getDimension() {
        return vectorDimension;
    }
}
