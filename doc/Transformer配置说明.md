# Transformer 配置与用法说明

## 1. 功能简介
transformer 是数据同步链路中的“数据处理插件”，用于在 Reader 读取数据后、Writer 写入前，对每条 Record 进行灵活的数据清洗、转换、脱敏、加密等操作，支持链式调用、参数化配置、内置与自定义扩展。

## 2. 架构原理
- 执行链路：Reader → Channel（自动执行transformer链）→ Writer
- 注入方式：在 job.content.common.parameter 或 job.content.reader.parameter 中配置 transformer 数组，系统自动解析并注入到 Channel。
- 执行时机：每条 Record 在 push 到 Channel 时，依次经过 transformer 链处理。

## 3. 配置格式
```json
"transformer": [
  { "name": "trim", "parameter": { "columnIndex": [1, 7] } },
  { "name": "replace", "parameter": { "columnIndex": [7], "oldValue": "简介", "newValue": "自我介绍" } },
  { "name": "filter", "parameter": { "columnIndex": [2], "value": "18", "op": ">" } },
  { "name": "dateformat", "parameter": { "columnIndex": [6], "fromFormat": "yyyy-MM-dd HH:mm:ss", "toFormat": "yyyyMMddHH" } },
  { "name": "md5encrypt", "parameter": { "columnIndex": [1] } },
  { "name": "zeroonetoboolean", "parameter": { "columnIndex": [2, 3] } }
]
```

## 4. 支持的内置 transformer 及参数
| name         | 作用     | 主要参数（parameter）       | 说明/示例 |
|--------------|--------|----------------------------|-----------|
| trim         | 去除首尾空格 | columnIndex                |           |
| replace      | 字符串替换  | columnIndex, oldValue, newValue |         |
| filter       | 过滤记录   | columnIndex, value, op(=,!=,>,<) | 只判断第一个字段 |
| dateformat   | 日期格式转换 | columnIndex, fromFormat, toFormat | 支持String/DateColumn |
| md5encrypt   | MD5加密  | columnIndex                |           |
| zeroonetoboolean   | o和1转换为布尔值    | columnIndex      |           |

## 5. 典型场景与注意事项
- 支持多 transformer 串联，顺序影响结果。
- 字段类型需为 StringColumn/DateColumn，非目标类型自动跳过。
- 参数缺失、索引越界、类型不符时自动跳过并输出日志，不影响主流程。
- filter 只判断 columnIndexSet 的第一个字段，避免多字段误过滤。
- dateformat 支持字符串和日期类型，格式不符时有详细日志。

## 6. 扩展与自定义
- 支持自定义 transformer，注册到 TransformerRegistry 即可。
- 详见 com.paipi.transformer.impl 包和 transformer 注册机制。

--- 