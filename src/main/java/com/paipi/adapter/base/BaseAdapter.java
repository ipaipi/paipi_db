package com.paipi.adapter.base;

import com.alibaba.fastjson.JSON;
import com.paipi.common.Constants;
import com.paipi.common.channel.Channel;
import com.paipi.common.channel.Record;
import com.paipi.common.column.*;
import com.paipi.config.Configuration;
import com.paipi.constant.CoreConstant;
import com.paipi.constant.ParameterConstant;
import com.paipi.exception.DbAdapterException;
import com.paipi.filewriter.FileWriterDescriptor;
import com.paipi.filewriter.FileWriterFactory;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;


@Getter
@Setter
public abstract class BaseAdapter implements Serializable {
    // 1. 静态常量
    protected static final Logger logger = LogManager.getLogger(BaseAdapter.class);

    // 2. 成员变量
    protected String ip;
    protected String port;
    protected String userName;
    protected String passWord;
    protected String database;
    protected String table;
    // 原始配置，备用
    protected Configuration originalConfig;
    // adapter 配置 job.content[0].demo.parameter
    protected Configuration config;

    protected UserGroupInformation loginUser = null;
    protected String saveUrl;
    protected int rowBuffer = 1000000;
    protected String schema;
    protected int selectBatchSize = 5000;
    protected int insertBatchSize = 5000;
    protected Set<String> loadedDrivers = new HashSet<>();
    // 删除全局con字段
    protected Connection con = null;

    protected int caseSensitive = 1;
    protected String dbType;
    protected long recordingTotalWrite = 1000;

    // 3. 构造方法
    protected BaseAdapter(Configuration originalConfig, Configuration config) {
        this.originalConfig = originalConfig;
        this.config = config;

        this.ip = config.getString(ParameterConstant.CONNECTION_IP);
        this.port = config.getString(ParameterConstant.CONNECTION_PORT);
        this.userName = config.getString(ParameterConstant.CONNECTION_USERNAME);
        this.passWord = config.getString(ParameterConstant.CONNECTION_PASSWORD);
        this.database = config.getString(ParameterConstant.CONNECTION_DATABASE);
        this.schema = config.getString(ParameterConstant.CONNECTION_SCHEMA);
        this.table = config.getString(ParameterConstant.TABLE, "");

        this.saveUrl = config.getString(ParameterConstant.SAVE_URL, "");

        this.dbType = config.getString(CoreConstant.DB_JOB_CONTENT_COMMON_NAME) != null ? config.getString(CoreConstant.DB_JOB_CONTENT_COMMON_NAME) : config.getString(CoreConstant.DB_JOB_CONTENT_READER_NAME) != null ? config.getString(CoreConstant.DB_JOB_CONTENT_READER_NAME) : config.getString(CoreConstant.DB_JOB_CONTENT_WRITER_NAME);
    }

    /**
     * 日期文本转Date对象，例如输入 Dec-18, 2022-11-12
     *
     * @param dateStr 必填，日期文本
     * @param sdf     可选,日期格式化
     * @return 返回日期java.lang.sql.date对象实例
     * @author zyf
     */
    public static Date parseDate(String dateStr, String sdf) throws ParseException {
        if (StringUtils.isNotBlank(sdf)) {
            try {
                SimpleDateFormat formatter = new SimpleDateFormat(sdf);
                return new Date(formatter.parse(dateStr).getTime());
            } catch (Exception e) {
                logger.warn("使用特定日期模式做日期转换异常： dateStr={" + dateStr + "},sdf={" + sdf + "}");
                return null;
            }
        }
        // 默认语言环境
        List<String> dateList = Arrays.asList("yyyy-MM-dd hh24:mi:ss.ff", "yyyy-MM-dd hh24:mi:ss", "yyyy-MM-dd HH:mm:ss.SSSS", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "yyyy/MM/dd", "yyyy-MM", "yyyyMM");
        for (String formatStr : dateList) {
            SimpleDateFormat formatter = new SimpleDateFormat(formatStr);
            return new Date(formatter.parse(dateStr).getTime());
        }

        // 英文格式日期，例如：Dec-84,Dec-1984
        // String regex = "[J][F][M][A][S][O][N][D]";
        List<String> enDateFormatList = Arrays.asList("MMM-yy", "MMM-yyyy");
        for (String enDateFormat : enDateFormatList) {
            try {
                SimpleDateFormat formatter = new SimpleDateFormat(enDateFormat, Locale.ENGLISH);
                return new Date(formatter.parse(dateStr).getTime());
            } catch (Exception ignored) {
            }
        }
        logger.warn("未找到合适的日期转换模式：dateStr={" + dateStr + "},sdf={" + sdf + "}");
        return null;
    }

