package com.aip.system.controller;

import com.aip.common.result.PageResult;
import com.aip.common.result.Result;
import com.aip.system.dto.ApiCredentialDTO;
import com.aip.system.dto.ApiCredentialStatsVO;
import com.aip.system.dto.ApiCredentialVO;
import com.aip.system.service.ISysApiCredentialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * API凭证管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/credentials")
@RequiredArgsConstructor
public class SysApiCredentialController {

    private final ISysApiCredentialService apiCredentialService;

    /**
     * 分页查询凭证
     */
    @GetMapping
    public Result<PageResult<ApiCredentialVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(apiCredentialService.page(keyword, page, size));
    }

    /**
     * 根据ID查询详情
     */
    @GetMapping("/{id}")
    public Result<ApiCredentialVO> getById(@PathVariable String id) {
        return apiCredentialService.getById(id)
                .map(cred -> {
                    ApiCredentialVO vo = new ApiCredentialVO();
                    vo.setId(cred.getId());
                    vo.setName(cred.getName());
                    vo.setAppId(cred.getAppId());
                    vo.setApiKey(cred.getApiKey());
                    vo.setApiSecretLastFour(cred.getApiSecretLastFour());
                    vo.setRateLimitQps(cred.getRateLimitQps());
                    vo.setDailyQuota(cred.getDailyQuota());
                    vo.setMonthlyQuota(cred.getMonthlyQuota());
                    vo.setStatus(cred.getStatus());
                    vo.setStatusText(cred.getStatus() != null && cred.getStatus() == 1 ? "启用" : "禁用");
                    vo.setExpireTime(cred.getExpireTime() != null ?
                            java.time.LocalDateTime.ofInstant(cred.getExpireTime(), java.time.ZoneId.systemDefault()) : null);
                    vo.setTotalCalls(cred.getTotalCalls());
                    vo.setTodayCalls(cred.getTodayCalls());
                    vo.setMonthlyCalls(cred.getMonthlyCalls());
                    vo.setLastCalledAt(cred.getLastCalledAt() != null ?
                            java.time.LocalDateTime.ofInstant(cred.getLastCalledAt(), java.time.ZoneId.systemDefault()) : null);
                    vo.setCreateTime(cred.getCreateTime() != null ?
                            java.time.LocalDateTime.ofInstant(cred.getCreateTime(), java.time.ZoneId.systemDefault()) : null);
                    if (cred.getAllowedExperts() != null) {
                        try {
                            vo.setAllowedExperts(com.alibaba.fastjson2.JSON.parseArray(cred.getAllowedExperts(), String.class));
                        } catch (Exception ignored) {}
                    }
                    return vo;
                })
                .map(Result::ok)
                .orElse(Result.fail("凭证不存在"));
    }

    /**
     * 创建凭证
     * 注意：返回的apiSecret仅在此接口返回，请妥善保存
     */
    @PostMapping
    public Result<ApiCredentialVO> create(@Valid @RequestBody ApiCredentialDTO dto) {
        ApiCredentialVO vo = apiCredentialService.create(dto);
        return Result.ok(vo);
    }

    /**
     * 更新凭证
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody ApiCredentialDTO dto) {
        apiCredentialService.update(id, dto);
        return Result.ok();
    }

    /**
     * 删除凭证
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        apiCredentialService.delete(id);
        return Result.ok();
    }

    /**
     * 修改状态
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id, @RequestParam Integer status) {
        apiCredentialService.updateStatus(id, status);
        return Result.ok();
    }

    /**
     * 重置密钥
     * 注意：返回的新密钥仅在此接口返回，请妥善保存
     */
    @PostMapping("/{id}/reset-secret")
    public Result<ApiCredentialVO> resetSecret(@PathVariable String id) {
        ApiCredentialVO vo = apiCredentialService.resetSecret(id);
        return Result.ok(vo);
    }

    /**
     * 获取凭证统计
     */
    @GetMapping("/{id}/stats")
    public Result<ApiCredentialStatsVO> getStats(@PathVariable String id) {
        return Result.ok(apiCredentialService.getStats(id));
    }
}
