package com.example.demo;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.UserGroupInformation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class HiveKerberosConnectionHDFS {

    public static void main(String[] args) {
        // 1. 设置Kerberos配置
        System.setProperty("java.security.krb5.conf", "C:/pro/paipi_db/local_test/krb5.conf");
        //System.setProperty("sun.security.krb5.debug", "true"); // 开启Kerberos调试

        // 2. 配置Hadoop安全认证
        Configuration conf = new Configuration();
        conf.set("hadoop.security.authentication", "kerberos");

        // 添加HDFS配置
        conf.set("fs.defaultFS", "hdfs://cdh01:8020"); // 替换为你的HDFS地址
        conf.set("hadoop.security.authentication", "kerberos");

        UserGroupInformation.setConfiguration(conf);

        try {
            System.out.println("尝试使用keytab登录...");

            // 3. 使用keytab登录
            UserGroupInformation.loginUserFromKeytab(
                    "hive@PAIPI.VIP",
                    "C:/pro/paipi_db/local_test/hive.keytab");

            System.out.println("Kerberos认证成功!");

            /****************** Hive操作部分 ********************/
            System.out.println("\n===== 开始Hive操作 =====");

            // 4. 加载Hive JDBC驱动
            Class.forName("org.apache.hive.jdbc.HiveDriver");

            // 5. 创建Hive连接
            String hiveConnectionURL = "jdbc:hive2://cdh01:10000/default;" +
                    "principal=hive/cdh01@PAIPI.VIP;" +
                    "auth=kerberos;";

            System.out.println("尝试连接HiveServer2: " + hiveConnectionURL);
            try (Connection conn = DriverManager.getConnection(hiveConnectionURL);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW TABLES")) {

                System.out.println("Hive连接成功!");
                System.out.println("数据库中的表:");

                // 处理结果
                while (rs.next()) {
                    System.out.println(rs.getString(1));
                }
            }

            /******************** HDFS操作部分 ********************/
            System.out.println("\n===== 开始HDFS操作 =====");

            // 6. 创建HDFS文件系统对象
            FileSystem fs = FileSystem.get(conf);
            System.out.println("HDFS连接成功!");

            // 示例1: 列出根目录下的文件
            System.out.println("\nHDFS根目录内容:");
            for (FileStatus path : fs.listStatus(new Path("/"))) {
                System.out.println(path.getPath());
            }

            // 示例2: 读取HDFS文件内容
            String hdfsFilePath = "/demo/demo.txt"; // 替换为你的文件路径
            if (fs.exists(new Path(hdfsFilePath))) {
                System.out.println("\n文件内容(" + hdfsFilePath + "):");
                try (BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(new Path(hdfsFilePath))))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            } else {
                System.out.println("\n文件不存在: " + hdfsFilePath);
            }

            // 关闭HDFS连接
            fs.close();
            System.out.println("\nHDFS操作完成!");

        } catch (Exception e) {
            System.err.println("发生错误:");
            e.printStackTrace();
        }
    }
}