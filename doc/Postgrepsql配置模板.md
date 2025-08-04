# PostgreSQL数据源配置模板

## 一、用途说明
本模板用于配置PostgreSQL数据源的同步任务，适用于PostgreSQL与Mysql、Hive、TxtFile等数据源之间的数据同步、导入导出等场景。

## 二、完整配置样例（含 common、reader、writer）
```json
{
  "job": {
    "sqlMode": 2,
    "setting": {
      "speed": { "channel": 2 },
      "reportPath": "C:/pro/paipi_db/local_test/temp/report_file.json",
      "enablePreCheck": true,
      "enablePostCheck": true
    },
    "content": [
      {
        "reader": {
          "name": "Postgresql",
          "parameter": {
            "connection": {
              "ip": "192.168.232.100",
              "port": "5432",
              "username": "postgres",
              "password": "admin",
              "database": "postgres",
              "schema": "public",
              "jdbcUrlParam": {}
            },
            "table": "employees",
            "column": ["*"],
            "preSql": "truncate table student_info",
            "postSql": "truncate table student_info",
            "where": "",
            "splitPK": "id"
          }
        },
        "writer": {
          "name": "Postgresql",
          "parameter": {
            "connection": {
              "ip": "192.168.232.101",
              "port": "5432",
              "username": "root",
              "password": "admin",
              "database": "demo",
              "jdbcUrlParam": {}
            },
            "table": "employees",
            "column": ["id", "name", "age", "department"],
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
| name | 是 | 数据源类型，Postgresql |
| parameter.connection.ip | 是 | 数据库地址 |
| parameter.connection.port | 是 | 端口号 |
| parameter.connection.username | 是 | 用户名 |
| parameter.connection.password | 是 | 密码 |
| parameter.connection.database | 是 | 数据库名 |
| parameter.connection.schema | 否 | schema名称，默认public |
| parameter.connection.jdbcUrlParam | 否 | JDBC附加参数（JSON对象） |
| parameter.table | 是 | 表名 |
| parameter.column | 是 | 字段列表，支持*或具体字段 |
| parameter.querySql | 否 | 查询SQL（优先级高于table/column/where） |
| parameter.saveUrl | 否 | 数据导出保存路径 |
| parameter.where | 否 | 过滤条件 |
| parameter.sep | 否 | 字段分隔符，默认逗号 |

### 2. reader 部分参数
| 参数路径 | 必填 | 说明 |
|---|---|---|
| name | 是 | 数据源类型，Postgresql |
| parameter.connection.ip | 是 | 数据库地址 |
| parameter.connection.port | 是 | 端口号 |
| parameter.connection.username | 是 | 用户名 |
| parameter.connection.password | 是 | 密码 |
| parameter.connection.database | 是 | 数据库名 |
| parameter.connection.schema | 否 | schema名称，默认public |
| parameter.connection.jdbcUrlParam | 否 | JDBC附加参数（JSON对象） |
| parameter.table | 是 | 表名 |
| parameter.column | 是 | 字段列表，支持*或具体字段 |
| parameter.querySql | 否 | 查询SQL（优先级高于table/column/where） |
| parameter.preSql | 否 | 执行前置SQL（数组） |
| parameter.postSql | 否 | 执行后置SQL（数组） |
| parameter.where | 否 | 过滤条件 |
| parameter.sep | 否 | 字段分隔符，默认逗号 |

### 3. writer 部分参数
| 参数路径 | 必填 | 说明 |
|---|---|---|
| name | 是 | 数据源类型，Postgresql |
| parameter.connection.ip | 是 | 数据库地址 |
| parameter.connection.port | 是 | 端口号 |
| parameter.connection.username | 是 | 用户名 |
| parameter.connection.password | 是 | 密码 |
| parameter.connection.database | 是 | 数据库名 |
| parameter.connection.jdbcUrlParam | 否 | JDBC附加参数（JSON对象） |
| parameter.table | 是 | 表名 |
| parameter.column | 是 | 字段列表，支持*或具体字段 |
| parameter.preSql | 否 | 执行前置SQL（数组） |
| parameter.postSql | 否 | 执行后置SQL（数组） |
| parameter.sep | 否 | 字段分隔符，默认逗号 |

## 四、注意事项
- schema参数可选，默认public。
- column可用*表示全字段，或指定字段数组。
- querySql优先级高于table/column/where，若填写则忽略后者。
- preSql/postSql为可选，支持多条SQL语句。
- JDBC附加参数可用于特殊连接需求。
- common、reader、writer参数结构类似，但建议根据实际场景分别配置。

## 五、典型用法场景
- PostgreSQL表数据导入导出
- PostgreSQL与Mysql、Hive等异构数据源同步
- 支持复杂SQL过滤、批量迁移等场景

