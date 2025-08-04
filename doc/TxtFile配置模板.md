# TxtFile数据源配置模板

## 一、用途说明
本模板用于配置TxtFile（文本文件）数据源的同步任务，**不支持0-1模式**，适用于与Mysql、Hive、PostgreSQL等数据库之间的数据导入导出、批量迁移等场景。

> 注意：TxtFile为项目重构后新增数据源，不支持模式0~7，且配置中**没有common块**，仅支持reader和writer。

## 二、完整配置样例（仅含 reader、writer）
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
        "reader": {
          "name": "TxtFile",
          "parameter": {
            //            "filePath": "C:/pro/paipi_db/local_test/temp/test/input.csv",
            "filePath": [
              "C:/pro/paipi_db/local_test/temp/test/input.csv",
              "C:/pro/paipi_db/local_test/temp/test/input.csv"
            ],
            "column": [
              "id",
              "name",
              "age"
            ],
            "sep": ",",
            "skipHeader": true
          }
        },
        "writer": {
          "name": "TxtFile",
          "parameter": {
            "filePath": "./txt_test/",
            "sep": ",",
            "fileType": "json",
            "chunkSize": -1,
            "column": [
              "id",
              "name",
              "age",
              "email",
              "create_time"
            ],
            "skipHeader": true
          }
        },
        "transformer": [
          {
            "name": "trim",
            "parameter": {
              "columnIndex": [
                1,
                4
              ]
            }
          },
          {
            "name": "replace",
            "parameter": {
              "columnIndex": [
                1
              ],
              "oldValue": "Alice",
              "newValue": "替换"
            }
          }
        ]
      }
    ]
  }
}
```

## 三、参数说明

### 1. reader 部分参数
| 参数路径 | 必填 | 说明 |
|---|---|---|
| name | 是 | 数据源类型，TxtFile |
| parameter.filePath | 是 | 文件路径 |
| parameter.column | 是 | 字段列表，顺序需与文件一致 |
| parameter.sep | 否 | 字段分隔符，默认逗号 |
| parameter.skipHeader | 否 | 跳过首行（布尔） |

### 2. writer 部分参数
| 参数路径 | 必填 | 说明 |
|---|---|---|
| name | 是 | 数据源类型，TxtFile |
| parameter.filePath | 是 | 文件路径 |
| parameter.column | 是 | 字段列表，顺序需与文件一致 |
| parameter.sep | 否 | 字段分隔符，默认逗号 |
| parameter.skipHeader | 否 | 跳过首行（布尔） |

## 四、注意事项
- 仅支持模式8、9，不支持0~7。
- 配置中没有common块。
- column字段顺序需与文件实际顺序一致。
- sep为字段分隔符，常用逗号、制表符、\u0001等。
- skipHeader为true时跳过首行，适用于带表头文件。
- filePath需为绝对路径或相对路径，确保有读写权限。
- reader、writer参数结构类似，但建议根据实际场景分别配置。

## 五、典型用法场景
- 文本文件批量导入数据库
- 数据库表批量导出为CSV/TXT文件
- 异构数据源间通过文本文件中转
