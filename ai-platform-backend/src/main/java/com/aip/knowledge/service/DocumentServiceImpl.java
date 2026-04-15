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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    @Value("${kb.preview.libreoffice-bin:soffice}")
    private String libreOfficeBin;

    @Value("${kb.preview.convert-timeout-seconds:120}")
    private Integer convertTimeoutSeconds;

    @Value("${kb.preview.converter-mode:local}")
    private String converterMode;

    @Value("${kb.preview.docker-container-name:ai-platform-libreoffice}")
    private String dockerContainerName;

    @Value("${kb.preview.shared-host-dir:/Users/ycy/work/docker-container/libreOffice/storage}")
    private String sharedHostDir;

    @Value("${kb.preview.shared-container-dir:/storage}")
    private String sharedContainerDir;

    private final Tika tika = new Tika();

    private static final Set<String> DIRECT_PREVIEW_TYPES = new HashSet<>(Arrays.asList(
            "pdf", "txt", "md", "html", "htm", "xml", "json", "csv"
    ));

    private static final Set<String> OFFICE_CONVERT_TYPES = new HashSet<>(Arrays.asList(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    ));

    private static final int CONTENT_SUMMARY_MAX_LENGTH = 500;
    private static final int SUMMARY_MAX_LENGTH = 120;
    private static final List<String> KEY_SENTENCE_HINTS = Arrays.asList(
            "摘要", "总结", "结论", "注意", "说明", "步骤", "流程", "建议", "FAQ", "Q", "A", "如何", "联系",
            "问题", "处理", "支持", "important", "note", "summary", "conclusion"
    );

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

        String bucketName = resolveBucketName(knowledgeBase);

        try {
            String extractedText = null;
            Integer extractStatus = 0;
            Integer pageCount = null;
            String errorMsg = null;
            try {
                extractedText = tika.parseToString(file.getInputStream());
                extractStatus = 2;
                pageCount = estimatePageCount(extractedText, fileType);
            } catch (Exception e) {
                extractStatus = 3;
                errorMsg = e.getMessage();
                log.warn("上传后自动提取文本失败: {}", originalName, e);
            }

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
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
            document.setExtractText(extractedText);
            document.setExtractStatus(extractStatus);
            document.setPageCount(pageCount);
            document.setErrorMsg(errorMsg);
            document.setChunkCount(0);

            document = documentMapper.save(document);

            log.info("文档上传成功: {} -> bucket={}, path={}", document.getId(), bucketName, minioPath);
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

        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(document.getKbId())
                .orElseThrow(() -> new BusinessException("知识库不存在"));
        String bucketName = resolveBucketName(knowledgeBase);

        try {
            document.setExtractStatus(1);
            documentMapper.save(document);

            GetObjectResponse response = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(document.getMinioPath())
                            .build()
            );

            String text = tika.parseToString(response);
            response.close();

            document.setExtractText(text);
            document.setExtractStatus(2);
            document.setPageCount(estimatePageCount(text, document.getFileType()));
            documentMapper.save(document);

            log.info("文档文本提取完成: {} -> bucket={}, {} 字符", documentId, bucketName, text.length());
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
        dto.setContent(buildContentSummary(document.getExtractText()));
        dto.setSummary(buildShortSummary(document.getExtractText()));
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

        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(document.getKbId())
                .orElseThrow(() -> new BusinessException("知识库不存在"));
        String bucketName = resolveBucketName(knowledgeBase);

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(document.getMinioPath())
                            .build()
            );

            String previewPath = buildPreviewPdfPath(document.getMinioPath());
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(previewPath)
                            .build()
            );
        } catch (Exception e) {
            log.warn("从MinIO删除文件失败: {}", document.getMinioPath(), e);
        }

        documentMapper.deleteById(id);
        log.info("删除文档: {} -> bucket={}", id, bucketName);
    }

    @Override
    public String getDownloadUrl(String id) {
        Document document = documentMapper.findById(id)
                .orElseThrow(() -> new BusinessException("文档不存在"));

        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(document.getKbId())
                .orElseThrow(() -> new BusinessException("知识库不存在"));
        String bucketName = resolveBucketName(knowledgeBase);

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucketName)
                            .object(document.getMinioPath())
                            .method(io.minio.http.Method.GET)
                            .expiry(3600)
                            .build()
            );
        } catch (Exception e) {
            log.error("获取下载链接失败: {}", id, e);
            throw new BusinessException("获取下载链接失败: " + e.getMessage());
        }
    }

    @Override
    public String getPreviewUrl(String id) {
        Document document = documentMapper.findById(id)
                .orElseThrow(() -> new BusinessException("文档不存在"));

        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(document.getKbId())
                .orElseThrow(() -> new BusinessException("知识库不存在"));
        String bucketName = resolveBucketName(knowledgeBase);

        String fileType = document.getFileType() == null ? "" : document.getFileType().toLowerCase();
        String objectPath = document.getMinioPath();
        if (OFFICE_CONVERT_TYPES.contains(fileType)) {
            objectPath = ensurePreviewPdf(document, bucketName);
        } else if (!DIRECT_PREVIEW_TYPES.contains(fileType)) {
            log.info("文件类型不支持直接预览，回退原文件直链: id={}, type={}", id, fileType);
        }

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucketName)
                            .object(objectPath)
                            .method(io.minio.http.Method.GET)
                            .expiry(3600)
                            .build()
            );
        } catch (Exception e) {
            log.error("获取预览链接失败: {}", id, e);
            throw new BusinessException("获取预览链接失败: " + e.getMessage());
        }
    }

    private String ensurePreviewPdf(Document document, String bucketName) {
        String previewPath = buildPreviewPdfPath(document.getMinioPath());
        if (objectExists(bucketName, previewPath)) {
            return previewPath;
        }

        Path workDir = null;
        try {
            boolean dockerMode = "docker".equalsIgnoreCase(converterMode);
            workDir = dockerMode
                    ? createDockerSharedWorkDir()
                    : Files.createTempDirectory("kb-preview-");
            String ext = getFileExtension(document.getOriginalName());
            if (ext == null || ext.isBlank()) {
                ext = document.getFileType();
            }
            Path sourceFile = workDir.resolve("source." + ext);

            try (InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucketName).object(document.getMinioPath()).build())) {
                Files.copy(inputStream, sourceFile, StandardCopyOption.REPLACE_EXISTING);
            }

            String output = convertToPdf(sourceFile, workDir, dockerMode);
            Path generatedPdf = workDir.resolve(removeExtension(sourceFile.getFileName().toString()) + ".pdf");
            if (!Files.exists(generatedPdf)) {
                throw new BusinessException("LibreOffice未生成PDF: " + output);
            }

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(previewPath)
                            .stream(Files.newInputStream(generatedPdf), Files.size(generatedPdf), -1)
                            .contentType("application/pdf")
                            .build()
            );

            return previewPath;
        } catch (Exception e) {
            log.error("文档转换预览PDF失败: docId={}, path={}", document.getId(), document.getMinioPath(), e);
            throw new BusinessException("文档预览转换失败，请确认服务已安装LibreOffice");
        } finally {
            if (workDir != null) {
                try {
                    Files.walk(workDir)
                            .sorted((a, b) -> b.toString().compareTo(a.toString()))
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (Exception ignored) {
                                }
                            });
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Path createDockerSharedWorkDir() throws Exception {
        Path base = Path.of(sharedHostDir, "tmp", UUID.randomUUID().toString());
        Files.createDirectories(base);
        return base;
    }

    private String convertToPdf(Path sourceFile, Path outDir, boolean dockerMode) throws Exception {
        if (dockerMode) {
            return convertToPdfWithDockerLibreOffice(sourceFile, outDir);
        }
        return convertToPdfWithLocalLibreOffice(sourceFile, outDir);
    }

    private String convertToPdfWithLocalLibreOffice(Path sourceFile, Path outDir) throws Exception {
        return runConvertProcess(List.of(
                libreOfficeBin,
                "--headless",
                "--nologo",
                "--nolockcheck",
                "--norestore",
                "--convert-to",
                "pdf",
                sourceFile.toString(),
                "--outdir",
                outDir.toString()
        ));
    }

    private String convertToPdfWithDockerLibreOffice(Path sourceFile, Path outDir) throws Exception {
        Path basePath = Path.of(sharedHostDir).toAbsolutePath().normalize();
        Path sourceAbs = sourceFile.toAbsolutePath().normalize();
        Path outAbs = outDir.toAbsolutePath().normalize();

        if (!sourceAbs.startsWith(basePath) || !outAbs.startsWith(basePath)) {
            throw new BusinessException("共享目录配置错误，无法在容器中访问待转换文件");
        }

        String sourceInContainer = toContainerPath(basePath.relativize(sourceAbs));
        String outDirInContainer = toContainerPath(basePath.relativize(outAbs));

        return runConvertProcess(List.of(
                "docker",
                "exec",
                dockerContainerName,
                libreOfficeBin,
                "--headless",
                "--nologo",
                "--nolockcheck",
                "--norestore",
                "--convert-to",
                "pdf",
                sourceInContainer,
                "--outdir",
                outDirInContainer
        ));
    }

    private String runConvertProcess(List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output;
        try (InputStream processInput = process.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            processInput.transferTo(baos);
            output = baos.toString();
        }

        boolean finished = process.waitFor(convertTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new BusinessException("LibreOffice转换超时");
        }

        if (process.exitValue() != 0) {
            throw new BusinessException("LibreOffice转换失败: " + output);
        }

        return output;
    }

    private String toContainerPath(Path relative) {
        String rel = relative.toString().replace('\\', '/');
        String base = sharedContainerDir.endsWith("/")
                ? sharedContainerDir.substring(0, sharedContainerDir.length() - 1)
                : sharedContainerDir;
        return base + "/" + rel;
    }

    private String buildPreviewPdfPath(String minioPath) {
        int slashIndex = minioPath.lastIndexOf('/');
        String dir = slashIndex >= 0 ? minioPath.substring(0, slashIndex + 1) : "";
        String fileName = slashIndex >= 0 ? minioPath.substring(slashIndex + 1) : minioPath;
        String baseName = removeExtension(fileName);
        return dir + "_preview/" + baseName + ".pdf";
    }

    private String removeExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private boolean objectExists(String bucketName, String objectPath) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectPath)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildContentSummary(String text) {
        String normalized = normalizeText(text);
        if (normalized.isBlank()) {
            return "";
        }

        List<String> paragraphs = splitParagraphs(text);
        if (paragraphs.isEmpty()) {
            return trimToLimit(normalized, CONTENT_SUMMARY_MAX_LENGTH);
        }

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String paragraph : paragraphs) {
            List<String> sentences = splitSentences(paragraph);
            if (sentences.isEmpty()) {
                continue;
            }

            selected.add(sentences.get(0));
            String keySentence = pickBestSentence(sentences);
            if (keySentence != null) {
                selected.add(keySentence);
            }

            String joined = String.join("\n", selected);
            if (joined.length() >= CONTENT_SUMMARY_MAX_LENGTH) {
                break;
            }
        }

        String result = String.join("\n", selected);
        if (result.isBlank()) {
            result = normalized;
        }
        return trimToLimit(result, CONTENT_SUMMARY_MAX_LENGTH);
    }

    private String buildShortSummary(String text) {
        String normalized = normalizeText(text);
        if (normalized.isBlank()) {
            return "";
        }

        List<String> paragraphs = splitParagraphs(text);
        if (paragraphs.isEmpty()) {
            return trimToLimit(normalized, SUMMARY_MAX_LENGTH);
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String paragraph : paragraphs) {
            List<String> sentences = splitSentences(paragraph);
            if (sentences.isEmpty()) {
                continue;
            }

            String keySentence = pickBestSentence(sentences);
            if (keySentence != null) {
                candidates.add(keySentence);
            } else {
                candidates.add(sentences.get(0));
            }

            if (candidates.size() >= 4) {
                break;
            }
        }

        String result = String.join("；", candidates);
        if (result.isBlank()) {
            result = normalized;
        }
        return trimToLimit(result, SUMMARY_MAX_LENGTH);
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private List<String> splitParagraphs(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalizedBreaks = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] parts = normalizedBreaks.split("\\n{2,}");
        if (parts.length <= 1) {
            parts = normalizedBreaks.split("\\n");
        }

        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String paragraph = normalizeText(part);
            if (paragraph.length() >= 8) {
                result.add(paragraph);
            }
        }
        return result;
    }

    private List<String> splitSentences(String paragraph) {
        if (paragraph == null || paragraph.isBlank()) {
            return List.of();
        }

        String splitReady = paragraph
                .replace("。", "。\n")
                .replace("！", "！\n")
                .replace("？", "？\n")
                .replace(";", ";\n")
                .replace("；", "；\n")
                .replace("!", "!\n")
                .replace("?", "?\n");

        String[] raw = splitReady.split("\\n+");
        List<String> sentences = new ArrayList<>();
        for (String s : raw) {
            String sentence = normalizeText(s);
            if (sentence.length() >= 6) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    private String pickBestSentence(List<String> sentences) {
        if (sentences == null || sentences.isEmpty()) {
            return null;
        }

        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            int score = sentenceScore(sentence, i);
            if (score > bestScore) {
                bestScore = score;
                best = sentence;
            }
        }
        return best;
    }

    private int sentenceScore(String sentence, int index) {
        int score = 0;
        int len = sentence.length();

        if (index == 0) {
            score += 3;
        }
        if (len >= 12 && len <= 120) {
            score += 3;
        } else if (len >= 8) {
            score += 1;
        }

        String lower = sentence.toLowerCase();
        for (String hint : KEY_SENTENCE_HINTS) {
            if (lower.contains(hint.toLowerCase())) {
                score += 4;
                break;
            }
        }

        if (sentence.contains(":") || sentence.contains("：")) {
            score += 1;
        }
        if (sentence.startsWith("Q") || sentence.startsWith("A")) {
            score += 2;
        }

        return score;
    }

    private String trimToLimit(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = normalizeText(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
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

    private String resolveBucketName(KnowledgeBase knowledgeBase) {
        if (knowledgeBase == null) {
            return minioConfig.getBucketName();
        }
        return StringUtils.hasText(knowledgeBase.getBucketName())
                ? knowledgeBase.getBucketName()
                : minioConfig.getBucketName();
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
