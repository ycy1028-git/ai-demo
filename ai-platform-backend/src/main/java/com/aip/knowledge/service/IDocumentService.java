package com.aip.knowledge.service;

import com.aip.knowledge.entity.Document;
import com.aip.knowledge.entity.KnowledgeBase;
import com.aip.knowledge.entity.KnowledgeItem;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 文档服务接口
 */
public interface IDocumentService {

    /**
     * 上传文档
     *
     * @param file 上传的文件
     * @param kbId 知识库ID（UUIDv7 无横杠字符串）
     * @param name 文档名称（可选）
     * @return 上传的文档记录
     */
    Document upload(MultipartFile file, String kbId, String name);

    /**
     * 使用Tika提取文档文本
     *
     * @param documentId 文档ID（UUIDv7 无横杠字符串）
     * @return 提取的文本
     */
    String extractText(String documentId);

    /**
     * 从提取的文本创建知识条目
     *
     * @param documentId 文档ID（UUIDv7 无横杠字符串）
     * @return 创建的知识条目
     */
    KnowledgeItem createKnowledgeItemFromDocument(String documentId);

    /**
     * 查询指定知识库的文档
     *
     * @param kbId 知识库ID（UUIDv7 无横杠字符串）
     * @return 文档列表
     */
    List<Document> listByKbId(String kbId);

    /**
     * 根据ID查询文档
     *
     * @param id 文档ID（UUIDv7 无横杠字符串）
     * @return 文档
     */
    Document getById(String id);

    /**
     * 删除文档
     *
     * @param id 文档ID（UUIDv7 无横杠字符串）
     */
    void delete(String id);

    /**
     * 获取文档下载URL
     *
     * @param id 文档ID（UUIDv7 无横杠字符串）
     * @return 下载URL
     */
    String getDownloadUrl(String id);

    /**
     * 批量提取文档文本
     *
     * @param kbId 知识库ID（UUIDv7 无横杠字符串）
     */
    void batchExtract(String kbId);

    /**
     * 上传文档并自动匹配知识库
     *
     * @param file 上传的文件
     * @return 匹配结果
     */
    Map<String, Object> uploadAndAutoMatch(MultipartFile file);
}
