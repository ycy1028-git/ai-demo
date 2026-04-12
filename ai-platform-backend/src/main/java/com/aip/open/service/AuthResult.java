package com.aip.open.service;

import com.aip.system.entity.SysApiCredential;
import lombok.Getter;
import lombok.Setter;

/**
 * 开放API认证结果
 */
@Getter
@Setter
public class AuthResult {

    private boolean success;
    private String message;
    private String credentialId;
    private String appId;
    private Integer rateLimitQps;
    private Long dailyQuota;
    private Long monthlyQuota;

    public static AuthResult success(String credentialId, String appId,
                                     Integer rateLimitQps, Long dailyQuota, Long monthlyQuota) {
        AuthResult result = new AuthResult();
        result.success = true;
        result.credentialId = credentialId;
        result.appId = appId;
        result.rateLimitQps = rateLimitQps;
        result.dailyQuota = dailyQuota;
        result.monthlyQuota = monthlyQuota;
        return result;
    }

    public static AuthResult fail(String message) {
        AuthResult result = new AuthResult();
        result.success = false;
        result.message = message;
        return result;
    }
}
