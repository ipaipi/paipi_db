package com.paipi.adapter;

import com.paipi.adapter.base.BaseAdapter;
import com.paipi.common.channel.Channel;
import com.paipi.common.channel.Record;
import com.paipi.common.column.*;
import com.paipi.config.Configuration;
import com.paipi.constant.ParameterConstant;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MysqlAdapter extends BaseAdapter {

    public MysqlAdapter(Configuration originalConfig, Configuration config) {
        super(originalConfig, config);
        selectBatchSize = 1000;
        insertBatchSize = 1000;
    }

    @Override
    public String getDriverName() {
        return "com.mysql.cj.jdbc.Driver";
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

    public List<Object> internalTypeToDbType(ColumnStruct column) {
        // 内部类型转成数据库类型
        switch (column.getType()) {
            case STRING:
                return Arrays.asList("varchar", column.getPrecision() > 0 ? column.getPrecision() : 255);
            case INT:
                return Arrays.asList("int", column.getPrecision());
            case LONG:
                return Arrays.asList("bigint", column.getPrecision());
            case DOUBLE:
                return Arrays.asList("double", column.getPrecision(), column.getScale());
            case BOOL:
                return Arrays.asList("tinyint", 1);
            case DATE:
                return Arrays.asList("datetime");
            case BYTES:
                return Arrays.asList("blob");
            default:
                return Arrays.asList("varchar", 255);
        }
    }

    @Override
    public String getCreateTableSql(String tableName, String[] colNames, String[] colTypes) {
        StringBuilder cols = new StringBuilder();
        for (int i = 0; i < colNames.length; i++) {
            cols.append(colNames[i]).append(" ");
            // 1. 类型字符串转枚举
            Column.Type type = Column.Type.valueOf(colTypes[i].toUpperCase());
            // 2. 构造ColumnStruct
            ColumnStruct struct = new ColumnStruct();
            struct.setType(type);
            // 3. 获取数据库类型
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
            if (i != (colTypes.length - 1)) {
                cols.append(", ");
            }
        }
        return "create table " + tableName + " (" + cols + ")";
    }

    @Override
    public String getJdbcUrl() {
        return "mysql://" + this.ip + ":" + this.port + "/" + this.database
                + "?" + assembleJdbcUrlParam();
    }

    @Override
    public String assembleJdbcUrlParam() {
        Map<String, String> param = new HashMap<>();
        param.put("rewriteBatchedStatements", "true");
        param.put("useUnicode", "true");
        param.put("characterEncoding", "utf8");
        param.put("useCursorFetch", "true");
        param.put("serverTimezone", "GMT%2B8");

        Map<String, Object> jdbcUrlParam = config.getMap(ParameterConstant.CONNECTION_JDBC_URL_PARAM, new HashMap<>());
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
            urlParam.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        if (urlParam.length() > 0) {
            urlParam.deleteCharAt(urlParam.length() - 1);
        }
        return urlParam.toString();
    }

    @Override
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
                    StringBuilder updateStr = new StringBuilder();
                    for (int i = 0; i < colNames.size(); i++) {
                        String col = colNames.get(i);
                        updateStr.append(col).append("=VALUES(").append(col).append(")");
                        if (i != (colNames.size() - 1)) updateStr.append(",");
                    }
                    sql = "insert into " + tableName + " (" + colStr + ") values(" + valueStr + ") ON DUPLICATE KEY UPDATE " + updateStr;
                    break;
                default:
                    sql = "insert into " + tableName + " (" + colStr + ") values(" + valueStr + ")";
                    break;
            }
            long totalWrite = 0;
            try (java.sql.Connection con = getConn(); java.sql.PreparedStatement pstmt = con.prepareStatement(sql)) {
                java.sql.ResultSetMetaData metaData = pstmt.getMetaData();
                if (metaData == null) {
                    java.sql.Statement stmt = con.createStatement();
                    java.sql.ResultSet rs = stmt.executeQuery("SELECT " + colStr + " FROM " + tableName + " WHERE 1=0");
                    metaData = rs.getMetaData();
                    rs.close();
                    stmt.close();
                }
                con.setAutoCommit(false);
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
                            }
                            pstmt.addBatch();
                        }
                        pstmt.executeBatch();
                        con.commit();
                        totalWrite += records.size();
                        if (totalWrite % recordingTotalWrite == 0) {
                            logger.info("[Thread-{}] 已写入{}条数据", Thread.currentThread().getId(), totalWrite);
                        }
                    } catch (Exception e) {
                        channel.close();
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
                logger.info("Channel closed in writerPlugin for MysqlAdapter");
            } catch (Exception ex) {
                logger.warn("Exception when closing channel in writerPlugin: {}", ex.getMessage(), ex);
            }
        }
    }

    @Override
    public java.util.List<Configuration> split(int adviceNumber) throws Exception {
        //todo 无主键分割

        // todo 方式一
        //-- 单次查询完成（适用于知道表名但不确定行数的情况）
        //SET @total_rows = (SELECT COUNT(*) FROM your_table_name);
        //SET @chunks = 4;
        //SET @rows_per_chunk = CEIL(@total_rows / @chunks);
        //
        //SELECT
        //    FLOOR(pseudo_rowid / @rows_per_chunk) AS chunk_id,
        //    MIN(pseudo_rowid) AS start_id,
        //    MAX(pseudo_rowid) AS end_id,
        //    COUNT(*) AS rows_in_chunk
        //FROM (
        //    SELECT @row := @row + 1 AS pseudo_rowid, t.*
        //    FROM your_table_name t, (SELECT @row := 0) r
        //) numbered
        //GROUP BY chunk_id
        //ORDER BY chunk_id;

        //-- 分片1查询
        //SELECT * FROM (
        //    SELECT @row := @row + 1 AS pseudo_rowid, t.*
        //    FROM your_table_name t, (SELECT @row := 0) r
        //) numbered WHERE pseudo_rowid BETWEEN 1 AND 2500;
        //
        //-- 分片2查询
        //SELECT * FROM (
        //    SELECT @row := @row + 1 AS pseudo_rowid, t.*
        //    FROM your_table_name t, (SELECT @row := 0) r
        //) numbered WHERE pseudo_rowid BETWEEN 2501 AND 5000;
        // 。。。。。

        //todo 方式二
        //SELECT
        //    ROW_NUMBER() OVER (ORDER BY column_name) AS row_id,
        //    t.*
        //FROM
        //    your_table t;

        String tableName = config.getString(ParameterConstant.TABLE, this.table);
        String splitPk = config.getString(ParameterConstant.SPLIT_PK);
        if (splitPk == null) {
            // 自动查主键
            try (java.sql.Connection con = getConn(); java.sql.PreparedStatement ps = con.prepareStatement(
                    "SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_NAME=? AND TABLE_SCHEMA=? AND CONSTRAINT_NAME='PRIMARY'")) {
                ps.setString(1, tableName);
                ps.setString(2, this.database);
                java.sql.ResultSet rs = ps.executeQuery();
                if (rs.next()) splitPk = rs.getString(1);
            }
        }
        if (splitPk == null) {
            // 没有主键，单分片
            java.util.List<Configuration> splits = new java.util.ArrayList<>();
            splits.add(config.clone());
            return splits;
        }
        // 查最小最大值
        try (java.sql.Connection con = getConn(); java.sql.Statement stmt = con.createStatement()) {
            java.sql.ResultSet rs = stmt.executeQuery("SELECT MIN(" + splitPk + "), MAX(" + splitPk + ") FROM " + tableName);
            if (!rs.next()) throw new RuntimeException("无法获取分片列范围");
            Object minObj = rs.getObject(1);
            Object maxObj = rs.getObject(2);

            // 只对数字类型主键做分片，否则单分片
            if (!(minObj instanceof Number) || !(maxObj instanceof Number)) {
                java.util.List<Configuration> splits = new java.util.ArrayList<>();
                splits.add(config.clone());
                return splits;
            }

            long min = ((Number) minObj).longValue(), max = ((Number) maxObj).longValue();
            if (min == max) { // 只有一行
                java.util.List<Configuration> splits = new java.util.ArrayList<>();
                Configuration conf = config.clone();

                // 拼接where
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
            java.util.List<Configuration> splits = new java.util.ArrayList<>();
            for (int i = 0; i < adviceNumber; i++) {
                long start = min + i * step;
                long end = (i == adviceNumber - 1) ? max : (start + step - 1);
                Configuration conf = config.clone();

                // 拼接where
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
