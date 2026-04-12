package com.aip.common.utils;

import java.util.regex.Pattern;

/**
 * 密码加密工具类（统一使用 BCrypt）
 */
public class PasswordEncoder {

    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private static final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder ENCODER =
        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    /**
     * 密码加密（BCrypt）
     */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 密码验证
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }

        String normalizedPassword = encodedPassword;
        if (encodedPassword.startsWith("$2y$")) {
            normalizedPassword = "$2a$" + encodedPassword.substring(4);
        }

        return ENCODER.matches(rawPassword, normalizedPassword);
    }

    /**
     * 判断是否为 BCrypt 加密的密码
     */
    public static boolean isBCryptPassword(String encodedPassword) {
        return BCRYPT_PATTERN.matcher(encodedPassword).matches();
    }
}
