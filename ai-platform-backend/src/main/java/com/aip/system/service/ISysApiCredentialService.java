package com.aip.system.service;

import com.aip.common.result.PageResult;
import com.aip.system.dto.ApiCredentialDTO;
import com.aip.system.dto.ApiCredentialStatsVO;
import com.aip.system.dto.ApiCredentialVO;
import com.aip.system.entity.SysApiCredential;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

/**
 * API凭证服务接口
 */
public interface ISysApiCredentialService {

    /**
     * 分页查询凭证
     */
    PageResult<ApiCredentialVO> page(String keyword, int page, int size);

    /**
     * 根据ID查询
     */
    Optional<SysApiCredential> getById(String id);

    /**
     * 根据API Key查询
     */
    Optional<SysApiCredential> findByApiKey(String apiKey);

    /**
     * 根据AppId查询
     */
    Optional<SysApiCredential> findByAppId(String appId);

    /**
     * 创建凭证
     * 返回完整的密钥（仅此时返回）
     */
    ApiCredentialVO create(ApiCredentialDTO dto);

    /**
     * 更新凭证
     */
    void update(String id, ApiCredentialDTO dto);

    /**
     * 删除凭证
     */
    void delete(String id);

    /**
     * 修改状态
     */
    void updateStatus(String id, Integer status);

    /**
     * 重置密钥
     * 返回新密钥（仅此时返回）
     */
    ApiCredentialVO resetSecret(String id);

    /**
     * 获取凭证统计
     */
    ApiCredentialStatsVO getStats(String id);

    /**
     * 获取所有启用的凭证
     */
    List<SysApiCredential> listActive();

    /**
     * 检查API Key是否存在
     */
    boolean existsByApiKey(String apiKey);

    /**
     * 检查AppId是否存在
     */
    boolean existsByAppId(String appId);
}
