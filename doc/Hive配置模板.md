# Hive数据源配置模板

## 一、用途说明
本模板用于配置Hive数据源的同步任务，支持普通认证和Kerberos认证，适用于Hive与其他数据源（如MySQL、PostgreSQL、TxtFile）之间的数据同步、导入导出等场景。

## 二、完整配置样例（含 common、reader、writer）
```json
{
  "job": {
    "sqlMode": 2,
    "setting": {
      "speed": {
        "channel": 1
      },
      "reportPath": "C:/pro/paipi_db/local_test/temp/report_file.json",
      "enablePreCheck": true,
      "enablePostCheck": true
    },
    "content": [
      {
        "common": {
          "name": "Hive",
          "parameter": {
            "connection": {
              "ip": "192.168.232.100",
              "port": "10000",
              "username": "root",
              "password": "admin",
              "database": "default",
              "authMechanism": "KERBEROS",
              "hdfsIP": "192.168.232.100",
              "hdfsPort": "8020",
              "hdfsUser": "hdfs",
              "hdfsTempPath": "/user/hive/paipi_db/",
              "kerberosServiceName": {
                "principal": "hive/cdh01@PAIPI.VIP",
                "keytabConf": "C:/pro/paipi_db/local_test/krb5.conf",
                "keytabUsername": "hive@PAIPI.VIP",
                "keytabFile": "C:/pro/paipi_db/local_test/hive.keytab",
                "hdfsKeytabUsername": "hdfs@PAIPI.VIP",
                "hdfsKeytabFile": "C:/pro/paipi_db/local_test/hdfs.keytab"
              },
              "jdbcUrlParam": {}
            },
            "table": "student_info",
            "column": [
              "*"
            ],
            "saveUrl": "C:/pro/paipi_db/local_test/temp/test",
            "where": "age > 18",
            "sep": ","
          }
        },
        "reader": {
          "name": "Hive",
          "parameter": {
            "connection": {
              "ip": "192.168.232.100",
              "port": "10000",
              "username": "root",
              "password": "admin",
              "database": "demo",
              "authMechanism": "NOSASL",
              "jdbcUrlParam": {}
            },
            "table": "demo",
            "column": [
              "id",
              "name"
            ],
            "querySql": "select id, name from demo where age > 20",
            "saveUrl": "C:/pro/paipi_db/local_test/temp/test",
            "where": "",
            "splitPK": "id"
          }
        },
        "writer": {
          "name": "Hive",
          "parameter": {
            "connection": {
              "ip": "192.168.232.101",
              "port": "10000",
              "username": "root",
              "password": "admin",
              "database": "demo",
              "authMechanism": "KERBEROS",
              "kerberosServiceName": {
                "principal": "hive/cdh01@PAIPI.VIP",
                "keytabConf": "C:/pro/paipi_db/local_test/krb5.conf",
                "keytabUsername": "hive@PAIPI.VIP",
                "keytabFile": "C:/pro/paipi_db/local_test/hive.keytab",
                "hdfsKeytabUsername": "hdfs@PAIPI.VIP",
                "hdfsKeytabFile": "C:/pro/paipi_db/local_test/hdfs.keytab"
              },
              "jdbcUrlParam": {}
            },
            "table": "student_info",
            "column": [
              "id",
              "name",
              "age"
            ],
            "preSql": "truncate table student_info",
            "postSql": "truncate table student_info",
            "autoCreateTable": true,
            "writeMode": "update"
          }
        }
      }
    ]
  }
}
```

## 三、参数说明

