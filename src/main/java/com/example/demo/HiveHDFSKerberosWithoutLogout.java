package com.example.demo;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.UserGroupInformation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.PrivilegedExceptionAction;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class HiveHDFSKerberosWithoutLogout {

    public static void main(String[] args) {
        // 1. 设置Kerberos配置
        System.setProperty("java.security.krb5.conf", "C:/pro/paipi_db/local_test/krb5.conf");
        //System.setProperty("sun.security.krb5.debug", "true");

        try {
            /******************** Hive操作部分 ********************/
            System.out.println("\n===== 开始Hive操作 =====");

            // 2. 配置Hive的Hadoop安全认证
            Configuration hiveConf = new Configuration();
            hiveConf.set("hadoop.security.authentication", "kerberos");
            UserGroupInformation.setConfiguration(hiveConf);

            // 3. 使用Hive的keytab登录
            UserGroupInformation hiveUgi = UserGroupInformation.loginUserFromKeytabAndReturnUGI(
                    "hive@PAIPI.VIP",
                    "C:/pro/paipi_db/local_test/hive.keytab");

            System.out.println("Hive Kerberos认证成功!");

            // 4. 在Hive安全上下文中执行操作
            hiveUgi.doAs(new PrivilegedExceptionAction<Void>() {
                @Override
                public Void run() throws Exception {
                    Class.forName("org.apache.hive.jdbc.HiveDriver");

                    String hiveConnectionURL = "jdbc:hive2://cdh01:10000/default;" +
                            "principal=hive/cdh01@PAIPI.VIP;" +
                            "auth=kerberos;";

                    System.out.println("尝试连接HiveServer2: " + hiveConnectionURL);
                    try (Connection conn = DriverManager.getConnection(hiveConnectionURL);
                         Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery("SHOW TABLES")) {

                        System.out.println("Hive连接成功!");
                        System.out.println("数据库中的表:");

                        while (rs.next()) {
                            System.out.println(rs.getString(1));
                        }
                    }
                    return null;
                }
            });

            /******************** HDFS操作部分 ********************/
            System.out.println("\n===== 开始HDFS操作 =====");

            // 5. 配置HDFS的Hadoop安全认证
            Configuration hdfsConf = new Configuration();
            hdfsConf.set("fs.defaultFS", "hdfs://cdh01:8020");
            hdfsConf.set("hadoop.security.authentication", "kerberos");

            // 6. 使用HDFS的keytab创建新的UGI
            UserGroupInformation hdfsUgi = UserGroupInformation.loginUserFromKeytabAndReturnUGI(
                    "hdfs@PAIPI.VIP",
                    "C:/pro/paipi_db/local_test/hdfs.keytab");

            System.out.println("HDFS Kerberos认证成功!");

            // 7. 在HDFS安全上下文中执行操作
            hdfsUgi.doAs(new PrivilegedExceptionAction<Void>() {
                @Override
                public Void run() throws Exception {
                    FileSystem fs = FileSystem.get(hdfsConf);
                    System.out.println("HDFS连接成功!");

                    System.out.println("\nHDFS根目录内容:");
                    for (FileStatus path : fs.listStatus(new Path("/"))) {
                        System.out.println(path.getPath());
                    }

                    String hdfsFilePath = "/demo/demo.txt";
                    if (fs.exists(new Path(hdfsFilePath))) {
                        System.out.println("\n文件内容(" + hdfsFilePath + "):");
                        try (BufferedReader br = new BufferedReader(
                                new InputStreamReader(fs.open(new Path(hdfsFilePath))))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                System.out.println(line);
                            }
                        }
                    } else {
                        System.out.println("\n文件不存在: " + hdfsFilePath);
                    }

                    fs.close();
                    System.out.println("\nHDFS操作完成!");
                    return null;
                }
            });

        } catch (Exception e) {
            System.err.println("发生错误:");
            e.printStackTrace();
        }
    }
}