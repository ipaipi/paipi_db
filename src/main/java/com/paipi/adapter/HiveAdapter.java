package com.paipi.adapter;

import com.paipi.adapter.base.BaseAdapter;
import com.paipi.common.channel.Channel;
import com.paipi.common.channel.Record;
import com.paipi.common.column.*;
import com.paipi.constant.HiveParameterConstant;
import com.paipi.constant.ParameterConstant;
import com.paipi.enums.AuthMechanism;
import com.paipi.filewriter.FileWriter;
import com.paipi.filewriter.FileWriterFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.UserGroupInformation;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.PrivilegedExceptionAction;
import java.sql.*;
import java.util.*;

import static com.paipi.utils.StringUtils.quot;


public class HiveAdapter extends BaseAdapter {
    private static final long serialVersionUID = 1L;
    public String auth_mechanism;
    public String principal;
    public String sep;
    public com.paipi.config.Configuration kerberosConfig;
    protected String defaultHdfsTempPath = "/tmp";
    protected FileSystem fileSystem;

    public HiveAdapter(com.paipi.config.Configuration originalConfig, com.paipi.config.Configuration config) throws Exception {
        super(originalConfig, config);

        // 认证机制初始化
        auth_mechanism = config.getString(HiveParameterConstant.CONNECTION_AUTH_MECHANISM);
        if (AuthMechanism.KERBEROS.getName().equalsIgnoreCase(auth_mechanism)) {
            kerberosConfig = config.getConfiguration(HiveParameterConstant.CONNECTION_KERBEROS_SERVICE_NAME);
            principal = kerberosConfig.getString(HiveParameterConstant.KERBEROS_PRINCIPAL);
            // Kerberos 认证
            initAuthKrb5();
        }

        // 初始化 HDFS 文件系统
        initFileSystem();
    }

    public void initAuthKrb5() throws Exception {
        // Kerberos 认证配置
        System.setProperty("java.security.krb5.conf", kerberosConfig.getString(HiveParameterConstant.KERBEROS_KEYTAB_CONF));
        //System.setProperty("sun.security.krb5.debug", "true"); // 开启Kerberos调试

        // 配置hadoop安全认证
        Configuration conf = new Configuration();
        conf.set("hadoop.security.authentication", "kerberos");
        UserGroupInformation.setConfiguration(conf);

        // 使用keytab登录
        UserGroupInformation.loginUserFromKeytab(
                kerberosConfig.getString(HiveParameterConstant.KERBEROS_KEYTAB_USERNAME),
                kerberosConfig.getString(HiveParameterConstant.KERBEROS_KEYTAB_FILE)
        );
        this.loginUser = UserGroupInformation.getLoginUser();
    }

    public void initFileSystem() throws Exception {
        // 初始化 HDFS 文件系统，支持 Kerberos 认证
        String ip = config.getString(HiveParameterConstant.CONNECTION_HDFS_IP, null);
        if (StringUtils.isNotBlank(ip)) {
            String port = StringUtils.isNotBlank(config.getString(HiveParameterConstant.CONNECTION_HDFS_PORT)) ? config.getString(HiveParameterConstant.CONNECTION_HDFS_PORT) : "8020";
            String user = config.getString(HiveParameterConstant.CONNECTION_HDFS_PORT);

            Configuration configuration = new Configuration();
            if (AuthMechanism.KERBEROS.getName().equals(auth_mechanism) && StringUtils.isNotBlank(kerberosConfig.getString(HiveParameterConstant.HDFS_KEYTAB_USERNAME))) {
                configuration.setBoolean("hadoop.security.authorization", true);
                configuration.set("hadoop.security.authentication", "kerberos");
                configuration.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");

                UserGroupInformation.setConfiguration(configuration);
                UserGroupInformation.loginUserFromKeytab(kerberosConfig.getString(HiveParameterConstant.HDFS_KEYTAB_USERNAME), kerberosConfig.getString(HiveParameterConstant.HDFS_KEYTAB_FILE));
            } else {
                configuration.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");
            }

            if (AuthMechanism.KERBEROS.getName().equals(auth_mechanism) && StringUtils.isNotBlank(kerberosConfig.getString(HiveParameterConstant.HDFS_KEYTAB_FILE))) {
                fileSystem = FileSystem.get(new URI("hdfs://" + ip + ":" + port), configuration);
            } else {
                fileSystem = FileSystem.get(new URI("hdfs://" + ip + ":" + port), configuration, user);
            }
        }
    }


