package com.example.demo;


import com.paipi.config.Configuration;

import java.util.List;
import java.util.Map;

public class DataXConfigTest {

    public static void testLoadDataXConfig() {
        // 1. 加载配置文件
        Configuration config = ConfigLoader.loadConfigFromResources("local_test/datasource-config1.json");

        // 2. 验证顶层配置
        System.out.println("=== 基本配置验证 ===");
        int sqlMode = config.getInt("job.sqlMode");
        System.out.println("SQL模式: " + sqlMode);
        int channel = config.getInt("job.setting.speed.channel");
        System.out.println("通道数: " + channel);

        // 3. 获取reader配置
        Configuration readerConfig = config.getConfiguration("job.content[0].reader");
        System.out.println("\n=== Reader配置 ===");
        System.out.println("Reader类型: " + readerConfig.getString("name"));

        Configuration readerParam = readerConfig.getConfiguration("parameter");
        Configuration readerConn = readerParam.getConfiguration("connection");
        System.out.println("数据库连接: " + readerConn.getString("ip") + ":" + readerConn.getString("port"));
        System.out.println("用户名: " + readerConn.getString("username"));
        System.out.println("表名: " + readerConn.getString("table"));

        // 4. 验证reader的column配置
        List<Map> columns = readerParam.getList("column", Map.class);
        System.out.println("\nReader列配置:");
        columns.forEach(col -> {
            System.out.printf("值: %-15s 类型: %s%n",
                    col.get("value"), col.get("type"));
        });

        // 5. 获取writer配置
        Configuration writerConfig = config.getConfiguration("job.content[0].writer");
        System.out.println("\n=== Writer配置 ===");
        System.out.println("Writer类型: " + writerConfig.getString("name"));

        Configuration writerParam = writerConfig.getConfiguration("parameter");
        System.out.println("写入模式: " + writerParam.getString("writeMode"));

        // 6. 验证writer的SQL配置
        List<String> preSqls = writerParam.getList("preSql", String.class);
        System.out.println("\n预执行SQL:");
        preSqls.forEach(System.out::println);

        List<String> sessions = writerParam.getList("session", String.class);
        System.out.println("\nSession设置:");
        sessions.forEach(System.out::println);

        // 7. 验证writer的column配置
        List<String> writerColumns = writerParam.getList("column", String.class);
        System.out.println("\nWriter列映射:");
        writerColumns.forEach(System.out::println);

        // 8. 验证writer连接配置
        Configuration writerConn = writerParam.getConfiguration("connection");
        System.out.println("\nWriter连接信息:");
        System.out.println("IP: " + writerConn.getString("ip"));
        System.out.println("端口: " + writerConn.getString("port"));
        System.out.println("表名: " + writerConn.getString("table"));
    }

    public static void main(String[] args) {
        testLoadDataXConfig();
    }
}