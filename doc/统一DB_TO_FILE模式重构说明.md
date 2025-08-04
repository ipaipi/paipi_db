# DB_TO_FILE模式统一重构操作与设计说明

## 一、重构背景与目标

- 原有“拉数落地文件”模式分为多种（DB_TO_PARQUET_CHUNK、DB_TO_PARQUET、DB_TO_CSV_CHUNK、DB_TO_CSV），导致代码、文档、配置分散、冗余，扩展性差。
- 目标是**统一为DB_TO_FILE(0)模式**，支持多种文件格式（csv、parquet、json等），分块/单文件灵活切换，参数简洁，文档一致，便于维护和扩展。

## 二、主要重构思路

1. **SqlMode枚举统一**
   - 只保留DB_TO_FILE(0)一种“拉数落地文件”模式，注释明确支持多格式、多分块。
   - 相关分支、判断、注释全部合并为DB_TO_FILE。

2. **downloadData方法与FileWriter机制优化**
   - downloadData方法不再区分多种模式，全部走DB_TO_FILE。
   - 通过fileType、isChunk、chunkSize等参数灵活控制导出格式和分块方式。
   - FileWriterFactory支持注册扩展机制，未来新增格式无需改downloadData。
   - 目录结构、indexMap.json、文件命名等全部由描述器统一管理。

3. **文档与配置模板同步**
   - 《模式与参数说明.md》、README、设计记录等文档全部合并为DB_TO_FILE模式，参数、样例、FAQ同步。
   - local_test/datasource-config-model-0.json等配置模板全部只用sqlMode=0，参数结构与文档一致。
   - 明确indexMap.json结构、路径、作用，便于下游解析。

4. **兼容性与扩展性**
   - 旧配置如有0~3，全部自动兼容为0，建议用户升级配置。
   - 未来如需支持orc、txt等新格式，只需实现FileWriter并注册，无需改主流程。

## 三、关键操作步骤

1. 修改SqlMode.java，合并0~3为DB_TO_FILE(0)。
2. 全局替换/合并所有相关代码分支、注释、日志为DB_TO_FILE。
3. 优化downloadData方法，参数化fileType、isChunk、chunkSize，去除模式分支。
4. 优化FileWriterFactory，支持扩展注册和统一命名。
5. 更新文档（模式与参数说明、README、设计记录等），只保留DB_TO_FILE模式，补充indexMap.json结构与路径说明。
6. 更新配置模板，确保与文档、代码一致。
7. 检查并测试典型导出目录结构、indexMap.json生成、分块/单文件等场景。

## 四、注意事项与最佳实践

- fileType、isChunk、chunkSize等参数灵活组合，满足各种导出需求。
- indexMap.json结构和路径在所有fileType下保持一致，便于下游统一解析。
- downloadData方法只负责调度和数据流转，所有文件命名、分块、表头、写出细节交给FileWriterDescriptor。
- 未来扩展新格式时，只需实现FileWriter和注册，无需改主流程。
- 文档、注释、样例、FAQ需同步更新，保持一致性。

## 五、典型目录结构与indexMap.json示例

详见《模式与参数说明.md》DB_TO_FILE部分。

---

## 六、信息来源与推荐阅读

本次重构及架构理解主要参考和建议阅读以下文档与代码：

### 1. 主要文档
- `doc/模式与参数说明.md`：所有模式用途、参数结构、样例、indexMap.json结构与路径说明。
- `README.md`：项目简介、架构、插件开发、FAQ、文档索引。
- `doc/设计记录.md`：整体设计思路、架构图、核心功能与优化点说明。
- `doc/统一DB_TO_FILE模式重构说明.md`（本文件）：重构操作与设计总结。
- 各数据源配置模板（如`doc/Mysql配置模板.md`、`doc/Hive配置模板.md`等）：参数细节与典型样例。

### 2. 关键代码文件
- `src/main/java/com/paipi/adapter/base/BaseAdapter.java`：核心导出方法downloadData、参数解析、indexMap生成等。
- `src/main/java/com/paipi/filewriter/FileWriterFactory.java` 及各FileWriter实现：文件写出策略、扩展机制、命名规则。
- `src/main/java/com/paipi/enums/SqlMode.java`：模式枚举定义。
- `src/main/java/com/paipi/Engine.java`、`src/main/java/com/paipi/operator/DBToFileOperator.java`：主流程调度与Operator分发。
- 典型配置样例：`local_test/datasource-config-model-0.json`。

### 3. 推荐阅读顺序
1. 先通读《模式与参数说明.md》《README.md》《设计记录.md》，理解整体架构与参数体系。
2. 阅读BaseAdapter、FileWriterFactory等核心代码，理解导出流程与扩展点。
3. 结合配置模板和典型样例，跑通一条完整链路。
4. 如需扩展新格式或优化流程，参考本文件和相关注释。

---

本说明用于团队后续查阅、维护和新成员快速理解DB_TO_FILE模式的设计与实现。如有新需求或优化建议，请在本文件补充。 