    @Override
    public String getJdbcUrl() {
        // 生成 Hive JDBC 连接 URL
        String auth = "";
        if (AuthMechanism.KERBEROS.getName().equals(auth_mechanism)) {
            auth = "principal=" + principal;
        }

        if (AuthMechanism.PLAIN.getName().equals(auth_mechanism)) {
            auth = "user=" + this.userName + ";" + "password=" + this.passWord;
        }

        String dbName = this.database;
        if ("default".equals(this.database)) {
            dbName = "";
        }

        String url = this.ip + ":" + this.port + "/" + dbName + ";" + auth + ";" + assembleJdbcUrlParam();
        return "hive2://" + url;
    }

    public String assembleJdbcUrlParam() {
        // 拼接 JDBC URL 参数
        Map<String, String> param = new HashMap<>();
        param.put("useUnicode", "true");
        param.put("characterEncoding", "utf8");
        param.put("hive.resultset.use.unique.column.names", "false");

        Map<String, Object> jdbcUrlParam = config.getMap(ParameterConstant.CONNECTION_JDBC_URL_PARAM);
        if (jdbcUrlParam != null && !jdbcUrlParam.isEmpty()) {
            for (Map.Entry<String, Object> entry : jdbcUrlParam.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    param.put(entry.getKey(), entry.getValue().toString());
                }
            }
        }

