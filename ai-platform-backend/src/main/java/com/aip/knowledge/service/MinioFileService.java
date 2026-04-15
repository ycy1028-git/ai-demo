package com.aip.knowledge.service;

import com.aip.common.exception.BusinessException;
import com.aip.knowledge.config.MinioConfig;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * MinIO文件预览/下载服务
 */
@Slf4j
@Service
public class MinioFileService {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    /**
     * 生成预览URL
     *
     * @param minioPath MinIO文件路径
     * @param expirySeconds URL有效期（秒），默认3600秒
     * @return 预览URL
     */
    public String generatePreviewUrl(String minioPath, int expirySeconds) {
        return generateUrl(minioPath, null, Method.GET, expirySeconds);
    }

    public String generatePreviewUrl(String minioPath, String bucketName, int expirySeconds) {
        return generateUrl(minioPath, bucketName, Method.GET, expirySeconds);
    }

    /**
     * 生成预览URL（默认1小时）
     */
    public String generatePreviewUrl(String minioPath) {
        return generatePreviewUrl(minioPath, 3600);
    }

    /**
     * 生成下载URL
     *
     * @param minioPath MinIO文件路径
     * @param fileName 下载时的文件名
     * @param expirySeconds URL有效期（秒），默认3600秒
     * @return 下载URL
     */
    public String generateDownloadUrl(String minioPath, String fileName, int expirySeconds) {
        return generateDownloadUrl(minioPath, null, fileName, expirySeconds);
    }

    public String generateDownloadUrl(String minioPath, String bucketName, String fileName, int expirySeconds) {
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(resolveBucketName(bucketName))
                            .object(minioPath)
                            .method(Method.GET)
                            .expiry(expirySeconds)
                            .extraQueryParams(Map.of("response-content-disposition",
                                    "attachment; filename=\"" + encodeFileName(fileName) + "\""))
                            .build()
            );
            log.debug("生成下载URL: {} -> {}", minioPath, url);
            return url;
        } catch (Exception e) {
            log.error("生成下载URL失败: {}", minioPath, e);
            throw new BusinessException("生成下载链接失败: " + e.getMessage());
        }
    }

    /**
     * 生成下载URL（默认1小时，使用原文件名）
     */
    public String generateDownloadUrl(String minioPath, String fileName) {
        return generateDownloadUrl(minioPath, fileName, 3600);
    }

    public String generateDownloadUrl(String minioPath, String bucketName, String fileName) {
        return generateDownloadUrl(minioPath, bucketName, fileName, 3600);
    }

    /**
     * 生成临时访问URL
     */
    private String generateUrl(String minioPath, String bucketName, Method method, int expirySeconds) {
        if (minioPath == null || minioPath.isBlank()) {
            throw new BusinessException("文件路径不能为空");
        }

        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(resolveBucketName(bucketName))
                            .object(minioPath)
                            .method(method)
                            .expiry(expirySeconds)
                            .build()
            );
            log.debug("生成URL: {} -> {}", minioPath, url);
            return url;
        } catch (Exception e) {
            log.error("生成预览URL失败: {}", minioPath, e);
            throw new BusinessException("生成预览链接失败: " + e.getMessage());
        }
    }

    /**
     * URL编码文件名
     */
    private String encodeFileName(String fileName) {
        try {
            return URLEncoder.encode(fileName, "UTF-8");
        } catch (Exception e) {
            return fileName;
        }
    }

    private String resolveBucketName(String bucketName) {
        return StringUtils.hasText(bucketName) ? bucketName : minioConfig.getBucketName();
    }
}
