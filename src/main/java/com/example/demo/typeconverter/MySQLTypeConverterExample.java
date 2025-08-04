package com.example.demo.typeconverter;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class MySQLTypeConverterExample {

    public static void main(String[] args) throws SQLException {
        String jdbcUrl = "jdbc:mysql://bigdata01:3306/demo";
        String username = "root";
        String password = "admin";
        String tableName = "users";

        // 1. 获取表字段类型
        Map<String, String> columnTypes = getColumnTypes(jdbcUrl, username, password, tableName);

        // 2. 打印类型转换结果
        System.out.println("MySQL 类型 → DataX 类型");
        columnTypes.forEach((name, type) -> {
            String dataxType = convertMySQLTypeToDataX(type);
            System.out.printf("%s (%s) → %s\n", name, type, dataxType);
        });

        // 3. 读取数据并转换
        readData(jdbcUrl, username, password, tableName, columnTypes);
    }

    // 类型转换逻辑（简化版）
    private static String convertMySQLTypeToDataX(String mysqlType) {
        String type = mysqlType.toUpperCase();
        if (type.contains("INT")) return "LONG";
        else if (type.contains("CHAR") || type.contains("TEXT")) return "STRING";
        else if (type.contains("DATETIME") || type.contains("TIMESTAMP")) return "DATE";
        else if (type.contains("DECIMAL") || type.contains("FLOAT")) return "DOUBLE";
        else if (type.equals("BIT")) return "BOOL";
        else if (type.contains("BLOB")) return "BYTES";
        else return "STRING"; // 默认转为字符串
    }

    // 获取字段类型信息
    private static Map<String, String> getColumnTypes(String jdbcUrl, String username, String password, String tableName) throws SQLException {
        Map<String, String> columnTypes = new LinkedHashMap<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet rs = metaData.getColumns(null, null, tableName, null);
            while (rs.next()) {
                columnTypes.put(rs.getString("COLUMN_NAME"), rs.getString("TYPE_NAME"));
            }
        }
        return columnTypes;
    }

    // 读取数据并应用类型转换
    private static void readData(String jdbcUrl, String username, String password, String tableName, Map<String, String> columnTypes) throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {

            ResultSetMetaData metaData = rs.getMetaData();
            System.out.println("\n" + "字段名 (类型 → DataX类型)");
            while (rs.next()) {
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    String columnName = metaData.getColumnName(i);
                    String mysqlType = columnTypes.get(columnName);
                    String dataxType = convertMySQLTypeToDataX(mysqlType);
                    Object value = rs.getObject(i);
                    System.out.printf("%s (%s → %s): %s\n", columnName, mysqlType, dataxType, value);
                }
                System.out.println("------");
            }
        }
    }
}