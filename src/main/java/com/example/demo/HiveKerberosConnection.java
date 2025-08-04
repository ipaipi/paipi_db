package com.example.demo;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class HiveKerberosConnection {

    public static void main(String[] args) {
        // 1. 设置Kerberos配置
        System.setProperty("java.security.krb5.conf", "C:/pro/paipi_db/local_test/krb5.conf");
        //System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
        System.setProperty("sun.security.krb5.debug", "true"); // 开启Kerberos调试

        // 2. 配置Hadoop安全认证
        Configuration conf = new Configuration();
        conf.set("hadoop.security.authentication", "kerberos");
        UserGroupInformation.setConfiguration(conf);

        try {
            System.out.println("尝试使用keytab登录...");

            // 3. 使用keytab登录
            UserGroupInformation.loginUserFromKeytab(
                    "hive@PAIPI.VIP",
                    "C:/pro/paipi_db/local_test/hive.keytab");

            System.out.println("Kerberos认证成功!");

            // 4. 加载Hive JDBC驱动
            Class.forName("org.apache.hive.jdbc.HiveDriver");

            // 5. 创建连接 - 添加更多参数
            String connectionURL = "jdbc:hive2://cdh01:10000/default;" +
                    "principal=hive/cdh01@PAIPI.VIP;" +
                    "auth=kerberos;";

            System.out.println("尝试连接HiveServer2: " + connectionURL);
            Connection conn = DriverManager.getConnection(connectionURL);
            System.out.println("连接成功!");

            // 6. 执行查询
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SHOW TABLES");

            // 处理结果
            while (rs.next()) {
                System.out.println(rs.getString(1));
            }

            // 关闭资源
            rs.close();
            stmt.close();
            conn.close();

        } catch (Exception e) {
            System.err.println("发生错误:");
            e.printStackTrace();
        }
    }
}