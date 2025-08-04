package com.example.demo.typeconverter;

import java.util.Arrays;
import java.util.List;

public class DataXTypeConverterFullDemo {

    // ==================== 测试用例 ====================
    public static void main(String[] args) {
        // 1. 初始化转换器
        WriterTypeConverter mysqlWriter = new MySQLWriterConverter();
        WriterTypeConverter oracleWriter = new OracleWriterConverter();

        // 2. 模拟 DataX 内部数据类型
        List<ColumnMeta> dataxColumns = Arrays.asList(
                new ColumnMeta("id", ColumnType.LONG, 20, null),
                new ColumnMeta("amount", ColumnType.DOUBLE, 10, 2),
                new ColumnMeta("name", ColumnType.STRING, null, null),
                new ColumnMeta("create_time", ColumnType.DATE, null, null)
        );

        // 3. 测试 MySQL Writer 转换
        System.out.println("==== MySQL Writer 转换 ====");
        testWriterConverter(mysqlWriter, dataxColumns);

        // 4. 测试 Oracle Writer 转换
        System.out.println("\n==== Oracle Writer 转换 ====");
        testWriterConverter(oracleWriter, dataxColumns);
    }

    // 辅助方法：测试 Writer 转换逻辑
    private static void testWriterConverter(WriterTypeConverter converter, List<ColumnMeta> columns) {
        for (ColumnMeta column : columns) {
            try {
                String targetType = converter.convert(column.type, column.precision, column.scale);
                System.out.printf("%-12s (%-6s) → %s\n",
                        column.name,
                        column.type,
                        targetType);
            } catch (Exception e) {
                System.err.println("转换失败: " + column.name + " - " + e.getMessage());
            }
        }
    }

    // DataX 内部数据类型
    public enum ColumnType {
        LONG, DOUBLE, STRING, DATE, BOOL, BYTES
    }

    // ==================== 定义 Reader 端的 TypeConverter 接口 ====================
    public interface TypeConverter {
        ColumnType convert(String sourceType, Integer precision, Integer scale);
    }

    // ==================== 定义 Writer 端的 WriterTypeConverter 接口 ====================
    public interface WriterTypeConverter {
        String convert(ColumnType dataxType, Integer precision, Integer scale);
    }

    // ==================== Reader 端 ====================
    // MySQL Reader 转换器
    public static class MySQLReaderConverter implements TypeConverter {
        @Override
        public ColumnType convert(String mysqlType, Integer precision, Integer scale) {
            String type = mysqlType.toUpperCase();
            if (type.contains("INT") && !type.contains("POINT")) return ColumnType.LONG;
            else if (type.contains("CHAR") || type.contains("TEXT")) return ColumnType.STRING;
            else if (type.contains("DATETIME") || type.contains("TIMESTAMP")) return ColumnType.DATE;
            else if (type.contains("DECIMAL") || type.contains("FLOAT")) return ColumnType.DOUBLE;
            else if (type.equals("BIT")) return ColumnType.BOOL;
            else if (type.contains("BLOB")) return ColumnType.BYTES;
            throw new IllegalArgumentException("Unsupported MySQL type: " + mysqlType);
        }
    }

    // ==================== Writer 端 ====================
    // MySQL Writer 转换器
    public static class MySQLWriterConverter implements WriterTypeConverter {
        @Override
        public String convert(ColumnType dataxType, Integer precision, Integer scale) {
            switch (dataxType) {
                case LONG:
                    return "BIGINT";
                case DOUBLE:
                    return precision != null ?
                            String.format("DECIMAL(%d,%d)", precision, scale != null ? scale : 2) : "DOUBLE";
                case STRING:
                    return "VARCHAR(255)";
                case DATE:
                    return "DATETIME";
                case BOOL:
                    return "BIT(1)";
                case BYTES:
                    return "BLOB";
                default:
                    throw new IllegalArgumentException("Unsupported DataX type: " + dataxType);
            }
        }
    }

    // Oracle Writer 转换器
    public static class OracleWriterConverter implements WriterTypeConverter {
        @Override
        public String convert(ColumnType dataxType, Integer precision, Integer scale) {
            switch (dataxType) {
                case LONG:
                    return "NUMBER(" + (precision != null ? precision : 38) + ")";
                case DOUBLE:
                    return "NUMBER(" + (precision != null ? precision : 38) +
                            "," + (scale != null ? scale : 4) + ")";
                case STRING:
                    return "VARCHAR2(4000)";
                case DATE:
                    return "TIMESTAMP";
                case BOOL:
                    return "NUMBER(1)";
                case BYTES:
                    return "BLOB";
                default:
                    throw new IllegalArgumentException("Unsupported DataX type: " + dataxType);
            }
        }
    }

    // 字段元数据类
    public static class ColumnMeta {
        public String name;
        public ColumnType type;
        public Integer precision;
        public Integer scale;

        public ColumnMeta(String name, ColumnType type, Integer precision, Integer scale) {
            this.name = name;
            this.type = type;
            this.precision = precision;
            this.scale = scale;
        }
    }
}