### 1. common 部分参数
| 参数路径 | 必填 | 说明 |
|---|---|---|
| name | 是 | 数据源类型，Hive |
| parameter.connection.ip | 是 | HiveServer2地址 |
| parameter.connection.port | 是 | 端口号 |
| parameter.connection.username | 是 | 用户名 |
| parameter.connection.password | 是 | 密码 |
| parameter.connection.database | 是 | 数据库名 |
| parameter.connection.authMechanism | 是 | 认证方式（NOSASL/KERBEROS） |
| parameter.connection.hdfsIP | 否 | HDFS地址（如需上传文件） |
| parameter.connection.hdfsPort | 否 | HDFS端口 |
| parameter.connection.hdfsUser | 否 | HDFS用户 |
| parameter.connection.hdfsTempPath | 否 | HDFS临时目录 |
| parameter.connection.kerberosServiceName | 否 | Kerberos相关配置（见下） |
| parameter.connection.jdbcUrlParam | 否 | JDBC附加参数（JSON对象） |
| parameter.table | 是 | 表名 |
| parameter.column | 是 | 字段列表，支持*或具体字段 |
| parameter.querySql | 否 | 查询SQL（优先级高于table/column/where） |
| parameter.saveUrl | 否 | 数据导出保存路径 |
| parameter.where | 否 | 过滤条件 |
| parameter.sep | 否 | 字段分隔符，默认逗号 |

#### Kerberos相关参数（authMechanism=KERBEROS时必填）
| 参数路径 | 说明 |
|---|---|
| kerberosServiceName.principal | Hive服务Principal |
| kerberosServiceName.keytabConf | krb5.conf配置文件路径 |
| kerberosServiceName.keytabUsername | Hive用户Principal |
| kerberosServiceName.keytabFile | Hive用户keytab文件路径 |
| kerberosServiceName.hdfsKeytabUsername | HDFS用户Principal |
| kerberosServiceName.hdfsKeytabFile | HDFS用户keytab文件路径 |

### 2. reader 部分参数
| 参数路径 | 必填 | 说明 |
|---|---|---|
| name | 是 | 数据源类型，Hive |
| parameter.connection.ip | 是 | HiveServer2地址 |
| parameter.connection.port | 是 | 端口号 |
| parameter.connection.username | 是 | 用户名 |
| parameter.connection.password | 是 | 密码 |
| parameter.connection.database | 是 | 数据库名 |
| parameter.connection.authMechanism | 是 | 认证方式（NOSASL/KERBEROS） |
| parameter.connection.jdbcUrlParam | 否 | JDBC附加参数（JSON对象） |
| parameter.table | 是 | 表名 |
| parameter.column | 是 | 字段列表，支持*或具体字段 |
| parameter.querySql | 否 | 查询SQL（优先级高于table/column/where） |
| parameter.saveUrl | 否 | 数据导出保存路径 |
| parameter.where | 否 | 过滤条件 |
| parameter.sep | 否 | 字段分隔符，默认逗号 |

### 3. writer 部分参数
| 参数路径 | 必填 | 说明 |
|---|---|---|
| name | 是 | 数据源类型，Hive |
| parameter.connection.ip | 是 | HiveServer2地址 |
| parameter.connection.port | 是 | 端口号 |
| parameter.connection.username | 是 | 用户名 |
| parameter.connection.password | 是 | 密码 |
| parameter.connection.database | 是 | 数据库名 |
| parameter.connection.authMechanism | 是 | 认证方式（NOSASL/KERBEROS） |
| parameter.connection.jdbcUrlParam | 否 | JDBC附加参数（JSON对象） |
| parameter.table | 是 | 表名 |
| parameter.column | 是 | 字段列表，支持*或具体字段 |
| parameter.preSql | 否 | 执行前置SQL（数组） |
| parameter.postSql | 否 | 执行后置SQL（数组） |
| parameter.sep | 否 | 字段分隔符，默认逗号 |

## 四、注意事项
- Kerberos认证需确保相关keytab和krb5.conf文件路径正确，且有权限访问。
- column可用*表示全字段，或指定字段数组。
- querySql优先级高于table/column/where，若填写则忽略后者。
- preSql/postSql为可选，支持多条SQL语句（writer）。
- JDBC附加参数可用于特殊连接需求。
- common、reader、writer参数结构类似，但建议根据实际场景分别配置。

## 五、典型用法场景
- Hive表数据导出到本地文件或其他数据库
- Hive与MySQL、PostgreSQL等异构数据源同步
- Kerberos安全环境下的Hive数据同步
