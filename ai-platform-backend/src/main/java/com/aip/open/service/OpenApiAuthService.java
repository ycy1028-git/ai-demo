package com.aip.open.service;

import com.aip.common.utils.ApiKeyEncryptUtil;
import com.aip.system.entity.SysApiCredential;
import com.aip.system.service.ISysApiCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 开放API鉴权服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenApiAuthService {

    private final ISysApiCredentialService credentialService;

    /**
     * 鉴权（API Key + Secret）
     *
     * @param apiKey    API Key
     * @param apiSecret API Secret
     * @return 鉴权结果
     */
    public AuthResult authenticate(String apiKey, String apiSecret) {
        if (apiKey == null || apiKey.isEmpty()) {
            return AuthResult.fail("API Key不能为空");
        }
        if (apiSecret == null || apiSecret.isEmpty()) {
            return AuthResult.fail("API Secret不能为空");
        }

        // 查找凭证
        var credentialOpt = credentialService.findByApiKey(apiKey);
        if (credentialOpt.isEmpty()) {
            log.warn("API Key不存在: {}", apiKey);
            return AuthResult.fail("API Key无效");
        }

        SysApiCredential credential = credentialOpt.get();

        // 验证Secret
        String encryptedSecret = ApiKeyEncryptUtil.encrypt(apiSecret);
        if (!encryptedSecret.equals(credential.getApiSecret())) {
            log.warn("API Secret验证失败: {}", apiKey);
            return AuthResult.fail("API Secret无效");
        }

        // 检查状态
        if (credential.getStatus() == null || credential.getStatus() != 1) {
            log.warn("凭证已禁用: {}", apiKey);
            return AuthResult.fail("凭证已禁用");
        }

        // 检查过期
        if (credential.getExpireTime() != null && credential.getExpireTime().isBefore(Instant.now())) {
            log.warn("凭证已过期: {}, 过期时间: {}", apiKey, credential.getExpireTime());
            return AuthResult.fail("凭证已过期");
        }

        log.info("开放API鉴权成功: appId={}", credential.getAppId());

        return AuthResult.success(
                credential.getId(),
                credential.getAppId(),
                credential.getRateLimitQps(),
                credential.getDailyQuota(),
                credential.getMonthlyQuota()
        );
    }

    /**
     * 验证专家访问权限
     *
     * @param credential 凭证
     * @param expertCode 专家编码
     * @return 是否有权限
     */
    public boolean canAccessExpert(SysApiCredential credential, String expertCode) {
        if (expertCode == null || expertCode.isEmpty()) {
            return true;
        }
        return credential.canAccessExpert(expertCode);
    }

    /**
     * 检查额度是否用尽
     *
     * @param credential 凭证
     * @return 额度检查结果
     */
    public QuotaCheckResult checkQuota(SysApiCredential credential) {
        // 检查每日额度
        if (credential.getDailyQuota() != null && credential.getDailyQuota() > 0) {
            checkAndResetDaily(credential);
            if (credential.getTodayCalls() >= credential.getDailyQuota()) {
                return new QuotaCheckResult(false, "每日额度已用尽");
            }
        }

        // 检查每月额度
        if (credential.getMonthlyQuota() != null && credential.getMonthlyQuota() > 0) {
            checkAndResetMonthly(credential);
            if (credential.getMonthlyCalls() >= credential.getMonthlyQuota()) {
                return new QuotaCheckResult(false, "每月额度已用尽");
            }
        }

        return new QuotaCheckResult(true, null);
    }

    /**
     * 检查并重置每日计数
     */
    private void checkAndResetDaily(SysApiCredential credential) {
        LocalDate today = LocalDate.now();
        if (credential.getDailyResetDate() == null ||
            !credential.getDailyResetDate().equals(today)) {
            credential.setTodayCalls(0);
            credential.setDailyResetDate(today);
        }
    }

    /**
     * 检查并重置每月计数
     */
    private void checkAndResetMonthly(SysApiCredential credential) {
        String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        if (credential.getMonthlyResetDate() == null ||
            !credential.getMonthlyResetDate().equals(currentMonth)) {
            credential.setMonthlyCalls(0L);
            credential.setMonthlyResetDate(currentMonth);
        }
    }

    /**
     * 额度检查结果
     */
    public static class QuotaCheckResult {
        private final boolean passed;
        private final String message;

        public QuotaCheckResult(boolean passed, String message) {
            this.passed = passed;
            this.message = message;
        }

        public boolean isPassed() {
            return passed;
        }

        public String getMessage() {
            return message;
        }
    }
}