    public String getSql(String tbName, String cols, String where, String orderBy, boolean getStuct, String querySql) {
        // 1. 处理列名
        cols = (cols == null) ? "*" : cols;

        // 2. 处理 where 条件
        String preWhere = getStuct ? " where 0=1" : " where 1=1";
        String whereClause = "";
        if (StringUtils.isNotBlank(where)) {
            String whereTrim = where.trim().toLowerCase();
            if (whereTrim.startsWith("and")) {
                whereClause = preWhere + " " + where;
            } else {
                whereClause = preWhere + " and " + where;
            }
        } else if (getStuct) {
            whereClause = preWhere;
        }

        // 3. 处理 order by
        String orderByClause = StringUtils.isNotBlank(orderBy) ? " order by " + orderBy : "";

        // 4. 拼接 SQL
        String sql;
        if (StringUtils.isNotBlank(querySql)) {
            sql = String.format("select %s from (%s) t%s%s", cols, querySql, whereClause, orderByClause);
        } else {
            sql = String.format("select %s from %s%s%s", cols, tbName, whereClause, orderByClause);
        }
        return sql.trim();
    }

    @Override
    protected void finalize() {
        try {
            if (con != null && !con.isClosed()) {
                con.close();
            }
        } catch (Exception ee) {
            //抛出异常
            logger.error("finalize Exception", ee);
            throw new DbAdapterException(ee);
        }
    }

