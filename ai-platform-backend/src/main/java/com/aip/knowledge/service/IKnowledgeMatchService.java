package com.aip.knowledge.service;

import com.aip.knowledge.entity.KnowledgeBase;

import java.util.List;
import java.util.Optional;

/**
 * 知识库匹配服务接口
 * 根据用户问题自动匹配最相关的知识库
 */
public interface IKnowledgeMatchService {

    /**
     * 根据用户问题匹配最相关的知识库
     *
     * @param userQuestion 用户问题
     * @return 匹配的知识库，如果没有匹配则返回空
     */
    Optional<KnowledgeBase> matchBest(String userQuestion);

    /**
     * 根据用户问题匹配多个相关知识库
     *
     * @param userQuestion 用户问题
     * @param limit 最大返回数量
     * @return 匹配的知识库列表
     */
    List<KnowledgeBase> matchTop(String userQuestion, int limit);

    /**
     * 判断问题是否匹配某个知识库
     *
     * @param userQuestion 用户问题
     * @param knowledgeBase 知识库
     * @return 是否匹配
     */
    boolean isMatch(String userQuestion, KnowledgeBase knowledgeBase);

    /**
     * 计算匹配分数
     *
     * @param userQuestion 用户问题
     * @param knowledgeBase 知识库
     * @return 匹配分数
     */
    int calculateMatchScore(String userQuestion, KnowledgeBase knowledgeBase);
}
