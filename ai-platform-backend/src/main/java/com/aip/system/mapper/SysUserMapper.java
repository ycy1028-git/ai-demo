package com.aip.system.mapper;

import com.aip.system.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * 用户Mapper
 */
@Repository
public interface SysUserMapper extends JpaRepository<SysUser, String> {

    /**
     * 根据用户名查询
     */
    Optional<SysUser> findByUsername(String username);

    /**
     * 根据用户名查询（未删除）
     */
    @Query("SELECT u FROM SysUser u WHERE u.username = :username AND u.deleted = false")
    Optional<SysUser> findActiveByUsername(@Param("username") String username);

    /**
     * 根据邮箱查询
     */
    Optional<SysUser> findByEmail(String email);

    /**
     * 根据手机号查询
     */
    Optional<SysUser> findByPhone(String phone);

    /**
     * 判断用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 判断用户名是否存在（排除自身）
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM SysUser u WHERE u.username = :username AND u.id != :excludeId")
    boolean existsByUsernameAndIdNot(@Param("username") String username, @Param("excludeId") String excludeId);

    /**
     * 判断邮箱是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 判断手机号是否存在
     */
    boolean existsByPhone(String phone);

    /**
     * 查询所有未删除的用户
     */
    @Query("SELECT u FROM SysUser u WHERE u.deleted = false")
    java.util.List<SysUser> findAllActive();

    long countByRoleId(String roleId);

    /**
     * 更新登录信息（跳过乐观锁版本检查）
     * 用于登录时记录最后登录时间和IP
     */
    @Modifying
    @Query(value = "UPDATE t_sys_user SET f_last_login_at = :lastLoginAt, f_last_login_ip = :lastLoginIp WHERE f_id = :userId", nativeQuery = true)
    void updateLoginInfo(@Param("userId") String userId, @Param("lastLoginAt") Instant lastLoginAt, @Param("lastLoginIp") String lastLoginIp);
}