    public Connection getConn() throws SQLException {
        // 连接可用直接返回
        if (con != null && !con.isClosed()) {
            return con;
        }
        int retry = Constants.MAX_RETRY;
        SQLException lastEx = null;
        for (int i = 1; i <= retry; i++) {
            try {
                con = getConnection();
                return con;
            } catch (SQLException | ClassNotFoundException e) {
                lastEx = (e instanceof SQLException) ? (SQLException) e : new SQLException(e);
                logger.warn(String.format("第%d次获取数据库连接失败，DriverName: %s, ExceptionType: %s, ErrorMessage: %s", i, this.getDriverName(), e.getClass(), e.getMessage()));
                if (i == retry) {
                    logger.error(String.format("重试%d次后仍无法获取数据库连接，抛出异常。", retry));
                    throw lastEx;
                }
                try {
                    Thread.sleep(Constants.RETRY_INTERVAL);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new SQLException("未知原因导致无法获取数据库连接");
    }

    public Connection getConnection() throws SQLException, ClassNotFoundException {
        // 只加载一次驱动
        if (!loadedDrivers.contains(this.getDriverName())) {
            Class.forName(this.getDriverName());
            loadedDrivers.add(this.getDriverName());
        }
        Connection conn = connection();
        // 新建物理连接后，自动执行 session SQL
        List<String> sessions = null;
        sessions = config.getList("session", String.class);
        if (sessions != null && !sessions.isEmpty()) {
            try (Statement stmt = conn.createStatement()) {
                for (String sessionSql : sessions) {
                    if (sessionSql != null && !sessionSql.trim().isEmpty()) {
                        stmt.execute(sessionSql);
                    }
                }
            }
        }
        return conn;
    }

    protected Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:" + getJdbcUrl(), this.userName, this.passWord);
    }

    public List<ColumnStruct> getColumnInfo(ResultSetMetaData rsmd, String tableName, boolean createFolder) throws SQLException {
        // 获取表结构，把列信息转换成ColumnStruct，db类型转成内部类型
        ArrayList<ColumnStruct> columnsInfo = new ArrayList<>();

        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
            String colName = rsmd.getColumnLabel(i);
            String tableNamePre = tableName + ".";
            if (colName.startsWith(tableNamePre)) {
                colName = colName.substring(tableNamePre.length());
            }
            Column.Type type = Column.Type.valueOf(dbTypeToInternalType(rsmd.getColumnTypeName(i)).toUpperCase());
            ColumnStruct cstruct = new ColumnStruct(colName, type, rsmd.getPrecision(i), rsmd.getScale(i), i);
            columnsInfo.add(cstruct);
        }
        if (createFolder) {
            createColumnDirs(columnsInfo);
        }
        return columnsInfo;
    }

    /**
     * 根据columnsInfo创建目录
     */
    protected void createColumnDirs(List<ColumnStruct> columnsInfo) {
        for (ColumnStruct cstruct : columnsInfo) {
            File fileDir = FileUtils.getFile(saveUrl, cstruct.getIndex());
            if (!fileDir.exists()) {
                boolean res = fileDir.mkdirs();
                if (!res) {
                    throw new DbAdapterException("创建目录失败：" + fileDir.getPath());
                }
            }
        }
    }

    /**
     * 通用批量SQL执行，支持单条、多条、文件、分号分隔。
     * 只会对最后一条SQL做特殊处理：
     * - 如果最后一条SQL执行后有结果集且配置了saveUrl，则导出查询结果到文件。
     * - 其它SQL（包括中间的SELECT）只顺序执行，不导出结果，只记录日志。
     * 这样设计是因为绝大多数场景只关心最后一条的结果，避免多次覆盖或混乱。
     *
     * @param sqlList SQL字符串列表（可为单条、多条）
     * @param saveUrl 查询结果导出路径（可选）
     * @throws Exception 执行异常
     */
    public void executeSQLBatch(List<String> sqlList, String saveUrl, String fileType, Boolean isChunk, int chunkSize, String sep) throws Exception {
        if (sqlList == null || sqlList.isEmpty()) return;
        Connection con = getConn();
        try {
            for (int i = 0; i < sqlList.size(); i++) {
                String sql = sqlList.get(i);
                if (StringUtils.isBlank(sql)) continue;
                boolean isLast = (i == sqlList.size() - 1); // 判断是否最后一条SQL
                if (isLast && StringUtils.isNotBlank(saveUrl)) {
                    // 先用Statement执行，判断是否有结果集
                    try (Statement stmt = con.createStatement()) {
                        boolean hasResult = stmt.execute(sql);
                        if (hasResult) {
                            // 有结果集，才走downloadData高效导出
                            downloadData("", "*", "", "", originalConfig.getInt(CoreConstant.DB_JOB_SETTING_SPEED_CHANNEL), saveUrl, sep, fileType, isChunk, chunkSize, sql);
                        } else {
                            int updateCount = stmt.getUpdateCount();
                            logger.info("最后一条SQL无结果集，影响行数：{}，SQL:{}", updateCount, sql);
                        }
                    }
                } else {
                    // 其它SQL（包括中间的SELECT）只执行，不导出结果
                    try (Statement stmt = con.createStatement()) {
                        boolean hasResult = stmt.execute(sql);
                        if (hasResult) {
                            // 非最后一条有结果集只记录日志，不导出
                            try (ResultSet rs = stmt.getResultSet()) {
                                int colCount = rs.getMetaData().getColumnCount();
                                logger.info("SQL[{}]为查询，列数:{}，未导出结果。SQL:{}", i + 1, colCount, sql);
                            }
                        } else {
                            int updateCount = stmt.getUpdateCount();
                            logger.info("SQL[{}]执行无结果集，影响行数:{}，SQL:{}", i + 1, updateCount, sql);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw e;
        }
    }

    public void executeSQL(String sql) throws Exception {
        Connection con;
        Statement stmt = null;

        try {
            con = getConn();
            stmt = getStatement(con, selectBatchSize);
            stmt.execute(sql);
        } catch (Exception e) {
            logger.error("执行SQL异常终止：{}, 异常信息：{}", sql, e);
            throw e;
        } finally {
            try {
                if (stmt != null) stmt.close();
            } catch (Exception ignore) {
            }
        }
    }

    public Statement getStatement(Connection con, int fetchSize) throws SQLException {
        Statement stmt = con.createStatement();
        stmt.setFetchSize(fetchSize);
        return stmt;
    }

    public void insert(String toTableName, String[] colNames, List<List<Object>> data) throws Exception {
        int dataSize = data.size();
        int index = 0;
        int count = 0;
        List<Object> rowdata;

        // 构建插入SQL
        StringBuilder colStr = new StringBuilder();
        StringBuilder valueStr = new StringBuilder();
        for (int i = 0; i < colNames.length; i++) {
            String[] col_name = colNames[i].split("\\.");
            if (col_name.length > 1) {
                colStr.append(col_name[1]);
            } else {
                colStr.append(colNames[i]);
            }
            valueStr.append("?");
            if (i != (colNames.length - 1)) {
                colStr.append(",");
                valueStr.append(",");
            }
        }
        String sql = "insert into " + toTableName + " (" + colStr + ") values(" + valueStr + ")";
        String dateFormatStr = null;
        // 执行批量插入
        try (Connection con = getConn(); PreparedStatement pstmt = con.prepareStatement(sql)) {
            ResultSetMetaData metaData = pstmt.getMetaData();
            if (metaData == null) {
                Statement stmt = con.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT " + colStr + " FROM " + toTableName + " WHERE 1=0");
                metaData = rs.getMetaData();
                rs.close();
                stmt.close();
            }
            con.setAutoCommit(false);
            while (index < dataSize) {
                rowdata = data.get(index);
                for (int i = 0; i < rowdata.size(); i++) {
                    int sqlType = metaData.getColumnType(i + 1);
                    Object row_data = rowdata.get(i);
                    convertAndSetParameter(row_data == null ? null : row_data.toString(), i + 1, pstmt, sqlType, dateFormatStr, true);
                }
                pstmt.addBatch();
                count++;
                // 达到缓冲区大小时提交
                if (count % insertBatchSize == 0) {
                    pstmt.executeBatch();
                    con.commit();
                    count = 0;
                }
                index++;
            }
            // 提交剩余数据
            if (count > 0) {
                pstmt.executeBatch();
                con.commit();
            }
        }
    }

    public void convertAndSetParameter(String colData, int paramIdx, PreparedStatement pstmt, int sqlType, String dateFormatStr, Boolean ignoreEmpty) throws SQLException, ParseException {
        boolean isNull = (colData == null || "\"\"".equals(colData) || colData.isEmpty() || "NULL".equalsIgnoreCase(colData));
        Column col;
        switch (sqlType) {
            case Types.BIGINT:
                col = isNull ? new LongColumn((Long) null) : new LongColumn(colData);
                break;
            case Types.DOUBLE:
            case Types.FLOAT:
            case Types.REAL:
                col = isNull ? new DoubleColumn((String) null) : new DoubleColumn(colData);
                break;
            case Types.BOOLEAN:
            case Types.BIT:
            case Types.TINYINT:
                col = isNull ? new BoolColumn((Boolean) null) : new BoolColumn(colData);
                break;
            case Types.DATE:
            case Types.TIMESTAMP:
                if (isNull) {
                    col = new DateColumn((Long) null);
                } else {
                    java.util.Date d = null;
                    if (dateFormatStr != null && !dateFormatStr.isEmpty()) {
                        SimpleDateFormat sdf = new SimpleDateFormat(dateFormatStr);
                        d = sdf.parse(colData);
                    } else {
                        try {
                            d = Timestamp.valueOf(colData);
                        } catch (Exception e) {
                            d = parseDate(colData, null);
                        }
                    }
                    col = d == null ? new DateColumn((Long) null) : new DateColumn(d);
                }
                break;
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                col = isNull ? new BytesColumn(null) : new BytesColumn(colData.getBytes());
                break;
            case Types.VARCHAR:
            case Types.CHAR:
            case Types.LONGVARCHAR:
            default:
                col = isNull ? new StringColumn(null) : new StringColumn(colData);
                break;
        }
        setPreparedStatementValue(pstmt, paramIdx, sqlType, 0, col, table, null);
    }


    /**
     * 多线程分批导出数据库表数据到文件或其他格式
     *
     * @param tableName 表名
     * @param colNames  导出列名（逗号分隔或json数组）
     * @param where     where条件
     * @param orderBy   排序条件
     * @param channel   并发线程数/分块数（自动不超过列数）
     * @param saveUrl   保存路径
     * @param sep       分隔符
     * @param fileType  文件类型（csv/parquet/json等）
     * @param isChunk   是否分块目录
     * @param chunkSize 每个数据文件最大行数
     * @param querySql  完整SQL（可选）
     * @throws Exception 异常
     */
    public void downloadData(String tableName, String colNames, String where, String orderBy, int channel, String saveUrl, String sep, String fileType, boolean isChunk, int chunkSize, String querySql) throws Exception {
        // 参数校验
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize必须大于0");
        if (channel <= 0) throw new IllegalArgumentException("channel必须大于0");
        if (StringUtils.isBlank(fileType)) throw new IllegalArgumentException("fileType不能为空");

        // 获取表结构
        String baseSQL = getSql(tableName, colNames, where, orderBy, true, querySql);
        logger.info("获取表结构信息查询语句: {}", baseSQL);
        List<ColumnStruct> columnsInfo;
        int colCount;
        try (Connection con = getConn(); Statement stmt = getStatement(con, 0); ResultSet rs = stmt.executeQuery(baseSQL)) {
            ResultSetMetaData rsmd = rs.getMetaData();
            columnsInfo = getColumnInfo(rsmd, tableName, false);
            colCount = rsmd.getColumnCount();
        }
        if (colCount == 0) throw new IllegalArgumentException("表结构无列，无法导出");

        FileWriterDescriptor descriptor = FileWriterFactory.getDescriptor(fileType);
        if (descriptor == null) throw new IllegalArgumentException("不支持的fileType: " + fileType);

        ExecutorService executorService = Executors.newFixedThreadPool(channel);
        ((ThreadPoolExecutor) executorService).setKeepAliveTime(channel, TimeUnit.SECONDS);
        ((ThreadPoolExecutor) executorService).allowCoreThreadTimeOut(true);

        List<Future<Integer>> resultList = new ArrayList<>();
        Map<String, String> allColIndexMap = new HashMap<>();

        if (isChunk) {
            // 分块模式：每个字段一个任务
            for (int i = 0; i < colCount; i++) {
                ColumnStruct colStruct = columnsInfo.get(i);
                String colName = colStruct.getColumnName();
                String colIndex = colStruct.getIndex();
                allColIndexMap.put(colName, colIndex);
                String chunkDir = saveUrl + File.separator + String.format("%010d", Integer.parseInt(colIndex));
                new File(chunkDir).mkdirs();
                final String sqlTmp = getSql(tableName, colName, where, orderBy, false, querySql);
                final List<String> colNamesList = Collections.singletonList(colName);
                final List<ColumnStruct> colStructsList = Collections.singletonList(colStruct);
                resultList.add(executorService.submit(() -> exportColumnsToDir(sqlTmp, colNamesList, colStructsList, chunkDir, descriptor, sep, chunkSize)));
            }
            dumpIndexMap(allColIndexMap);
        } else {
            // 非分块模式：所有字段一个任务
            final List<String> allColNames = columnsInfo.stream().map(ColumnStruct::getColumnName).collect(Collectors.toList());
            final List<ColumnStruct> allColStructs = new ArrayList<>(columnsInfo);
            final String sqlTmp = getSql(tableName, String.join(",", allColNames), where, orderBy, false, querySql);
            resultList.add(executorService.submit(() -> exportColumnsToDir(sqlTmp, allColNames, allColStructs, saveUrl, descriptor, sep, chunkSize)));
        }

        waitForFutures(resultList, executorService);

        // 校验导出结果
        File dir = new File(saveUrl);
        if (dir.list() == null || Objects.requireNonNull(dir.list()).length == 0) {
            throw new DbAdapterException("db to file dir check failed. dir is empty: " + dir.getPath());
        }
        logger.info("导出完成，所有分片/线程数据已写出");
    }

    /**
     * 通用字段导出任务体（支持单字段/多字段/分块/全集）。
     * <p>
     * 该方法负责将指定SQL查询结果的字段集合导出到指定目录下的一个或多个数据文件（支持分片）。
     * 适用于：
     * - 单字段分块导出（如isChunk=true时每个字段一个文件夹）
     * - 多字段全集导出（如isChunk=false时所有字段导出到同一目录）
     * - 未来可扩展为部分字段导出、分组导出等场景
     *
     * @param sqlTmp     要执行的SQL（可为单字段或多字段查询）
     * @param colNames   导出的字段名集合（顺序与SQL一致）
     * @param colStructs 字段结构信息集合（顺序与SQL一致）
     * @param outDir     输出目录（分块时为分块文件夹，全集时为主目录）
     * @param descriptor 文件写出描述符（决定文件类型、命名、header等）
     * @param sep        字段分隔符（如csv的逗号）
     * @param chunkSize  每个数据文件的最大行数（超过则自动分片）
     * @return 导出的总行数
     * @throws Exception 导出过程中的任何异常
     *                   <p>
     *                   实现细节：
     *                   - 支持大批量数据分片写出，自动分文件，自动写header
     *                   - 支持多线程并发（由外部线程池调度）
     *                   - 兼容所有FileWriter类型（csv、parquet、json等）
     *                   - 统一异常处理和资源释放，保证writer关闭
     *                   - 适合所有“导出一组字段到某个目录”的场景
     */
    private int exportColumnsToDir(String sqlTmp, List<String> colNames, List<ColumnStruct> colStructs, String outDir, FileWriterDescriptor descriptor, String sep, int chunkSize) throws Exception {
        int rowCount = 0;
        int fileIndex = 0;
        int fileRowCount = 0;
        com.paipi.filewriter.FileWriter writer = null;
        try (Connection threadCon = getConnection(); Statement threadStmt = getStatement(threadCon, selectBatchSize); ResultSet threadRs = threadStmt.executeQuery(sqlTmp)) {
            List<List<Object>> batchRows = new ArrayList<>();
            int batchSize = 10000;
            ResultSetMetaData threadRsmd = threadRs.getMetaData();
            int threadColCount = threadRsmd.getColumnCount();
            // 构造首个数据文件路径
            String filePath = outDir + File.separator + descriptor.getDataFileName(fileIndex);
            File parentDir = new File(filePath).getParentFile();
            if (parentDir != null && !parentDir.exists()) parentDir.mkdirs();
            writer = descriptor.createWriter(filePath, colNames, colStructs, sep, null, null);
            if (descriptor.needHeader()) writer.writeHeader(colNames);
            // 主循环：逐行读取结果集，批量写入文件
            while (threadRs.next()) {
                List<Object> row = new ArrayList<>(threadColCount);
                for (int c = 1; c <= threadColCount; c++) {
                    Object data = threadRs.getObject(c);
                    // 类型兼容处理
                    if (data instanceof Number) {
                        row.add("NaN".equals(data + "") ? "NaN" : data);
                    } else if (data instanceof java.sql.Timestamp) {
                        java.sql.Timestamp timeData = (java.sql.Timestamp) data;
                        String formatted = timeData.toLocalDateTime().format(Constants.DATA_TIME_FORMATTER);
                        row.add(formatted);
                    } else {
                        row.add(threadRs.getString(c));
                    }
                }
                batchRows.add(row);
                rowCount++;
                fileRowCount++;
                // 达到批量阈值，批量写入
                if (batchRows.size() >= batchSize) {
                    writer.writeRows(batchRows);
                    batchRows.clear();
                }
                // 达到分片阈值，切换新文件
                if (fileRowCount >= chunkSize) {
                    if (!batchRows.isEmpty()) {
                        writer.writeRows(batchRows);
                        batchRows.clear();
                    }
                    writer.close();
                    fileIndex++;
                    fileRowCount = 0;
                    String nextFilePath = outDir + File.separator + descriptor.getDataFileName(fileIndex);
                    File nextParentDir = new File(nextFilePath).getParentFile();
                    if (nextParentDir != null && !nextParentDir.exists()) nextParentDir.mkdirs();
                    writer = descriptor.createWriter(nextFilePath, colNames, colStructs, sep, null, null);
                    if (descriptor.needHeader()) writer.writeHeader(colNames);
                }
            }
            // 写出剩余数据
            if (!batchRows.isEmpty()) writer.writeRows(batchRows);
        } catch (Exception e) {
            logger.error("导出异常", e);
            throw e;
        } finally {
            if (writer != null) try {
                writer.close();
            } catch (Exception ignore) {
            }
        }
        return rowCount;
    }

    /**
     * 统一线程池收尾和异常处理
     */
    private void waitForFutures(List<Future<Integer>> resultList, ExecutorService executorService) throws Exception {
        for (Future<Integer> future : resultList) {
            try {
                logger.info("Future result: {}", future.get());
            } catch (Exception e) {
                logger.error("线程执行异常", e);
                throw e;
            }
        }
        executorService.shutdown();
        if (!executorService.awaitTermination(10, TimeUnit.MINUTES)) {
            logger.warn("线程池未能在10分钟内完全关闭，尝试强制关闭");
            executorService.shutdownNow();
        }
    }

    /**
     * 将列名与列索引的映射关系dump成文件
     *
     * @param allColIndexMap allColIndexMap
     */
    public void dumpIndexMap(Map<String, String> allColIndexMap) throws IOException {
        if (allColIndexMap == null || allColIndexMap.isEmpty()) {
            logger.warn("allColIndexMap参数为空");
            return;
        }

        String content = JSON.toJSONString(allColIndexMap);
        String filePath = saveUrl + File.separator + Constants.INDEX_MAP_FILE_NAME;

        // 获取文件路径的父目录
        Path path = Paths.get(filePath);
        Path parentDir = path.getParent();

        // 如果父目录不存在，则创建所有必要的目录
        if (parentDir != null && !Files.exists(parentDir)) {
            try {
                Files.createDirectories(parentDir);
            } catch (IOException e) {
                logger.error("无法创建目录: {}", parentDir, e);
                throw e;
            }
        }

        try (BufferedWriter singleWriter = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8))) {
            singleWriter.write(content);
        } catch (IOException e) {
            logger.error("dump列名映射文件异常", e);
            throw e;
        }
    }

    public String createTable(String tableName, String[] colNames, String[] colTypes) throws Exception {
        try (Connection conn = getConn(); Statement stmt = conn.createStatement()) {
            String createSql = getCreateTableSql(tableName, colNames, colTypes);

            logger.info("Create table SQL: {}", createSql);
            stmt.execute(createSql);
            return tableName;
        }
    }

    public List<ColumnStruct> getColInfoFromTableName(String tableName, String cols) {
        ArrayList<ColumnStruct> columnsInfo = new ArrayList<>();
        String baseSQL = "";
        try (Connection con = getConn(); Statement stmt = getStatement(con, 1)) {
            baseSQL = getSql(tableName, cols, "", "orderBy", true, "");
            ResultSet rs = stmt.executeQuery(baseSQL);
            ResultSetMetaData rsmd = rs.getMetaData();

            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String colName = rsmd.getColumnLabel(i);
                String tableNamePre = tableName + ".";
                if (colName.startsWith(tableNamePre)) {
                    colName = colName.substring(tableNamePre.length());
                }
                Column.Type type = Column.Type.valueOf(dbTypeToInternalType(rsmd.getColumnTypeName(i)).toUpperCase());
                ColumnStruct cstruct = new ColumnStruct(colName, type, rsmd.getPrecision(i), rsmd.getScale(i), i);
                columnsInfo.add(cstruct);
            }
        } catch (Exception ee) {
            throw new DbAdapterException(String.format("get table %s  column info error:%s, sql is :\n %s \n", tableName, ee.getMessage(), baseSQL));
        }

        return columnsInfo;
    }

    /**
     * 判断指定表是否存在
     *
     * @param tableName 表名
     * @return 存在返回true，不存在返回false
     */
    public boolean tableIsExist(String tableName) {
        // 这里建议用更通用的SQL，比如 select 1 from tableName limit 1
        String checkSql = String.format("SELECT 1 FROM %s LIMIT 1", tableName);
        try (Connection conn = getConn(); Statement stmt = conn.createStatement()) {
            stmt.executeQuery(checkSql);
            return true;
        } catch (SQLException e) {
            // 可以根据SQLState进一步判断是否为表不存在
            return false;
        }
    }

    public abstract String getJdbcUrl();

    public abstract String getDriverName();

    public abstract String getCreateTableSql(String tableName, String[] colNames, String[] colTypes);

    // 数据库的类型转成内部类型
    public abstract String dbTypeToInternalType(String colType);

    // 内部类型转成数据库类型
    public abstract List<Object> internalTypeToDbType(ColumnStruct column);

    /**
     * 通用的PreparedStatement参数设置方法，支持类型适配、长度截断、异常增强。
     *
     * @param pstmt      预编译语句
     * @param paramIndex 参数索引
     * @param sqlType    JDBC类型
     * @param maxLength  最大长度
     * @param columnData DataX Column
     * @param tableName  表名
     * @param colName    字段名
     * @throws SQLException 类型不匹配或长度超限时抛出
     */
    protected void setPreparedStatementValue(PreparedStatement pstmt, int paramIndex, int sqlType, int maxLength, Column columnData, String tableName, String colName) throws SQLException {
        try {
            if (columnData == null || columnData.getRawData() == null) {
                pstmt.setNull(paramIndex, sqlType);
            } else if (sqlType == Types.VARCHAR || sqlType == Types.CHAR || sqlType == Types.LONGVARCHAR) {
                String str = StringColumn.truncate(columnData.asString(), maxLength);
                pstmt.setString(paramIndex, str);
            } else if (sqlType == Types.INTEGER) {
                Long v = columnData.asLong();
                if (v == null) pstmt.setNull(paramIndex, sqlType);
                else pstmt.setInt(paramIndex, v.intValue());
            } else if (sqlType == Types.BIGINT) {
                Long v = columnData.asLong();
                if (v == null) pstmt.setNull(paramIndex, sqlType);
                else pstmt.setLong(paramIndex, v);
            } else if (sqlType == Types.DOUBLE || sqlType == Types.FLOAT || sqlType == Types.REAL) {
                Double v = columnData.asDouble();
                if (v == null) pstmt.setNull(paramIndex, sqlType);
                else pstmt.setDouble(paramIndex, v);
            } else if (sqlType == Types.DATE) {
                java.util.Date d = columnData.asDate();
                if (d == null) pstmt.setNull(paramIndex, sqlType);
                else pstmt.setDate(paramIndex, new Date(d.getTime()));
            } else if (sqlType == Types.TIMESTAMP) {
                java.util.Date d = columnData.asDate();
                if (d == null) pstmt.setNull(paramIndex, sqlType);
                else pstmt.setTimestamp(paramIndex, new Timestamp(d.getTime()));
            } else if (sqlType == Types.BOOLEAN || sqlType == Types.BIT) {
                Boolean b = columnData.asBoolean();
                if (b == null) pstmt.setNull(paramIndex, sqlType);
                else pstmt.setBoolean(paramIndex, b);
            } else if (sqlType == Types.BINARY || sqlType == Types.VARBINARY || sqlType == Types.LONGVARBINARY) {
                byte[] bytes = columnData.asBytes();
                pstmt.setBytes(paramIndex, bytes);
            } else {
                pstmt.setObject(paramIndex, columnData.getRawData());
            }
        } catch (Exception e) {
            throw new SQLException("setPreparedStatementValue异常，表:" + tableName + ", 字段:" + colName + ", 原始值:" + (columnData == null ? null : columnData.getRawData()) + ", 错误:" + columnData.toString(), e);
        }
    }

    /**
     * DataX风格的Reader插件，从MySQL读取数据并推送到Channel。
     * 支持类型适配，push异常时优雅退出，防止通道死锁。
     *
     * @param channel 通道（transformer链由Channel自动处理，Adapter无需关心）
     */
    public void readerPlugin(Channel<Record> channel) throws Exception {
        try {
            String tableName = config.getString(ParameterConstant.TABLE, this.table);
            String cols = ColumnParser.parseColumns(config.getList(ParameterConstant.COLUMN)).getColumnNamesStr();
            String where = config.getString(ParameterConstant.WHERE, "");
            String querySql = config.getString(ParameterConstant.QUERY_SQL, "");

            String sql = getSql(tableName, cols, where, "", false, querySql);
            try (Connection con = getConn(); Statement stmt = getStatement(con, selectBatchSize); ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData rsmd = rs.getMetaData();
                int colCount = rsmd.getColumnCount();
                while (rs.next()) {
                    Record record = new SimpleRecord();
                    for (int i = 1; i <= colCount; i++) {
                        Object value = rs.getObject(i);
                        int type = rsmd.getColumnType(i);
                        Column col;
                        switch (type) {
                            case Types.INTEGER:
                            case Types.BIGINT:
                                if (value instanceof Integer) {
                                    col = new LongColumn((Integer) value);
                                } else if (value instanceof Long) {
                                    col = new LongColumn((Long) value);
                                } else if (value != null) {
                                    col = new LongColumn(value.toString());
                                } else {
                                    col = new LongColumn((Long) null);
                                }
                                break;
                            case Types.DOUBLE:
                            case Types.FLOAT:
                            case Types.REAL:
                                if (value instanceof Double) {
                                    col = new DoubleColumn((Double) value);
                                } else if (value instanceof Float) {
                                    col = new DoubleColumn((Float) value);
                                } else if (value != null) {
                                    col = new DoubleColumn(value.toString());
                                } else {
                                    col = new DoubleColumn((String) null);
                                }
                                break;
                            case Types.BOOLEAN:
                            case Types.BIT:
                                if (value instanceof Boolean) {
                                    col = new BoolColumn((Boolean) value);
                                } else if (value != null) {
                                    col = new BoolColumn(value.toString());
                                } else {
                                    col = new BoolColumn((Boolean) null);
                                }
                                break;
                            case Types.DATE:
                                col = new DateColumn((Date) value);
                                break;
                            case Types.TIMESTAMP:
                                if (value instanceof Timestamp) {
                                    col = new DateColumn((Timestamp) value);
                                } else if (value instanceof LocalDateTime) {
                                    col = new DateColumn(Timestamp.valueOf((LocalDateTime) value));
                                } else {
                                    col = new DateColumn((Long) null);
                                }
                                break;
                            case Types.BINARY:
                            case Types.VARBINARY:
                            case Types.LONGVARBINARY:
                                col = new BytesColumn((byte[]) value);
                                break;
                            case Types.VARCHAR:
                            case Types.CHAR:
                            case Types.LONGVARCHAR:
                            default:
                                col = new StringColumn(value == null ? null : value.toString());
                        }
                        record.addColumn(col);
                    }
                    try {
                        channel.push(record);
                    } catch (Exception e) {
                        // 通道关闭时优雅退出
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("readerPlugin error", e);
            throw e;
        } finally {
            try {
                channel.close();
                logger.info("Channel closed in readerPlugin for {}", this.getClass().getSimpleName());
            } catch (Exception ex) {
                logger.warn("Exception when closing channel in readerPlugin: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * 通用DataX风格的Writer插件，支持writeMode参数（insert/replace/update/ignore），如有特殊需求请在子类重写。
     */
    public void writerPlugin(Channel<Record> channel) throws Exception {
        try {
            String tableName = config.getString(ParameterConstant.TABLE, this.table);
            List<String> colNames = ColumnParser.parseColumns(config.getList(ParameterConstant.COLUMN)).getColumnNamesList();
            if (colNames == null || colNames.isEmpty()) throw new IllegalArgumentException("colNames参数不能为空");
            int batchSize = insertBatchSize;
            String writeMode = config.getString(ParameterConstant.WRITE_MODE, "insert").toLowerCase();
            StringBuilder colStr = new StringBuilder();
            StringBuilder valueStr = new StringBuilder();
            for (int i = 0; i < colNames.size(); i++) {
                colStr.append(colNames.get(i));
                valueStr.append("?");
                if (i != (colNames.size() - 1)) {
                    colStr.append(",");
                    valueStr.append(",");
                }
            }
            String sql;
            switch (writeMode) {
                case "replace":
                    sql = "replace into " + tableName + " (" + colStr + ") values(" + valueStr + ")";
                    break;
                case "ignore":
                    sql = "insert ignore into " + tableName + " (" + colStr + ") values(" + valueStr + ")";
                    break;
                case "update":
                    // 默认实现：ON DUPLICATE KEY UPDATE（如数据库不支持请在子类重写）
                    StringBuilder updateStr = new StringBuilder();
                    for (int i = 0; i < colNames.size(); i++) {
                        String col = colNames.get(i);
                        updateStr.append(col).append("=VALUES(").append(col).append(")");
                        if (i != (colNames.size() - 1)) updateStr.append(",");
                    }
                    sql = "insert into " + tableName + " (" + colStr + ") values(" + valueStr + ") ON DUPLICATE KEY UPDATE " + updateStr;
                    break;
                default:  // 默认insert
                    sql = "insert into " + tableName + " (" + colStr + ") values(" + valueStr + ")";
                    break;
            }
            try (Connection con = getConn(); PreparedStatement pstmt = con.prepareStatement(sql)) {
                ResultSetMetaData metaData = pstmt.getMetaData();
                if (metaData == null) {
                    Statement stmt = con.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT " + colStr + " FROM " + tableName + " WHERE 1=0");
                    metaData = rs.getMetaData();
                    rs.close();
                    stmt.close();
                }
                con.setAutoCommit(false);
                long totalWrite = 0;
                while (true) {
                    List<Record> records = channel.pullAll(batchSize);
                    if (records == null || records.isEmpty()) break;

                    try {
                        for (Record t : records) {
                            Record record = t;
                            for (int i = 0; i < colNames.size(); i++) {
                                Column col = record.getColumn(i);
                                int sqlType = metaData.getColumnType(i + 1);
                                int maxLength = metaData.getPrecision(i + 1);
                                setPreparedStatementValue(pstmt, i + 1, sqlType, maxLength, col, tableName, colNames.get(i));
                                totalWrite++;
                            }
                            pstmt.addBatch();
                            if (totalWrite % recordingTotalWrite == 0) {
                                logger.info("[Thread-{}] 已写入{}条数据", Thread.currentThread().getId(), totalWrite);
                            }
                        }
                        pstmt.executeBatch();
                        con.commit();
                    } catch (Exception e) {
                        channel.close(); // 写入异常时关闭通道，防止死锁
                        throw e;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("writerPlugin error", e);
            throw e;
        } finally {
            try {
                channel.close();
                logger.info("Channel closed in writerPlugin for {}", this.getClass().getSimpleName());
            } catch (Exception ex) {
                logger.warn("Exception when closing channel in writerPlugin: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * DataX风格的分片能力，按建议分片数切分配置，供并发同步使用。
     * 默认实现：不分片，返回单分片。
     *
     * @param adviceNumber 建议分片数
     * @return 每个分片的配置
     * @throws Exception 分片异常
     */
    public java.util.List<com.paipi.config.Configuration> split(int adviceNumber) throws Exception {
        java.util.List<com.paipi.config.Configuration> splits = new java.util.ArrayList<>();
        splits.add(config.clone());
        return splits;
    }
}

