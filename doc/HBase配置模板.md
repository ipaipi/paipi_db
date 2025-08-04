# HBase数据源配置模板

## 一、用途说明
本模板用于配置HBase数据源的同步任务，**目前仅支持模式2**，适用于HBase与Mysql、PostgreSQL、TxtFile等数据源之间的数据同步、批量导出等场景。

> 注意：HBase为NoSQL数据源，仅支持reader端，参数结构与传统数据库不同，需指定hbaseConfig、table、rowkey、column等。

## 二、完整配置样例（仅含 reader、writer）

###  HBase -> Mysql（模式9，含分片）
```json
{
  "job": {
    "sqlMode": 2,
    "setting": {
      "speed": { "channel": 2 },
      "reportPath": "C:/pro/paipi_db/local_test/temp/report_file.json",
      "enablePreCheck": true,
      "enablePostCheck": true,
      "channel": {
        "class": "memory",
        "memoryCapacity": 10000,
        "path": "./channel_file"
      }
    },
    "content": [
      {
        "reader": {
          "name": "HBase",
          "parameter": {
            "hbaseConfig": {
              "hbase.zookeeper.quorum": "192.168.232.101,192.168.232.102,192.168.232.103",
              "hbase.zookeeper.property.clientPort": "2181",
              "zookeeper.znode.parent": "/hbase"
            },
            "table": "test_ns:test_table",
            "rowkey": "id",
            "column": [
              { "name": "cf1:id", "type": "string" },
              { "name": "cf1:name", "type": "string" },
              { "name": "cf1:age", "type": "int" },
              { "name": "cf1:email", "type": "string" },
              { "name": "cf1:create_time", "type": "string" }
            ],
            "splitMode": "range",
            "range": {
              "startRowkey": "row1",
              "endRowkey": "row4"
            }
          }
        },
        "writer": {
          "name": "Mysql",
          "parameter": {
            "connection": {
              "ip": "192.168.232.100",
              "port": "3306",
              "username": "root",
              "password": "admin",
              "database": "demo",
              "jdbcUrlParam": {}
            },
            "table": "user",
            "column": ["id", "name", "age", "email", "create_time"],
            "session": ["set names utf8mb4"],
            "autoCreateTable": true
          }
        }
      }
    ]
  }
}
```

## 三、参数说明

### 1. reader 部分参数
| 参数路径 | 必填 | 说明 |
|---|---|---|
| name | 是 | 数据源类型，HBase |
| parameter.hbaseConfig | 是 | HBase连接配置，zookeeper地址、端口等 |
| parameter.table | 是 | HBase表名，格式namespace:table |
| parameter.rowkey | 是 | rowkey字段名（需在column中出现）|
| parameter.column | 是 | 字段列表，格式cf:col，需包含rowkey |
| parameter.splitMode | 否 | 分片模式，region/range，默认region |
| parameter.range.startRowkey | 否 | range分片时起始rowkey |
| parameter.range.endRowkey | 否 | range分片时结束rowkey |

### 2. writer 部分参数
详见目标数据源配置模板，如Mysql、TxtFile等。

## 四、注意事项
- 仅支持模式2，不支持0~1。
- HBase为NoSQL，需指定hbaseConfig、table、rowkey、column。
- column字段需包含rowkey，格式为cf:col。
- 支持region/range分片，range需指定startRowkey/endRowkey。
- hbaseConfig需包含zookeeper相关配置，确保可连通。
- reader、writer参数结构需分别配置，详见样例。

## 五、典型用法场景
- HBase表数据批量导出为文本文件
- HBase与Mysql、PostgreSQL等数据库间高效同步
- 大表分片并发同步，支持region/range分片 