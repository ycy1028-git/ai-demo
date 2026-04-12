package com.aip.system.service;

import com.aip.common.result.PageResult;
import com.aip.system.dto.UserDTO;
import com.aip.system.entity.SysUser;

import java.util.List;

/**
 * 用户服务接口
 */
public interface ISysUserService {

    /**
     * 分页查询用户
     *
     * @param keyword 关键词
     * @param status  状态
     * @param page    页码
     * @param size    每页大小
     * @return 分页结果
     */
    PageResult<SysUser> page(String keyword, Integer status, int page, int size);

    /**
     * 查询所有用户
     *
     * @return 用户列表
     */
    List<SysUser> list();

    /**
     * 根据ID查询
     *
     * @param id 用户ID（UUIDv7 无横杠字符串）
     * @return 用户实体
     */
    SysUser getById(String id);

    /**
     * 创建用户
     *
     * @param dto 创建参数
     * @return 创建的用户
     */
    SysUser create(UserDTO dto);

    /**
     * 更新用户
     *
     * @param id  用户ID（UUIDv7 无横杠字符串）
     * @param dto 更新参数
     * @return 更新后的用户
     */
    SysUser update(String id, UserDTO dto);

    /**
     * 删除用户
     *
     * @param id 用户ID（UUIDv7 无横杠字符串）
     */
    void delete(String id);

    /**
     * 重置密码
     *
     * @param id          用户ID（UUIDv7 无横杠字符串）
     * @param newPassword 新密码
     */
    void resetPassword(String id, String newPassword);

    /**
     * 修改状态
     *
     * @param id     用户ID（UUIDv7 无横杠字符串）
     * @param status 状态值
     */
    void updateStatus(String id, Integer status);
}
