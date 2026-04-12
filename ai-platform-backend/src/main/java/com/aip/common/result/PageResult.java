package com.aip.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果封装
 *
 * @param <T> 数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private Long total;

    /** 当前页数据 */
    private List<T> records;

    /** 当前页码 */
    private Long current;

    /** 每页记录数 */
    private Long size;

    /** 总页数 */
    private Long pages;

    /**
     * 默认构造器
     */
    public PageResult(Long total, List<T> records, Long current, Long size) {
        this.total = total;
        this.records = records;
        this.current = current;
        this.size = size;
        this.pages = size != null && size > 0 ? (total + size - 1) / size : 0L;
    }

    /**
     * 静态工厂方法
     */
    public static <T> PageResult<T> of(Long total, List<T> records, Long current, Long size) {
        return new PageResult<>(total, records, current, size);
    }

    /**
     * 快速创建分页结果
     */
    public static <T> PageResult<T> of(List<T> records, Long total, Long current, Long size) {
        return new PageResult<>(total, records, current, size);
    }

    /**
     * 空分页结果
     */
    public static <T> PageResult<T> empty() {
        return new PageResult<>(0L, List.of(), 1L, 10L);
    }

    /**
     * 空分页结果（指定页码和大小）
     */
    public static <T> PageResult<T> empty(Long current, Long size) {
        return new PageResult<>(0L, List.of(), current, size);
    }
}
