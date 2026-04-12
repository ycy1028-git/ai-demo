package com.aip.common.utils;

import cn.hutool.core.codec.Base64;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * API Key 加密工具类
 * 使用 AES-GCM 算法进行加密，支持盐值和 IV
 */
@Slf4j
public class ApiKeyEncryptUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int SALT_LENGTH = 16;

    private static final String ENCRYPT_PREFIX = "ENC(";
    private static final String ENCRYPT_SUFFIX = ")";

    private static final String DEFAULT_KEY = "AiPlatform2024SecretKey16Byte!";

    /**
     * 加密 API Key
     * 格式：ENC(base64(iv + salt + ciphertext))
     */
    public static String encrypt(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return apiKey;
        }

        if (isEncrypted(apiKey)) {
            return apiKey;
        }

        try {
            byte[] iv = generateRandomBytes(GCM_IV_LENGTH);
            byte[] salt = generateRandomBytes(SALT_LENGTH);

            SecretKey key = generateKey(salt);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            byte[] encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + salt.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(salt, 0, combined, iv.length, salt.length);
            System.arraycopy(encrypted, 0, combined, iv.length + salt.length, encrypted.length);

            String encoded = Base64.encode(combined);
            return ENCRYPT_PREFIX + encoded + ENCRYPT_SUFFIX;

        } catch (Exception e) {
            log.error("API Key 加密失败", e);
            throw new RuntimeException("API Key 加密失败", e);
        }
    }

    /**
     * 解密 API Key
     */
    public static String decrypt(String encryptedApiKey) {
        if (encryptedApiKey == null || encryptedApiKey.isEmpty()) {
            return encryptedApiKey;
        }

        if (!isEncrypted(encryptedApiKey)) {
            return encryptedApiKey;
        }

        try {
            String encoded = encryptedApiKey.substring(
                ENCRYPT_PREFIX.length(),
                encryptedApiKey.length() - ENCRYPT_SUFFIX.length()
            );

            byte[] combined = Base64.decode(encoded);

            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] salt = Arrays.copyOfRange(combined, GCM_IV_LENGTH, GCM_IV_LENGTH + SALT_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_LENGTH + SALT_LENGTH, combined.length);

            SecretKey key = generateKey(salt);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            byte[] decrypted = cipher.doFinal(ciphertext);
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("API Key 解密失败", e);
            throw new RuntimeException("API Key 解密失败", e);
        }
    }

    /**
     * 判断是否已加密
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPT_PREFIX) && value.endsWith(ENCRYPT_SUFFIX);
    }

    /**
     * 生成随机字节数组
     */
    private static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    /**
     * 使用盐值生成密钥
     */
    private static SecretKey generateKey(byte[] salt) throws Exception {
        byte[] keyBytes = DEFAULT_KEY.getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[keyBytes.length + salt.length];
        System.arraycopy(keyBytes, 0, combined, 0, keyBytes.length);
        System.arraycopy(salt, 0, combined, keyBytes.length, salt.length);

        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(combined);

        return new SecretKeySpec(Arrays.copyOf(hash, 16), "AES");
    }

    /**
     * 验证 API Key 格式是否有效
     */
    public static boolean isValidApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }
        if (isEncrypted(apiKey)) {
            try {
                decrypt(apiKey);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return apiKey.length() >= 10;
    }
}
