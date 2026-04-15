package com.aip.system.mapper;

import com.aip.system.entity.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysRoleMapper extends JpaRepository<SysRole, String> {

    Optional<SysRole> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByName(String name);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM SysRole r WHERE r.code = :code AND r.id != :excludeId")
    boolean existsByCodeAndIdNot(@Param("code") String code, @Param("excludeId") String excludeId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM SysRole r WHERE r.name = :name AND r.id != :excludeId")
    boolean existsByNameAndIdNot(@Param("name") String name, @Param("excludeId") String excludeId);

    @Query("SELECT r FROM SysRole r WHERE r.deleted = false")
    List<SysRole> findAllActive();
}
