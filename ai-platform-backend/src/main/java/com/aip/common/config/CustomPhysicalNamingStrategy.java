package com.aip.common.config;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * JPA 物理命名策略
 * 
 * 功能：
 * - 驼峰转下划线
 * - 表名自动添加 t_ 前缀（如果未添加）
 * - 字段名自动添加 f_ 前缀（如果未添加）
 * 
 * 示例：
 * - Java: templateCode -> 物理名: f_template_code
 * - Java: createTime -> 物理名: f_create_time
 * - Java: AiModelConfig -> 表名: t_ai_model_config
 */
public class CustomPhysicalNamingStrategy extends PhysicalNamingStrategyStandardImpl {

    public static final CustomPhysicalNamingStrategy INSTANCE = new CustomPhysicalNamingStrategy();

    @Override
    public Identifier toPhysicalTableName(Identifier logicalName, JdbcEnvironment jdbcEnvironment) {
        String name = camelToSnake(logicalName.getText());
        if (!name.startsWith("t_")) {
            name = "t_" + name;
        }
        return Identifier.toIdentifier(name, logicalName.isQuoted());
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier logicalName, JdbcEnvironment jdbcEnvironment) {
        String name = camelToSnake(logicalName.getText());
        if (!name.startsWith("f_")) {
            name = "f_" + name;
        }
        return Identifier.toIdentifier(name, logicalName.isQuoted());
    }

    private String camelToSnake(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
