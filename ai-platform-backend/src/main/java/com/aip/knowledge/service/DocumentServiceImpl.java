package com.aip.knowledge.service;

import com.aip.common.exception.BusinessException;
import com.aip.knowledge.config.MinioConfig;
import com.aip.knowledge.dto.KnowledgeItemDTO;
import com.aip.knowledge.entity.Document;
import com.aip.knowledge.entity.KnowledgeBase;
import com.aip.knowledge.entity.KnowledgeItem;
import com.aip.knowledge.mapper.DocumentMapper;
import com.aip.knowledge.mapper.KnowledgeBaseMapper;
import com.aip.knowledge.mapper.KnowledgeItemMapper;
import com.aip.common.util.UuidV7Utils;
import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档服务实现
 */
@Slf4j
@Service
public class DocumentServiceImpl implements IDocumentService {

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private KnowledgeItemMapper knowledgeItemMapper;

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private IKnowledgeItemService knowledgeItemService;

    @Autowired
    private IKnowledgeBaseService knowledgeBaseService;

    private final Tika tika = new Tika();

    private static final String[] ALLOWED_TYPES = {
            "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "html", "htm", "xml", "json", "md", "csv"
    };

    @Override
    @Transactional
    public Document upload(MultipartFile file, String kbId, String name) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(kbId)
                .orElseThrow(() -> new BusinessException("知识库不存在"));

        String originalName = file.getOriginalFilename();
        String fileType = getFileExtension(originalName);
        if (!isAllowedType(fileType)) {
            throw new BusinessException("不支持的文件类型: " + fileType);
        }

        String minioPath = generateMinioPath(kbId, originalName);

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(minioPath)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            Document document = new Document();
            document.setKbId(kbId);
            document.setName(name != null && !name.isBlank() ? name : originalName);
            document.setOriginalName(originalName);
            document.setFileType(fileType);
            document.setFileSize(file.getSize());
            document.setMinioPath(minioPath);
            document.setExtractStatus(0);
            document.setChunkCount(0);

            document = documentMapper.save(document);

