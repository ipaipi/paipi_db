package com.example.demo.typeconverter;

import java.util.Arrays;
import java.util.List;

public class DataXTypeConverterDemo {

    // 6. 主方法（完整示例）
    public static void main(String[] args) {
        // 创建转换器实例
        TypeConverter mysqlConverter = new MySQLTypeConverter();
        TypeConverter oracleConverter = new OracleTypeConverter();

        // 模拟 MySQL 字段类型测试
        System.out.println("==== MySQL 类型转换测试 ====");
        testConverter(mysqlConverter, Arrays.asList(
                new ColumnMeta("id", "BIGINT", 20, 0),
                new ColumnMeta("name", "VARCHAR", 100, 0),
                new ColumnMeta("price", "DECIMAL", 10, 2),
                new ColumnMeta("created_at", "DATETIME", 0, 0)
        ));

        // 模拟 Oracle 字段类型测试
        System.out.println("\n==== Oracle 类型转换测试 ====");
        testConverter(oracleConverter, Arrays.asList(
                new ColumnMeta("id", "NUMBER", 10, 0),
                new ColumnMeta("amount", "NUMBER", 12, 2),
                new ColumnMeta("description", "VARCHAR2", 200, 0)
        ));
    }

    // 7. 测试转换器的方法
    private static void testConverter(TypeConverter converter, List<ColumnMeta> columns) {
        for (ColumnMeta column : columns) {
            try {
                ColumnType dataxType = converter.convert(
                        column.typeName,
                        column.precision,
                        column.scale
                );
                System.out.printf("%-12s (%-10s) → %s\n",
                        column.name,
                        column.typeName + (column.scale > 0 ?
                                "(" + column.precision + "," + column.scale + ")" : ""),
                        dataxType
                );
            } catch (Exception e) {
                System.err.println("转换失败: " + column.name + " - " + e.getMessage());
            }
        }
    }

    // 1. 定义 DataX 内部数据类型枚举
    public enum ColumnType {
        LONG, DOUBLE, STRING, DATE, BOOL, BYTES
    }

    // 2. 类型转换接口
    public interface TypeConverter {
        ColumnType convert(String sourceType, Integer precision, Integer scale);
    }

    // 3. MySQL 类型转换实现类
    public static class MySQLTypeConverter implements TypeConverter {
        @Override
        public ColumnType convert(String mysqlType, Integer precision, Integer scale) {
            String type = mysqlType.toUpperCase();
            if (type.contains("INT") && !type.contains("POINT")) {
                return ColumnType.LONG;
            } else if (type.contains("CHAR") || type.contains("TEXT")) {
                return ColumnType.STRING;
            } else if (type.contains("DATETIME") || type.contains("TIMESTAMP")) {
                return ColumnType.DATE;
            } else if (type.contains("DECIMAL") || type.contains("FLOAT")) {
                return ColumnType.DOUBLE;
            } else if (type.equals("BIT")) {
                return ColumnType.BOOL;
            } else if (type.contains("BLOB")) {
                return ColumnType.BYTES;
            }
            throw new IllegalArgumentException("Unsupported MySQL type: " + mysqlType);
        }
    }

    // 4. Oracle 类型转换实现类（演示 precision/scale 的使用）
    public static class OracleTypeConverter implements TypeConverter {
        @Override
        public ColumnType convert(String oracleType, Integer precision, Integer scale) {
            switch (oracleType.toUpperCase()) {
                case "NUMBER":
                    if (scale != null && scale > 0) {
                        return ColumnType.DOUBLE; // NUMBER(10,2) → DOUBLE
                    }
                    return ColumnType.LONG;       // NUMBER(10) → LONG
                case "VARCHAR2":
                case "CLOB":
                    return ColumnType.STRING;
                case "DATE":
                case "TIMESTAMP":
                    return ColumnType.DATE;
                case "BLOB":
                    return ColumnType.BYTES;
                default:
                    throw new IllegalArgumentException("Unsupported Oracle type: " + oracleType);
            }
        }
    }

    // 5. 字段元数据存储类
    public static class ColumnMeta {
        public String name;
        public String typeName;
        public int precision;
        public int scale;

        public ColumnMeta(String name, String typeName, int precision, int scale) {
            this.name = name;
            this.typeName = typeName;
            this.precision = precision;
            this.scale = scale;
        }
    }
}