        StringBuilder urlParam = new StringBuilder();
        for (Map.Entry<String, String> entry : param.entrySet()) {
            if (StringUtils.isBlank(entry.getKey()) || StringUtils.isBlank(entry.getValue())) {
                continue;
            }
            urlParam.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
        }
        if (urlParam.length() > 0) {
            urlParam.deleteCharAt(urlParam.length() - 1);
        }
        return urlParam.toString();
    }


    @Override
    public String getDriverName() {
        // 返回 Hive JDBC 驱动类名
        return "org.apache.hive.jdbc.HiveDriver";
    }

    @Override
    public String dbTypeToInternalType(String colType) {
        // 数据库的类型转成内部类型
        if (colType == null) return StringColumn.TYPE_NAME;
        String type = colType.toLowerCase();
        if (type.contains("int")) return IntColumn.TYPE_NAME;
        if (type.contains("bigint") || type.contains("long")) return LongColumn.TYPE_NAME;
        if (type.contains("double") || type.contains("float")) return DoubleColumn.TYPE_NAME;
        if (type.contains("char") || type.contains("string") || type.contains("varchar")) return StringColumn.TYPE_NAME;
        if (type.contains("bool")) return BoolColumn.TYPE_NAME;
        if (type.contains("date") || type.contains("time")) return DateColumn.TYPE_NAME;
        if (type.contains("binary") || type.contains("bytes")) return BytesColumn.TYPE_NAME;
        return StringColumn.TYPE_NAME;
    }

    @Override
    public List<Object> internalTypeToDbType(ColumnStruct column) {
        // 内部类型转成数据库类型
        switch (column.getType()) {
            case STRING:
                return Arrays.asList("string", column.getPrecision());
            case INT:
                return Arrays.asList("int", column.getPrecision());
            case LONG:
                return Arrays.asList("bigint", column.getPrecision());
            case DOUBLE:
                return Arrays.asList("double", column.getPrecision(), column.getScale());
            case BOOL:
                return Arrays.asList("boolean");
            case DATE:
                return Arrays.asList("timestamp");
            case BYTES:
                return Arrays.asList("binary");
            default:
                return Arrays.asList("string", 255);
        }
    }

    @Override
    public String getCreateTableSql(String tableName, String[] colNames, String[] colTypes) {
        String fileType = config.getString(ParameterConstant.FILE_TYPE, "orc").toLowerCase();
        String partition = config.getString(HiveParameterConstant.PARTITION, null);

        StringBuilder cols = new StringBuilder();
        for (int i = 0; i < colNames.length; i++) {
            String colName = colNames[i];
            String[] col_name = colName.split("\\.");
            if (col_name.length > 1) {
                cols.append(col_name[1]).append(" ");
            } else {
                cols.append(colName).append(" ");
            }
            // 类型字符串转枚举
            Column.Type type = Column.Type.valueOf(colTypes[i].toUpperCase());
            // 构造ColumnStruct
            ColumnStruct struct = new ColumnStruct();
            struct.setType(type);
            struct.setColumnName(colName);
            // 获取Hive类型
            List<Object> typeInfo = internalTypeToDbType(struct);
            cols.append(typeInfo.get(0));
            // 如有精度、标度可拼接
            if (typeInfo.size() > 1 && typeInfo.get(1) != null) {
                cols.append("(").append(typeInfo.get(1));
                if (typeInfo.size() > 2 && typeInfo.get(2) != null) {
                    cols.append(",").append(typeInfo.get(2));
                }
                cols.append(")");
            }
            cols.append(", ");
        }
        // 移除最后一个逗号
        if (cols.length() > 2) {
            cols.setLength(cols.length() - 2);
        }
        StringBuilder sql = new StringBuilder();
        sql.append("create table  ").append(tableName).append(" (").append(cols).append(") ");
        if (partition != null && !partition.isEmpty()) {
            String[] parts = partition.split(",");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                String field = part.split("=")[0].trim();
                sb.append(field).append(" string,");
            }
            if (sb.length() > 0) sb.setLength(sb.length() - 1);
            sql.append("partitioned by (").append(sb).append(") ");
        }
        if (sep != null) {
            sql.append("row format delimited fields terminated by '").append(sep).append("' ");
        }
        // fileType控制表类型
        if ("orc".equals(fileType) || "parquet".equals(fileType)) {
            sql.append("stored as ").append(fileType).append(" ");
        } else if ("csv".equals(fileType) || "text".equals(fileType) || "txt".equals(fileType)) {
            sql.append("stored as textfile ");
        }
        return sql.toString().trim();
    }

    @Override
    public String getSql(String tableName, String cols, String where, String orderBy, boolean getStuct, String fullSql) {
        // 1. 安全获取表结构信息
        Map<String, String> colMap = null;
        try {
            colMap = getTableColumns(tableName, fullSql);
        } catch (Exception e) {
            throw new RuntimeException("获取结构信息异常,入参:tableName=" + tableName + ", full sql=" + fullSql, e);
        }

        // 2. 构建基础WHERE条件（用于获取结构或正常查询）
        String preWhere = getStuct ? " where 0=1" : " where 1=1";

        // 3. 处理列选择部分
        // 3.1 如果未指定列或为*，则使用所有列
        if ((cols == null || cols.equals("*")) && colMap != null) {
            cols = String.join(",", colMap.keySet());
        }
        // 3.2 如果指定了列且不为*，则处理每列
        else if (cols != null && !cols.equals("*")) {
            // 处理可能存在歧义的列分隔符
            String[] tokens = cols.split(",");

            // 处理每个列的定义
            StringBuilder colsBuilder = new StringBuilder();
            for (String token : tokens) {
                String colName = token.trim();
                String asName = null;

                // 处理列别名
                if (colName.contains(" ")) {
                    int space = colName.lastIndexOf(" ");
                    asName = colName.substring(space).trim();
                    colName = colName.substring(0, space).trim();
                } else {
                    colName = "`" + colName + "`";
                }

                // 处理varchar类型的转换
                boolean isVarchar = colMap != null && colMap.getOrDefault(colName, "").contains("varchar");
                String finalName = isVarchar ? "cast(" + colName + " as string)" : colName;

                // 添加别名
                if (asName != null) {
                    finalName += " " + asName;
                } else if (isVarchar) {
                    finalName += " " + colName;
                }

                colsBuilder.append(finalName).append(",");
            }
            cols = colsBuilder.length() > 0 ? colsBuilder.substring(0, colsBuilder.length() - 1) : "*";
        } else {
            cols = "*";
        }

        // 4. 处理WHERE条件
        if (!where.isEmpty()) {
            where = preWhere + " and " + where;
        } else {
            where = getStuct ? preWhere : "";
        }

        // 5. 处理ORDER BY部分
        if (orderBy != null && !orderBy.isEmpty()) {
            orderBy = " order by " + orderBy;
        } else {
            orderBy = "";
        }

        // 6. 构建最终SQL
        String sql;
        if (fullSql != null && !fullSql.isEmpty()) {
            sql = "select " + cols + " from (" + fullSql + ") t " + where + " " + orderBy;
        } else {
            sql = "select " + cols + " from " + tableName + " " + where + " " + orderBy;
        }

        return sql.trim();
    }

    /**
     * 获取Hive表的字段名及类型映射
     * <p>
     * 该方法用于获取指定表（或SQL查询结果）的所有字段名及其对应的类型。
     * 支持两种场景：
     * 1. 传入tableName时，获取该表的字段名和类型。
     * 2. 未传tableName但传入fullSql时，获取该SQL查询结果的字段名和类型。
     * 类型会统一转换为内部类型（如DataX全链路类型统一）。
     *
     * @param tableName 表名（可为空，为空时用fullSql）
     * @param fullSql   查询SQL（可为空，优先用tableName）
     * @return 字段名到内部类型的有序映射（LinkedHashMap）
     * @throws Exception 获取表结构或SQL结构异常
     */
    public Map<String, String> getTableColumns(String tableName, String fullSql) throws Exception {
        Map<String, String> colMap = null;
        Connection con = connection();
        Statement stmt = null;
        try {
            stmt = con.createStatement();
            if (StringUtils.isNotBlank(tableName)) {
                // 获取原始类型
                Map<String, String> rawColMap = new LinkedHashMap<>();
                try (ResultSet resultSet = stmt.executeQuery("desc " + tableName)) {
                    while (resultSet.next()) {
                        String colName = resultSet.getString(1);
                        String dataType = resultSet.getString(2);
                        if (!StringUtils.isBlank(colName) && !StringUtils.isBlank(dataType)) {
                            if (colName.trim().startsWith("#")) break;
                            rawColMap.put(colName, dataType);
                        }
                    }
                }
                LinkedHashMap<String, String> unifiedColMap = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : rawColMap.entrySet()) {
                    unifiedColMap.put(entry.getKey(), dbTypeToInternalType(entry.getValue()));
                }
                colMap = unifiedColMap;
            } else if (StringUtils.isNotBlank(fullSql)) {
                String tempFullSql = "select * from (" + fullSql + ") t where 0=1";
                Map<String, String> rawColMap = new LinkedHashMap<>();
                // explain 解析类型
                try (ResultSet resultSet = stmt.executeQuery("explain " + tempFullSql)) {
                    while (resultSet.next()) {
                        String outputline = resultSet.getString(1).trim();
                        if (outputline.startsWith("expressions:")) {
                            String[] tokensArr = outputline.split("expressions:")[1].split(", ");
                            for (String token : tokensArr) {
                                String[] tokens = token.trim().split(" ");
                                if (tokens.length >= 3) {
                                    rawColMap.put(tokens[0], tokens[2].substring(0, tokens[2].length() - 1));
                                }
                            }
                        }
                    }
                }
                ResultSetMetaData metaData = null;
                try (ResultSet rs = stmt.executeQuery(tempFullSql)) {
                    metaData = rs.getMetaData();
                    ArrayList<Object> header = new ArrayList<>();
                    for (int i = 1; i <= metaData.getColumnCount(); i++) {
                        String colName = metaData.getColumnLabel(i);
                        if (colName.contains(".")) {
                            colName = colName.split("\\.")[1];
                        }
                        header.add(colName);
                    }
                    LinkedHashMap<String, String> updatedColMap = new LinkedHashMap<>();
                    for (int i = 0; i < header.size(); i++) {
                        String colName = header.get(i).toString();
                        String rawType = (String) rawColMap.values().toArray()[i];
                        updatedColMap.put(colName, dbTypeToInternalType(rawType));
                    }
                    colMap = updatedColMap;
                }
            }
        } catch (Exception e) {
            logger.error("---- 发生异常: {}", e.getMessage(), e);
            throw e;
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (Exception e) {
                    logger.warn("关闭Statement异常: {}", e.getMessage(), e);
                }
            }
        }

        return colMap;
    }

    @Override
    public Statement getStatement(Connection con, int fetchSize) throws SQLException {
        Statement stmt = con.createStatement();
        stmt.execute("set hive.resultset.use.unique.column.names=false");
        return stmt;
    }

    @Override
    protected Connection connection() throws SQLException {
        String jdbcUrl = String.format("jdbc:%s", getJdbcUrl());
        Driver driver = DriverManager.getDriver(jdbcUrl);
        Properties properties = new Properties();
        if (Objects.isNull(principal)) {
            return driver.connect(jdbcUrl, properties);
        }
        try {
            return loginUser.doAs((PrivilegedExceptionAction<Connection>) () -> driver.connect(jdbcUrl, properties));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    // 拼接并执行批量insert
    private void writeBatch(java.sql.Statement stmt, String sqlPrefix, java.util.List<Record> buffer, java.util.List<String> colTypes) throws Exception {
        StringBuilder sql = new StringBuilder(sqlPrefix);
        for (int i = 0; i < buffer.size(); i++) {
            Record record = buffer.get(i);
            sql.append("(");
            for (int j = 0; j < colTypes.size(); j++) {
                Column col = record.getColumn(j);
                String type = colTypes.get(j);
                sql.append(toHiveLiteral(col, type));
                if (j != colTypes.size() - 1) sql.append(",");
            }
            sql.append(")");
            if (i != buffer.size() - 1) sql.append(",");
        }
        stmt.executeUpdate(sql.toString());
    }

    private String toHiveLiteral(Column col, String type) {
        if (col == null || col.getRawData() == null) {
            return "null";
        }
        switch (type.toUpperCase()) {
            case "STRING":
            case "DATE":
                // 字符串和日期都用单引号包裹，内部单引号转义
                return quot(col.asString());
            case "INT":
            case "LONG":
                Long l = col.asLong();
                return l == null ? "null" : l.toString();
            case "DOUBLE":
                Double d = col.asDouble();
                return d == null ? "null" : d.toString();
            case "BOOL":
                Boolean b = col.asBoolean();
                return b == null ? "null" : (b ? "true" : "false");
            case "BYTES":
                byte[] bytes = col.asBytes();
                // 转16进制字符串，并用单引号包裹
                return bytes == null ? "null" : quot(bytesToHex(bytes));
            default:
                // 复杂结构、未知类型一律转字符串
                return quot(col.asString());
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * 解析文件大小字符串（如128MB/1GB/1024等）为字节数
     */
    private long parseFileSize(String sizeStr) {
        sizeStr = sizeStr.trim().toUpperCase();
        if (sizeStr.endsWith("GB")) return Long.parseLong(sizeStr.replace("GB", "")) * 1024 * 1024 * 1024;
        if (sizeStr.endsWith("MB")) return Long.parseLong(sizeStr.replace("MB", "")) * 1024 * 1024;
        if (sizeStr.endsWith("KB")) return Long.parseLong(sizeStr.replace("KB", "")) * 1024;
        return Long.parseLong(sizeStr);
    }

    /**
     * JDBC 批量 insert 回退方案
     */
    protected void batchInsertByJdbc(Channel<Record> channel, String tableName, List<String> colNames) throws Exception {
        boolean channelClosed = false;
        int batchSize = insertBatchSize > 0 ? insertBatchSize : 5000;
        StringBuilder colStr = new StringBuilder();
        for (int i = 0; i < colNames.size(); i++) {
            colStr.append(colNames.get(i));
            if (i != (colNames.size() - 1)) colStr.append(",");
        }
        String sqlPrefix = "insert into " + tableName + " (" + colStr + ") values ";
        java.util.List<Record> buffer = new java.util.ArrayList<>(batchSize);
        try (java.sql.Connection con = getConn(); java.sql.Statement stmt = con.createStatement()) {
            while (true) {
                List<Record> records = channel.pullAll(batchSize);
                if (records == null || records.isEmpty()) break;
                buffer.addAll(records);
                if (buffer.size() >= batchSize) {
                    writeBatch(stmt, sqlPrefix, buffer, null);
                    buffer.clear();
                }
            }
            if (!buffer.isEmpty()) {
                writeBatch(stmt, sqlPrefix, buffer, null);
            }
        } catch (Exception e) {
            channel.close();
            channelClosed = true;
            throw e;
        } finally {
            if (!channelClosed) {
                channel.close();
            }
        }
    }


    @Override
    public void writerPlugin(Channel<Record> channel) throws Exception {
        try {
            // 1. 解析参数
            String tableName = config.getString(ParameterConstant.TABLE, this.table);
            List<String> colNames = ColumnParser.parseColumns(config.getList(ParameterConstant.COLUMN)).getColumnNamesList();
            List<String> colTypes = ColumnParser.parseColumns(config.getList(ParameterConstant.COLUMN)).getColumnTypesList();
            if (colTypes == null || colTypes.isEmpty()) {
                // 若未指定类型，则从表结构获取
                Map<String, String> colTypeMap = getTableColumns(tableName, null);
                colTypes = new ArrayList<>();
                for (String col : colNames) {
                    colTypes.add(colTypeMap.getOrDefault(col, "string"));
                }
            }
            if (colNames == null || colNames.isEmpty()) throw new IllegalArgumentException("colNames参数不能为空");
            List<ColumnStruct> colStructs = new ArrayList<>();
            for (int i = 0; i < colNames.size(); i++) {
                colStructs.add(new ColumnStruct(colNames.get(i), colTypes.get(i), i));
            }
            String fileType = config.getString(ParameterConstant.FILE_TYPE, "orc").toLowerCase();
            String writeMode = config.getString(ParameterConstant.WRITE_MODE, "insert").toLowerCase();
            String fieldDelimiter = config.getString(ParameterConstant.SEP, ",");
            String encoding = config.getString(HiveParameterConstant.ENCODING, "UTF-8");
            String compress = config.getString(HiveParameterConstant.COMPRESS, "NONE").toUpperCase();
            String partition = config.getString(HiveParameterConstant.PARTITION, null);
            String tmpPath = config.getString(HiveParameterConstant.CONNECTION_HDFS_TEMP_PATH, defaultHdfsTempPath);
            String maxFileSizeStr = config.getString(HiveParameterConstant.MAX_FILE_SIZE, "128MB");
            String fileNamePrefix = config.getString(HiveParameterConstant.FILE_NAME, "paipi_db_output");
            long maxFileSize = parseFileSize(maxFileSizeStr);
            int batchSize = insertBatchSize > 0 ? insertBatchSize : 5000;

            // 2. 分片写本地文件，达到指定大小后切分
            List<File> partFiles = new ArrayList<>();
            int partIdx = 0;
            FileWriter writer = null;
            File curFile = null;
            long totalWrite = 0;
            try {
                while (true) {
                    List<Record> records = channel.pullAll(batchSize);
                    if (records == null || records.isEmpty()) break;
                    for (Record record : records) {
                        // 判断是否需要新分片（首次或超出最大分片大小）
                        if (writer == null || curFile.length() > maxFileSize) {
                            if (writer != null) {
                                try {
                                    writer.close();
                                } catch (Exception e) {
                                    logger.warn("writer close error", e);
                                    throw e;
                                }
                            }
                            // 构造唯一分片文件名
                            String partFileName = fileNamePrefix + "_" + partIdx + "_" + System.currentTimeMillis() + FileWriterFactory.getDescriptor(fileType).getFileSuffix();
                            curFile = File.createTempFile(partFileName, null);
                            curFile.deleteOnExit();
                            writer = FileWriterFactory.createWriter(fileType, curFile.getAbsolutePath(), colNames, colStructs, fieldDelimiter, compress, encoding);
                            partFiles.add(curFile);
                            partIdx++;
                        }
                        // 写入一行数据
                        List<Object> row = new ArrayList<>();
                        for (int i = 0; i < record.getColumnNumber(); i++) {
                            Column col = record.getColumn(i);
                            String type = colTypes.get(i).toUpperCase();
                            Object value;
                            if (col == null) {
                                value = null;
                            } else {
                                switch (type) {
                                    case "INT":
                                    case "LONG":
                                        value = col.asLong();
                                        break;
                                    case "DOUBLE":
                                        value = col.asDouble();
                                        break;
                                    case "BOOL":
                                        value = col.asBoolean();
                                        break;
                                    default:
                                        value = col.asString();
                                }
                            }
                            row.add(value);
                        }
                        writer.writeRow(row);
                        totalWrite++;
                        if (totalWrite % recordingTotalWrite == 0) {
                            logger.info("[Thread-{}] 已写入{}条数据", Thread.currentThread().getId(), totalWrite);
                        }
                    }
                }
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }

            // 3. 上传分片文件到HDFS并load data，上传后删除本地文件
            for (File partFile : partFiles) {
                // 上传到HDFS
                String hdfsPath = tmpPath + "/" + partFile.getName();
                Path hdfsDest = new Path(hdfsPath);
                if (fileSystem.exists(hdfsDest)) fileSystem.delete(hdfsDest, true);
                fileSystem.copyFromLocalFile(new Path(partFile.getAbsolutePath()), hdfsDest);
                // 加载数据到hive
                StringBuilder loadSql = new StringBuilder();
                loadSql.append("load data inpath '").append(hdfsPath).append("' ");
                if ("overwrite".equals(writeMode)) {
                    loadSql.append("overwrite ");
                }
                loadSql.append("into table ").append(tableName);
                if (partition != null && !partition.isEmpty()) {
                    loadSql.append(" partition(").append(partition).append(")");
                }
                logger.info("executeLoadData. sql={}", loadSql.toString());
                this.executeSQL(loadSql.toString());
                // 上传后删除本地分片文件
                if (partFile.exists()) {
                    boolean deleted = partFile.delete();
                    if (!deleted) {
                        logger.warn("本地分片文件删除失败: {}", partFile.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("writerPlugin error", e);
            throw e;
        } finally {
            try {
                channel.close();
                logger.info("Channel closed in writerPlugin for HiveAdapter");
            } catch (Exception ex) {
                logger.warn("Exception when closing channel in writerPlugin: {}", ex.getMessage(), ex);
            }
        }
    }


    @Override
    public java.util.List<com.paipi.config.Configuration> split(int adviceNumber) throws Exception {
        String tableName = config.getString(ParameterConstant.TABLE, this.table);
        String splitPk = config.getString(ParameterConstant.SPLIT_PK);
        if (StringUtils.isBlank(splitPk)) {
            // 没有分片主键，单分片
            java.util.List<com.paipi.config.Configuration> splits = new java.util.ArrayList<>();
            splits.add(config.clone());
            return splits;
        }
        // 查询最小最大值
        try (java.sql.Connection con = getConn(); java.sql.Statement stmt = con.createStatement()) {
            java.sql.ResultSet rs = stmt.executeQuery("SELECT MIN(" + splitPk + "), MAX(" + splitPk + ") FROM " + tableName);
            if (!rs.next()) throw new RuntimeException("无法获取分片列范围");
            Object minObj = rs.getObject(1);
            Object maxObj = rs.getObject(2);
            if (!(minObj instanceof Number) || !(maxObj instanceof Number)) {
                // 不是数字类型，单分片
                java.util.List<com.paipi.config.Configuration> splits = new java.util.ArrayList<>();
                splits.add(config.clone());
                return splits;
            }
            long min = ((Number) minObj).longValue(), max = ((Number) maxObj).longValue();
            if (min == max) { // 只有一行
                java.util.List<com.paipi.config.Configuration> splits = new java.util.ArrayList<>();
                com.paipi.config.Configuration conf = config.clone();
                String finalWhere = config.getString(ParameterConstant.WHERE, "");
                String splitWhere = splitPk + " = " + min;
                if (StringUtils.isNotBlank(splitWhere)) {
                    if (StringUtils.isBlank(finalWhere)) {
                        finalWhere = splitWhere;
                    } else {
                        finalWhere = "(" + finalWhere + ") AND (" + splitWhere + ")";
                    }
                }
                conf.set(ParameterConstant.WHERE, finalWhere);
                splits.add(conf);
                return splits;
            }
            long step = (max - min + 1) / adviceNumber;
            java.util.List<com.paipi.config.Configuration> splits = new java.util.ArrayList<>();
            for (int i = 0; i < adviceNumber; i++) {
                long start = min + i * step;
                long end = (i == adviceNumber - 1) ? max : (start + step - 1);
                com.paipi.config.Configuration conf = config.clone();
                String finalWhere = config.getString(ParameterConstant.WHERE, "");
                String splitWhere = splitPk + " >= " + start + " AND " + splitPk + " <= " + end;
                if (StringUtils.isNotBlank(splitWhere)) {
                    if (StringUtils.isBlank(finalWhere)) {
                        finalWhere = splitWhere;
                    } else {
                        finalWhere = "(" + finalWhere + ") AND (" + splitWhere + ")";
                    }
                }
                conf.set(ParameterConstant.WHERE, finalWhere);
                splits.add(conf);
            }
            return splits;
        }
    }
}
