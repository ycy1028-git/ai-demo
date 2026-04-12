package com.aip.knowledge.service.impl;

import com.aip.knowledge.entity.KnowledgeBase;
import com.aip.knowledge.mapper.KnowledgeBaseMapper;
import com.aip.knowledge.service.EmbeddingService;
import com.aip.knowledge.service.ElasticsearchService;
import com.aip.knowledge.service.IKnowledgeMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库匹配服务实现
 * 支持向量相似度匹配、优先级排序，以及默认客服知识库兜底
 * 知识库匹配现在由专家流程/流程节点配置，不再依赖关键词匹配
 */
@Slf4j
@Service
public class KnowledgeMatchServiceImpl implements IKnowledgeMatchService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired(required = false)
    private EmbeddingService embeddingService;

    @Autowired(required = false)
    private ElasticsearchService elasticsearchService;

    /** 默认客服知识库编码 */
    @Value("${ai.chat.default-knowledge-base-code:customer-service}")
    private String defaultKbCode;

    /** 最小匹配分数阈值 */
    @Value("${ai.chat.min-match-score:5}")
    private int minMatchScore;

    /** 向量匹配结果数 */
    @Value("${ai.chat.vector-match-topk:10}")
    private int vectorMatchTopK;

    public KnowledgeMatchServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    @Override
    public Optional<KnowledgeBase> matchBest(String userQuestion) {
        List<KnowledgeBase> matched = matchTop(userQuestion, 1);
        if (matched.isEmpty()) {
            Optional<KnowledgeBase> defaultKb = getDefaultCustomerServiceKb();
            log.debug("未找到匹配的知识库，使用默认客服知识库: {}", defaultKb.map(KnowledgeBase::getName).orElse("无"));
            return defaultKb;
        }
        return Optional.of(matched.get(0));
    }

    @Override
    public List<KnowledgeBase> matchTop(String userQuestion, int limit) {
        if (userQuestion == null || userQuestion.isBlank()) {
            return List.of();
        }

        List<KnowledgeBase> allKbs = knowledgeBaseMapper.findAll().stream()
                .filter(kb -> kb.getStatus() != null && kb.getStatus() == 1)
                .collect(Collectors.toList());

        // 向量相似度匹配
        List<KnowledgeBaseMatch> vectorMatches = matchByVector(userQuestion, allKbs);

        // 按分数排序
        return vectorMatches.stream()
                .filter(m -> m.score >= minMatchScore)
                .sorted((a, b) -> {
                    int scoreCompare = Integer.compare(b.score, a.score);
                    if (scoreCompare != 0) return scoreCompare;
                    return Integer.compare(
                            b.kb.getPriority() != null ? b.kb.getPriority() : 0,
                            a.kb.getPriority() != null ? a.kb.getPriority() : 0
                    );
                })
                .map(m -> m.kb)
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isMatch(String userQuestion, KnowledgeBase knowledgeBase) {
        if (userQuestion == null || userQuestion.isBlank() || knowledgeBase == null) {
            return false;
        }
        return isVectorMatch(userQuestion, knowledgeBase);
    }

    @Override
    public int calculateMatchScore(String userQuestion, KnowledgeBase knowledgeBase) {
        if (userQuestion == null || userQuestion.isBlank() || knowledgeBase == null) {
            return 0;
        }

        int score = 0;
        score += calculateVectorScore(userQuestion, knowledgeBase);

        if (knowledgeBase.getPriority() != null) {
            score += knowledgeBase.getPriority();
        }

        return score;
    }

    /**
     * 获取默认客服知识库
     */
    public Optional<KnowledgeBase> getDefaultCustomerServiceKb() {
        return knowledgeBaseMapper.findAll().stream()
                .filter(kb -> kb.getStatus() != null && kb.getStatus() == 1)
                .filter(kb -> defaultKbCode.equals(kb.getCode()))
                .findFirst();
    }

    /**
     * 根据向量相似度匹配
     */
    private List<KnowledgeBaseMatch> matchByVector(String userQuestion, List<KnowledgeBase> allKbs) {
        if (embeddingService == null || elasticsearchService == null) {
            log.debug("EmbeddingService或ElasticsearchService不可用，跳过向量匹配");
            return Collections.emptyList();
        }

        List<KnowledgeBaseMatch> matches = new ArrayList<>();

        try {
            float[] queryVector = embeddingService.embed(userQuestion);

            for (KnowledgeBase kb : allKbs) {
                if (kb.getEsIndex() == null || kb.getEsIndex().isBlank()) {
                    continue;
                }

                try {
                    List<Map<String, Object>> results = elasticsearchService.similaritySearchRaw(
                            kb.getEsIndex(), queryVector, vectorMatchTopK
                    );

                    if (!results.isEmpty()) {
                        float avgSimilarity = 0;
                        int matchCount = results.size();

                        for (Map<String, Object> doc : results) {
                            Object kbId = doc.get("kbId");
                            if (kbId != null && kbId.toString().equals(kb.getId().toString())) {
                                avgSimilarity += 1.0f;
                            }
                        }

                        avgSimilarity = avgSimilarity / matchCount;
                        int score = (int) (avgSimilarity * 50);
                        score = Math.max(score, 0);

                        if (score > 0) {
                            KnowledgeBaseMatch match = new KnowledgeBaseMatch();
                            match.kb = kb;
                            match.score = score;
                            match.matchType = Set.of("vector");
                            matches.add(match);
                            log.debug("向量匹配知识库: {} 分数: {}", kb.getName(), score);
                        }
                    }
                } catch (Exception e) {
                    log.warn("知识库{}向量匹配失败: {}", kb.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("向量匹配失败: {}", e.getMessage());
        }

        return matches;
    }

    /**
     * 判断是否向量匹配
     */
    private boolean isVectorMatch(String userQuestion, KnowledgeBase kb) {
        if (kb.getEsIndex() == null || kb.getEsIndex().isBlank()) {
            return false;
        }
        if (embeddingService == null || elasticsearchService == null) {
            return false;
        }

        try {
            float[] queryVector = embeddingService.embed(userQuestion);
            List<Map<String, Object>> results = elasticsearchService.similaritySearchRaw(
                    kb.getEsIndex(), queryVector, 1
            );
            return !results.isEmpty();
        } catch (Exception e) {
            log.warn("知识库{}向量匹配判断失败: {}", kb.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * 计算向量匹配分数
     */
    private int calculateVectorScore(String userQuestion, KnowledgeBase kb) {
        if (kb.getEsIndex() == null || kb.getEsIndex().isBlank()) {
            return 0;
        }
        if (embeddingService == null || elasticsearchService == null) {
            return 0;
        }

        try {
            float[] queryVector = embeddingService.embed(userQuestion);
            List<Map<String, Object>> results = elasticsearchService.similaritySearchRaw(
                    kb.getEsIndex(), queryVector, vectorMatchTopK
            );

            if (results.isEmpty()) {
                return 0;
            }

            float totalSimilarity = 0;
            for (Map<String, Object> doc : results) {
                Object sim = doc.get("_score");
                if (sim != null) {
                    totalSimilarity += Float.parseFloat(sim.toString());
                }
            }

            int avgScore = (int) (totalSimilarity / results.size() * 10);
            return Math.max(avgScore, 0);

        } catch (Exception e) {
            log.warn("计算向量匹配分数失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 匹配结果包装类
     */
    private static class KnowledgeBaseMatch {
        KnowledgeBase kb;
        int score;
        Set<String> matchType = new HashSet<>();
    }
}