            log.info("文档上传成功: {} -> {}", document.getId(), minioPath);
            return document;

        } catch (Exception e) {
            log.error("文档上传失败: {}", originalName, e);
            throw new BusinessException("文档上传失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public String extractText(String documentId) {
        Document document = documentMapper.findById(documentId)
                .orElseThrow(() -> new BusinessException("文档不存在"));

        try {
            document.setExtractStatus(1);
            documentMapper.save(document);

            GetObjectResponse response = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(document.getMinioPath())
                            .build()
            );

            String text = tika.parseToString(response);
            response.close();

            document.setExtractText(text);
            document.setExtractStatus(2);
            document.setPageCount(estimatePageCount(text, document.getFileType()));
            documentMapper.save(document);

            log.info("文档文本提取完成: {} -> {} 字符", documentId, text.length());
            return text;

        } catch (Exception e) {
            log.error("文档文本提取失败: {}", documentId, e);
            document.setExtractStatus(3);
            document.setErrorMsg(e.getMessage());
            documentMapper.save(document);
            throw new BusinessException("文档文本提取失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public KnowledgeItem createKnowledgeItemFromDocument(String documentId) {
        Document document = documentMapper.findById(documentId)
                .orElseThrow(() -> new BusinessException("文档不存在"));

        if (document.getExtractText() == null || document.getExtractText().isBlank()) {
            throw new BusinessException("文档尚未提取文本，请先调用提取接口");
        }

        if (document.getExtractStatus() != 2) {
            throw new BusinessException("文档提取状态异常: " + document.getExtractStatus());
        }

        KnowledgeItemDTO dto = new KnowledgeItemDTO();
        dto.setKbId(document.getKbId());
        dto.setTitle(document.getName());
        dto.setContent(document.getExtractText());
        dto.setSourceType("document");
        dto.setSourceDocId(documentId);
        dto.setStatus(1);
        dto.setMinioPath(document.getMinioPath());
        dto.setOriginalFileName(document.getOriginalName());
        dto.setFileType(document.getFileType());

        KnowledgeItem item = knowledgeItemService.create(dto);

        document.setChunkCount(item.getVectorChunks());
        documentMapper.save(document);

        log.info("从文档创建知识条目: {} -> {}", documentId, item.getId());
        return item;
    }

    @Override
    public List<Document> listByKbId(String kbId) {
        return documentMapper.findByKbId(kbId);
    }

    @Override
    public Document getById(String id) {
        return documentMapper.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public void delete(String id) {
        Document document = documentMapper.findById(id)
                .orElseThrow(() -> new BusinessException("文档不存在"));

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(document.getMinioPath())
                            .build()
            );
        } catch (Exception e) {
            log.warn("从MinIO删除文件失败: {}", document.getMinioPath(), e);
        }

        documentMapper.deleteById(id);
        log.info("删除文档: {}", id);
    }

    @Override
    public String getDownloadUrl(String id) {
        Document document = documentMapper.findById(id)
                .orElseThrow(() -> new BusinessException("文档不存在"));

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(document.getMinioPath())
                            .expiry(3600)
                            .build()
            );
        } catch (Exception e) {
            log.error("获取下载链接失败: {}", id, e);
            throw new BusinessException("获取下载链接失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void batchExtract(String kbId) {
        List<Document> documents = listByKbId(kbId);
        int success = 0;
        int failed = 0;

        for (Document doc : documents) {
            if (doc.getExtractStatus() != 2) {
                try {
                    extractText(doc.getId());
                    success++;
                } catch (Exception e) {
                    log.error("批量提取失败: {}", doc.getId(), e);
                    failed++;
                }
            }
        }

        log.info("批量提取完成: 成功={}, 失败={}", success, failed);
    }

    @Override
    @Transactional
    public Map<String, Object> uploadAndAutoMatch(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String fileType = getFileExtension(originalName);
        if (!isAllowedType(fileType)) {
            throw new BusinessException("不支持的文件类型: " + fileType);
        }

        String previewText;
        try {
            previewText = tika.parseToString(file.getInputStream());
        } catch (Exception e) {
            log.warn("文件文本提取失败（非致命）: {}", originalName, e);
            previewText = originalName;
        }

        String matchQuery = (originalName != null ? originalName : "") + " " + (previewText != null ? previewText : "");
        List<KnowledgeBase> matchedKbs = knowledgeBaseService.matchByQuestion(matchQuery);

        String targetKbId;
        if (!matchedKbs.isEmpty()) {
            targetKbId = matchedKbs.get(0).getId();
        } else {
            List<KnowledgeBase> allEnabled = knowledgeBaseMapper.findAll().stream()
                    .filter(kb -> kb.getStatus() != null && kb.getStatus() == 1)
                    .toList();
            if (allEnabled.isEmpty()) {
                throw new BusinessException("系统暂无可用的知识库，请先创建知识库");
            }
            targetKbId = allEnabled.get(0).getId();
        }

        Document document = upload(file, targetKbId, null);

        Map<String, Object> result = new HashMap<>();
        result.put("document", document);
        result.put("matchedBases", matchedKbs);
        result.put("targetBaseId", targetKbId);

        if (!matchedKbs.isEmpty()) {
            log.info("文件上传并自动匹配: {} -> 知识库[{}]{}",
                    originalName,
                    targetKbId,
                    matchedKbs.stream().map(KnowledgeBase::getName).toList());
        } else {
            log.info("文件上传无匹配知识库，自动归入: {} -> 知识库[{}]",
                    originalName, targetKbId);
        }

        return result;
    }

    private String generateMinioPath(String kbId, String fileName) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(kbId)
                .orElseThrow(() -> new BusinessException("知识库不存在"));

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UuidV7Utils.generateUuidV7String().substring(0, 8);
        String extension = getFileExtension(fileName);

        String prefix = knowledgeBase.getOssPathPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "documents/";
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }

        return String.format("%s%s/%s/%s.%s", prefix, kbId, date, uuid, extension);
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    private boolean isAllowedType(String extension) {
        for (String allowed : ALLOWED_TYPES) {
            if (allowed.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    private Integer estimatePageCount(String text, String fileType) {
        int charsPerPage = 1500;
        switch (fileType.toLowerCase()) {
            case "pdf":
            case "doc":
            case "docx":
            case "ppt":
            case "pptx":
                charsPerPage = 1000;
                break;
            case "xls":
            case "xlsx":
                charsPerPage = 500;
                break;
            default:
                charsPerPage = 1500;
        }
        return (int) Math.ceil((double) text.length() / charsPerPage);
    }
}
